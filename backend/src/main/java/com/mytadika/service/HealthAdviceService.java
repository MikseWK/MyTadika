package com.mytadika.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HealthAdviceService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AdviceTemplates templates;

    // Allergen keyword maps for guardrail checks
    private static final Map<String, List<String>> ALLERGEN_KEYWORDS = Map.of(
            "milk", List.of(
                    "milk", "dairy", "yoghurt", "yogurt", "cultured milk", "full-fat",
                    "uht", "pasteurised", "cheese", "butter", "cream", "casein", "whey",
                    "lactose", "condensed milk"),
            "peanut", List.of(
                    "peanut", "groundnut", "peanut butter", "kacang", "nut"));

    private static final String MEDICAL_DISCLAIMER = "This advice is generated from validated paediatric health guidelines "
            +
            "(Malaysian Dietary Guidelines, WHO, USDA, CDC) and does not replace " +
            "professional medical consultation. Please consult a qualified healthcare " +
            "professional (doctor, nutritionist, or dietitian) for personalised medical advice.";

    // Data Transfer Objects for the rules engine database
    @Getter
    @Setter
    @NoArgsConstructor
    public static class AdviceCard {
        private String id;
        private String title;
        private String body;
        private String source;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Substitution {
        private String id;
        private String avoid;
        private String substitute;
        private String rationale;
        private String source;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CrossContactPrevention {
        private String id;
        private String rule;
        private String detail;
        private String source;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AllergyGuardrail {
        private String allergen;
        private String description;
        private List<Substitution> safeSubstitutions;
        private List<CrossContactPrevention> crossContactPrevention;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AdviceTemplates {
        private Map<String, List<AdviceCard>> dietaryAdviceDatabase;
        private Map<String, List<AdviceCard>> activityAdviceDatabase;
        private Map<String, AllergyGuardrail> allergyGuardrails;
    }

    // Result DTO structures returned to Controller
    @Data
    @Builder
    public static class GuardrailAudit {
        private String adviceId;
        private String title;
        private List<String> blockedBy;
        private String timestamp;
    }

    @Data
    @Builder
    public static class AdviceResult {
        private String childId;
        private String generatedAt;
        private String status;
        private double confidence;
        private String adviceKey;
        private List<AdviceCard> dietaryAdvice;
        private List<AdviceCard> activityAdvice;
        private List<AllergyGuardrail> allergyWarnings;
        private List<GuardrailAudit> guardrailLog;
        private boolean requiresUrgentReferral;
        private String confidenceCaveat;
        private String disclaimer;
    }

    @PostConstruct
    public void init() {
        try {
            log.info("Loading health advice templates from JSON resource...");
            InputStream is = new ClassPathResource("advice_templates.json").getInputStream();
            this.templates = objectMapper.readValue(is, AdviceTemplates.class);
            log.info("Successfully loaded health advice templates database.");
        } catch (IOException e) {
            log.error("Fatal error loading advice templates: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize HealthAdviceService", e);
        }
    }

    /**
     * Executes rules engine advice selection.
     */
    public AdviceResult generateAdvice(
            String childId,
            double ageMonths,
            int activityLevel,
            List<String> allergies,
            List<String> shownAdviceIds,
            AiPredictionClient.PredictionResponse modelOutput) {
        // 1. Input Validation (plan §8.3 & healthAdviceEngine.js)
        if (modelOutput == null || modelOutput.status() == null) {
            throw new IllegalArgumentException("modelOutput status is required");
        }
        if (activityLevel < 0 || activityLevel > 2) {
            throw new IllegalArgumentException("activityLevel must be 0, 1, or 2");
        }
        if (ageMonths < 0.0 || ageMonths > 72.0) {
            throw new IllegalArgumentException("ageMonths must be between 0 and 72");
        }

        String status = modelOutput.status().toLowerCase().trim();
        List<String> normalizedAllergies = allergies == null ? Collections.emptyList()
                : allergies.stream().map(String::trim).map(String::toLowerCase).collect(Collectors.toList());

        // 2. Label mapping: ML label -> advice key
        // normal -> "1" (Normal), moderate -> "0" (Underweight), severe -> "0"
        // (Underweight)
        String adviceKey = switch (status) {
            case "normal" -> "1";
            case "moderate", "severe" -> "0";
            default -> throw new IllegalArgumentException("Unknown ML status: " + status);
        };
        String adviceKeyLabel = Map.of("0", "underweight", "1", "normal", "2", "overweight").get(adviceKey);

        // 3. Urgent referral determination
        boolean requiresUrgentReferral = status.equals("severe") ||
                (modelOutput.flags() != null && modelOutput.flags().is_sam());

        // 4. Confidence caveat configuration
        String confidenceCaveat = null;
        if (modelOutput.confidence() < 0.70) {
            confidenceCaveat = String.format(
                    "This health assessment has moderate certainty (confidence: %d%%). " +
                            "We recommend consulting a healthcare professional for confirmation.",
                    Math.round(modelOutput.confidence() * 100));
        }

        // Audit log for allergy-blocked items
        List<GuardrailAudit> guardrailLog = new ArrayList<>();

        // 5. Query Dietary advice and filter by allergy guardrail rules
        List<AdviceCard> dietaryPool = templates.getDietaryAdviceDatabase().getOrDefault(adviceKey,
                Collections.emptyList());
        List<AdviceCard> selectedDietary;

        if (status.equals("severe")) {
            // Always force include UW-04 (Consult health professional) as first
            // recommendation
            AdviceCard urgentCard = dietaryPool.stream().filter(c -> c.getId().equals("UW-04")).findFirst()
                    .orElse(null);
            List<AdviceCard> remainingPool = dietaryPool.stream().filter(c -> !c.getId().equals("UW-04"))
                    .collect(Collectors.toList());

            List<AdviceCard> rest = selectAdvice(remainingPool, 3, normalizedAllergies, shownAdviceIds, guardrailLog);
            selectedDietary = new ArrayList<>();
            if (urgentCard != null) {
                selectedDietary.add(urgentCard);
            }
            selectedDietary.addAll(rest);
        } else {
            selectedDietary = selectAdvice(dietaryPool, 4, normalizedAllergies, shownAdviceIds, guardrailLog);
        }

        // 6. Query Activity advice and filter
        List<AdviceCard> activityPool = templates.getActivityAdviceDatabase()
                .getOrDefault(String.valueOf(activityLevel), Collections.emptyList());
        List<AdviceCard> selectedActivity = selectAdvice(activityPool, 3, normalizedAllergies, shownAdviceIds,
                guardrailLog);

        // 7. Render active Allergy Warning banners
        List<AllergyGuardrail> allergyWarnings = new ArrayList<>();
        for (String allergen : normalizedAllergies) {
            AllergyGuardrail guard = templates.getAllergyGuardrails().get(allergen);
            if (guard != null) {
                allergyWarnings.add(guard);
            }
        }

        return AdviceResult.builder()
                .childId(childId)
                .generatedAt(LocalDateTime.now().toString())
                .status(status)
                .confidence(modelOutput.confidence())
                .adviceKey(adviceKeyLabel)
                .dietaryAdvice(selectedDietary)
                .activityAdvice(selectedActivity)
                .allergyWarnings(allergyWarnings)
                .guardrailLog(guardrailLog)
                .requiresUrgentReferral(requiresUrgentReferral)
                .confidenceCaveat(confidenceCaveat)
                .disclaimer(MEDICAL_DISCLAIMER)
                .build();
    }

    /**
     * Filters, orders, and crops advice templates list.
     */
    private List<AdviceCard> selectAdvice(
            List<AdviceCard> pool,
            int count,
            List<String> allergies,
            List<String> shownAdviceIds,
            List<GuardrailAudit> guardrailLog) {
        List<AdviceCard> allowed = new ArrayList<>();

        for (AdviceCard card : pool) {
            List<String> triggers = checkAllergiesMatch(card, allergies);
            if (!triggers.isEmpty()) {
                guardrailLog.add(GuardrailAudit.builder()
                        .adviceId(card.getId())
                        .title(card.getTitle())
                        .blockedBy(triggers)
                        .timestamp(LocalDateTime.now().toString())
                        .build());
            } else {
                allowed.add(card);
            }
        }

        // Segregate by shown/rotation lists
        List<AdviceCard> fresh = allowed.stream().filter(c -> !shownAdviceIds.contains(c.getId()))
                .collect(Collectors.toList());
        List<AdviceCard> stale = allowed.stream().filter(c -> shownAdviceIds.contains(c.getId()))
                .collect(Collectors.toList());

        List<AdviceCard> combined = new ArrayList<>();
        combined.addAll(fresh);
        combined.addAll(stale);

        return combined.stream().limit(count).collect(Collectors.toList());
    }

    /**
     * Returns matching allergen triggers if advice references blocked keywords.
     */
    private List<String> checkAllergiesMatch(AdviceCard card, List<String> allergies) {
        List<String> triggers = new ArrayList<>();
        String text = (card.getTitle() + " " + card.getBody()).toLowerCase();

        for (String allergen : allergies) {
            List<String> keywords = ALLERGEN_KEYWORDS.get(allergen);
            if (keywords != null) {
                for (String kw : keywords) {
                    if (text.contains(kw)) {
                        triggers.add(allergen);
                        break;
                    }
                }
            }
        }
        return triggers;
    }

    // Workaround for JSON mapping key name mismatch
    private static class AllergyTemplatesWorkaround extends AdviceTemplates {
        @JsonProperty("allergyGuardrails")
        public void setAllergyGuardrailsWorkaround(Map<String, AllergyGuardrail> allergyGuardrails) {
            super.setAllergyGuardrails(allergyGuardrails);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class AdviceTemplatesWorkaroundRoot {
        private Map<String, List<AdviceCard>> dietaryAdviceDatabase;
        private Map<String, List<AdviceCard>> activityAdviceDatabase;
        private Map<String, AllergyGuardrail> allergyGuardrails;
    }
}

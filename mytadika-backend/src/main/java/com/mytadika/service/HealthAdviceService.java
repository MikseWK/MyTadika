package com.mytadika.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HealthAdviceService {

    private static final Logger log = LoggerFactory.getLogger(HealthAdviceService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AdviceTemplates templates;

    private static final Map<String, List<String>> ALLERGEN_KEYWORDS = Map.of(
            "milk",   List.of("milk","dairy","yoghurt","yogurt","cultured milk","full-fat",
                              "uht","pasteurised","cheese","butter","cream","casein","whey",
                              "lactose","condensed milk"),
            "peanut", List.of("peanut","groundnut","peanut butter","kacang","nut")
    );

    private static final String MEDICAL_DISCLAIMER =
            "This advice is generated from validated paediatric health guidelines " +
            "(Malaysian Dietary Guidelines, WHO, USDA, CDC) and does not replace " +
            "professional medical consultation. Please consult a qualified healthcare " +
            "professional (doctor, nutritionist, or dietitian) for personalised medical advice.";

    // ── Inner data classes ───────────────────────────────────────────────────────

    public static class AdviceCard {
        private String id, title, body, source;
        public String getId()     { return id; }
        public String getTitle()  { return title; }
        public String getBody()   { return body; }
        public String getSource() { return source; }
        public void setId(String v)     { this.id = v; }
        public void setTitle(String v)  { this.title = v; }
        public void setBody(String v)   { this.body = v; }
        public void setSource(String v) { this.source = v; }
    }

    public static class Substitution {
        private String id, avoid, substitute, rationale, source;
        public String getId()          { return id; }
        public String getAvoid()       { return avoid; }
        public String getSubstitute()  { return substitute; }
        public String getRationale()   { return rationale; }
        public String getSource()      { return source; }
        public void setId(String v)          { this.id = v; }
        public void setAvoid(String v)       { this.avoid = v; }
        public void setSubstitute(String v)  { this.substitute = v; }
        public void setRationale(String v)   { this.rationale = v; }
        public void setSource(String v)      { this.source = v; }
    }

    public static class CrossContactPrevention {
        private String id, rule, detail, source;
        public String getId()     { return id; }
        public String getRule()   { return rule; }
        public String getDetail() { return detail; }
        public String getSource() { return source; }
        public void setId(String v)     { this.id = v; }
        public void setRule(String v)   { this.rule = v; }
        public void setDetail(String v) { this.detail = v; }
        public void setSource(String v) { this.source = v; }
    }

    public static class AllergyGuardrail {
        private String allergen, description;
        private List<Substitution> safeSubstitutions;
        private List<CrossContactPrevention> crossContactPrevention;
        public String getAllergen()                            { return allergen; }
        public String getDescription()                        { return description; }
        public List<Substitution> getSafeSubstitutions()     { return safeSubstitutions; }
        public List<CrossContactPrevention> getCrossContactPrevention() { return crossContactPrevention; }
        public void setAllergen(String v)                            { this.allergen = v; }
        public void setDescription(String v)                         { this.description = v; }
        public void setSafeSubstitutions(List<Substitution> v)       { this.safeSubstitutions = v; }
        public void setCrossContactPrevention(List<CrossContactPrevention> v) { this.crossContactPrevention = v; }
    }

    public static class AdviceTemplates {
        private Map<String, List<AdviceCard>> dietaryAdviceDatabase;
        private Map<String, List<AdviceCard>> activityAdviceDatabase;
        private Map<String, AllergyGuardrail> allergyGuardrails;
        public Map<String, List<AdviceCard>> getDietaryAdviceDatabase()  { return dietaryAdviceDatabase; }
        public Map<String, List<AdviceCard>> getActivityAdviceDatabase() { return activityAdviceDatabase; }
        public Map<String, AllergyGuardrail> getAllergyGuardrails()      { return allergyGuardrails; }
        public void setDietaryAdviceDatabase(Map<String, List<AdviceCard>> v)  { this.dietaryAdviceDatabase = v; }
        public void setActivityAdviceDatabase(Map<String, List<AdviceCard>> v) { this.activityAdviceDatabase = v; }
        public void setAllergyGuardrails(Map<String, AllergyGuardrail> v)      { this.allergyGuardrails = v; }
    }

    // ── Result DTOs ──────────────────────────────────────────────────────────────

    public static class GuardrailAudit {
        private String adviceId, title, timestamp;
        private List<String> blockedBy;

        private GuardrailAudit() {}
        public static class Builder {
            private String adviceId, title, timestamp;
            private List<String> blockedBy;
            public Builder adviceId(String v)       { this.adviceId = v; return this; }
            public Builder title(String v)           { this.title = v; return this; }
            public Builder timestamp(String v)       { this.timestamp = v; return this; }
            public Builder blockedBy(List<String> v) { this.blockedBy = v; return this; }
            public GuardrailAudit build() {
                GuardrailAudit g = new GuardrailAudit();
                g.adviceId = adviceId; g.title = title;
                g.timestamp = timestamp; g.blockedBy = blockedBy;
                return g;
            }
        }
        public static Builder builder() { return new Builder(); }
        public String getAdviceId()       { return adviceId; }
        public String getTitle()          { return title; }
        public String getTimestamp()      { return timestamp; }
        public List<String> getBlockedBy(){ return blockedBy; }
    }

    public static class AdviceResult {
        private String childId, generatedAt, status, adviceKey, confidenceCaveat, disclaimer;
        private double confidence;
        private boolean requiresUrgentReferral;
        private List<AdviceCard> dietaryAdvice, activityAdvice;
        private List<AllergyGuardrail> allergyWarnings;
        private List<GuardrailAudit> guardrailLog;

        private AdviceResult() {}
        public static class Builder {
            private String childId, generatedAt, status, adviceKey, confidenceCaveat, disclaimer;
            private double confidence;
            private boolean requiresUrgentReferral;
            private List<AdviceCard> dietaryAdvice, activityAdvice;
            private List<AllergyGuardrail> allergyWarnings;
            private List<GuardrailAudit> guardrailLog;

            public Builder childId(String v)                         { this.childId = v; return this; }
            public Builder generatedAt(String v)                     { this.generatedAt = v; return this; }
            public Builder status(String v)                          { this.status = v; return this; }
            public Builder adviceKey(String v)                       { this.adviceKey = v; return this; }
            public Builder confidence(double v)                      { this.confidence = v; return this; }
            public Builder confidenceCaveat(String v)                { this.confidenceCaveat = v; return this; }
            public Builder disclaimer(String v)                      { this.disclaimer = v; return this; }
            public Builder requiresUrgentReferral(boolean v)         { this.requiresUrgentReferral = v; return this; }
            public Builder dietaryAdvice(List<AdviceCard> v)         { this.dietaryAdvice = v; return this; }
            public Builder activityAdvice(List<AdviceCard> v)        { this.activityAdvice = v; return this; }
            public Builder allergyWarnings(List<AllergyGuardrail> v) { this.allergyWarnings = v; return this; }
            public Builder guardrailLog(List<GuardrailAudit> v)      { this.guardrailLog = v; return this; }
            public AdviceResult build() {
                AdviceResult r = new AdviceResult();
                r.childId = childId; r.generatedAt = generatedAt; r.status = status;
                r.adviceKey = adviceKey; r.confidence = confidence;
                r.confidenceCaveat = confidenceCaveat; r.disclaimer = disclaimer;
                r.requiresUrgentReferral = requiresUrgentReferral;
                r.dietaryAdvice = dietaryAdvice; r.activityAdvice = activityAdvice;
                r.allergyWarnings = allergyWarnings; r.guardrailLog = guardrailLog;
                return r;
            }
        }
        public static Builder builder() { return new Builder(); }

        public String getChildId()                       { return childId; }
        public String getGeneratedAt()                   { return generatedAt; }
        public String getStatus()                        { return status; }
        public String getAdviceKey()                     { return adviceKey; }
        public double getConfidence()                    { return confidence; }
        public String getConfidenceCaveat()              { return confidenceCaveat; }
        public String getDisclaimer()                    { return disclaimer; }
        public boolean isRequiresUrgentReferral()        { return requiresUrgentReferral; }
        public List<AdviceCard> getDietaryAdvice()       { return dietaryAdvice; }
        public List<AdviceCard> getActivityAdvice()      { return activityAdvice; }
        public List<AllergyGuardrail> getAllergyWarnings(){ return allergyWarnings; }
        public List<GuardrailAudit> getGuardrailLog()   { return guardrailLog; }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

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

    // ── Core logic ───────────────────────────────────────────────────────────────

    public AdviceResult generateAdvice(
            String childId, double ageMonths, int activityLevel,
            List<String> allergies, List<String> shownAdviceIds,
            AiPredictionClient.PredictionResponse modelOutput
    ) {
        if (modelOutput == null || modelOutput.status() == null)
            throw new IllegalArgumentException("modelOutput status is required");
        if (activityLevel < 0 || activityLevel > 2)
            throw new IllegalArgumentException("activityLevel must be 0, 1, or 2");
        if (ageMonths < 0.0 || ageMonths > 72.0)
            throw new IllegalArgumentException("ageMonths must be between 0 and 72");

        String status = modelOutput.status().toLowerCase().trim();
        List<String> normAllergies = allergies == null ? Collections.emptyList()
                : allergies.stream().map(String::trim).map(String::toLowerCase).collect(Collectors.toList());

        String adviceKey = switch (status) {
            case "normal"             -> "1";
            case "moderate", "severe" -> "0";
            default -> throw new IllegalArgumentException("Unknown ML status: " + status);
        };
        String adviceKeyLabel = Map.of("0","underweight","1","normal","2","overweight").get(adviceKey);

        boolean urgentReferral = status.equals("severe")
                || (modelOutput.flags() != null && modelOutput.flags().is_sam());

        String caveat = null;
        if (modelOutput.confidence() < 0.70) {
            caveat = String.format(
                "This health assessment has moderate certainty (confidence: %d%%). " +
                "We recommend consulting a healthcare professional for confirmation.",
                Math.round(modelOutput.confidence() * 100));
        }

        List<GuardrailAudit> guardrailLog = new ArrayList<>();

        // Dietary advice selection
        List<AdviceCard> dietaryPool = templates.getDietaryAdviceDatabase().getOrDefault(adviceKey, Collections.emptyList());
        List<AdviceCard> selectedDietary;
        if (status.equals("severe")) {
            AdviceCard urgentCard = dietaryPool.stream().filter(c -> "UW-04".equals(c.getId())).findFirst().orElse(null);
            List<AdviceCard> rest = dietaryPool.stream().filter(c -> !"UW-04".equals(c.getId())).collect(Collectors.toList());
            List<AdviceCard> selected = selectAdvice(rest, 3, normAllergies, shownAdviceIds, guardrailLog);
            selectedDietary = new ArrayList<>();
            if (urgentCard != null) selectedDietary.add(urgentCard);
            selectedDietary.addAll(selected);
        } else {
            selectedDietary = selectAdvice(dietaryPool, 4, normAllergies, shownAdviceIds, guardrailLog);
        }

        // Activity advice selection
        List<AdviceCard> actPool = templates.getActivityAdviceDatabase().getOrDefault(String.valueOf(activityLevel), Collections.emptyList());
        List<AdviceCard> selectedActivity = selectAdvice(actPool, 3, normAllergies, shownAdviceIds, guardrailLog);

        // Allergy warnings
        List<AllergyGuardrail> warnings = new ArrayList<>();
        for (String allergen : normAllergies) {
            AllergyGuardrail g = templates.getAllergyGuardrails().get(allergen);
            if (g != null) warnings.add(g);
        }

        return AdviceResult.builder()
                .childId(childId)
                .generatedAt(LocalDateTime.now().toString())
                .status(status)
                .confidence(modelOutput.confidence())
                .adviceKey(adviceKeyLabel)
                .dietaryAdvice(selectedDietary)
                .activityAdvice(selectedActivity)
                .allergyWarnings(warnings)
                .guardrailLog(guardrailLog)
                .requiresUrgentReferral(urgentReferral)
                .confidenceCaveat(caveat)
                .disclaimer(MEDICAL_DISCLAIMER)
                .build();
    }

    private List<AdviceCard> selectAdvice(List<AdviceCard> pool, int count,
                                          List<String> allergies, List<String> shown,
                                          List<GuardrailAudit> log) {
        List<AdviceCard> allowed = new ArrayList<>();
        for (AdviceCard card : pool) {
            List<String> triggers = allergyTriggers(card, allergies);
            if (!triggers.isEmpty()) {
                log.add(GuardrailAudit.builder()
                        .adviceId(card.getId()).title(card.getTitle())
                        .blockedBy(triggers).timestamp(LocalDateTime.now().toString())
                        .build());
            } else {
                allowed.add(card);
            }
        }
        List<AdviceCard> fresh = allowed.stream().filter(c -> !shown.contains(c.getId())).collect(Collectors.toList());
        List<AdviceCard> stale = allowed.stream().filter(c ->  shown.contains(c.getId())).collect(Collectors.toList());
        List<AdviceCard> combined = new ArrayList<>(fresh);
        combined.addAll(stale);
        return combined.stream().limit(count).collect(Collectors.toList());
    }

    private List<String> allergyTriggers(AdviceCard card, List<String> allergies) {
        List<String> triggers = new ArrayList<>();
        String text = (card.getTitle() + " " + card.getBody()).toLowerCase();
        for (String allergen : allergies) {
            List<String> keywords = ALLERGEN_KEYWORDS.get(allergen);
            if (keywords != null && keywords.stream().anyMatch(text::contains))
                triggers.add(allergen);
        }
        return triggers;
    }
}

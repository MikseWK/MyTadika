package com.mytadika.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class AcademicPredictionClient {

    private final RestTemplate restTemplate;
    private final String predictUrl;

    public AcademicPredictionClient(
            @Value("${academic.ai.service.url:http://localhost:8002/api/predict}") String predictUrl) {
        this.restTemplate = new RestTemplate();
        this.predictUrl = predictUrl;
    }

    // Fallback thresholds — must mirror AI/academic/api/generate_dataset.py's
    // assign_label() exactly, since this is used verbatim as the rule when the
    // Python service is offline (not a different heuristic).
    private static final double Z_STRENGTH = 0.5;
    private static final double Z_EMERGING = -0.5;
    private static final double COMPLETION_HEALTHY = 0.75;
    private static final double COMPLETION_LOW = 0.5;

    // Request DTOs
    public record DomainFeatureRequest(
            String domain,
            double domain_avg_score,
            String domain_trend,
            double domain_completion_rate,
            int domain_overdue_count,
            double ipsative_zscore) {
    }

    public record PredictionRequest(String student_id, List<DomainFeatureRequest> domains) {
    }

    // Response DTOs
    public record DomainPredictionResult(
            String domain,
            String domain_display_name,
            String level,
            double confidence) {
    }

    public record PredictionResponse(
            String student_id,
            List<DomainPredictionResult> domains,
            String model_version,
            String disclaimer) {
    }

    /**
     * Calls the FastAPI academic microservice. Falls back to the same
     * deterministic labeling rule the model was bootstrapped from if the
     * service is offline.
     */
    public PredictionResponse predict(String studentId, List<DomainFeatureRequest> domainRows) {
        PredictionRequest request = new PredictionRequest(studentId, domainRows);
        try {
            log.info("Sending academic domain prediction request to FastAPI for student_id: {}", studentId);
            return restTemplate.postForObject(predictUrl, request, PredictionResponse.class);
        } catch (Exception e) {
            log.warn("Academic AI service unavailable ({}). Initiating rule-based fallback.", e.getMessage());
            return generateFallbackResponse(request);
        }
    }

    private PredictionResponse generateFallbackResponse(PredictionRequest request) {
        List<DomainPredictionResult> results = request.domains().stream()
                .map(d -> new DomainPredictionResult(
                        d.domain(),
                        displayName(d.domain()),
                        fallbackLevel(d.ipsative_zscore(), d.domain_trend(), d.domain_completion_rate()),
                        0.50))
                .toList();

        return new PredictionResponse(
                request.student_id(),
                results,
                "Fallback-Rules-v1.0",
                "WARNING: Academic AI service offline. Prediction computed using fallback rules.");
    }

    private String fallbackLevel(double z, String trend, double completionRate) {
        if (z > Z_STRENGTH && completionRate >= COMPLETION_HEALTHY && !"declining".equals(trend)) {
            return "Strength";
        }
        if (z < Z_EMERGING || "declining".equals(trend) || completionRate < COMPLETION_LOW) {
            return "Emerging";
        }
        return "Developing";
    }

    private String displayName(String domain) {
        return switch (domain) {
            case "language_literacy" -> "Language & Literacy";
            case "stem_logic" -> "STEM & Logic";
            case "creative_arts" -> "Creative Arts";
            case "physical_movement" -> "Physical & Movement";
            default -> domain;
        };
    }
}

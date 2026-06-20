package com.mytadika.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AiPredictionClient {

    private static final Logger log = LoggerFactory.getLogger(AiPredictionClient.class);
    private final RestTemplate restTemplate;
    private final String predictUrl;

    public AiPredictionClient(
            @Value("${ai.service.url:http://localhost:8001/api/predict}") String predictUrl) {
        this.restTemplate = new RestTemplate();
        this.predictUrl = predictUrl;
    }

    // Request DTO record
    public record PredictionRequest(
            String child_id,
            double age_months,
            double weight_kg,
            double height_cm,
            Double muac_cm,
            Double bmi,
            String gender
    ) {}

    // Response DTO records
    public record ProbabilityBreakdown(
            double normal,
            double moderate,
            double severe
    ) {}

    public record ClinicalFlags(
            boolean is_sam,
            boolean is_stunted,
            boolean is_wasted
    ) {}

    public record PredictionResponse(
            String child_id,
            String status,
            int encoded,
            double confidence,
            ProbabilityBreakdown probabilities,
            ClinicalFlags flags,
            String model_version,
            String disclaimer
    ) {}

    public PredictionResponse predict(String childId, double ageMonths, double weightKg,
                                      double heightCm, Double muacCm, String gender) {
        double calculatedBmi = weightKg / Math.pow(heightCm / 100.0, 2);
        PredictionRequest request = new PredictionRequest(
                childId, ageMonths, weightKg, heightCm, muacCm, calculatedBmi,
                gender != null ? gender.toLowerCase() : null
        );
        try {
            log.info("Sending prediction request to FastAPI for child_id: {}", childId);
            return restTemplate.postForObject(predictUrl, request, PredictionResponse.class);
        } catch (Exception e) {
            log.warn("FastAPI unavailable ({}). Using rule-based fallback.", e.getMessage());
            return generateFallbackResponse(request, calculatedBmi);
        }
    }

    private PredictionResponse generateFallbackResponse(PredictionRequest request, double bmi) {
        String status;
        int encoded;
        double confidence = 0.50;

        if (bmi < 12.0)      { status = "severe";   encoded = 2; }
        else if (bmi < 13.5) { status = "moderate";  encoded = 1; }
        else                 { status = "normal";    encoded = 0; }

        boolean isStunted = request.height_cm() < (95.0 - (72 - request.age_months()) * 0.4);
        boolean isWasted  = bmi < 13.0;
        boolean isSam     = (request.muac_cm() != null && request.muac_cm() < 11.5) || bmi < 11.0;

        ProbabilityBreakdown probs = new ProbabilityBreakdown(
                status.equals("normal")   ? 0.70 : 0.15,
                status.equals("moderate") ? 0.70 : 0.15,
                status.equals("severe")   ? 0.70 : 0.15
        );

        return new PredictionResponse(
                request.child_id(), status, encoded, confidence,
                probs, new ClinicalFlags(isSam, isStunted, isWasted),
                "Fallback-Rules-v1.0",
                "WARNING: FastAPI service offline. Prediction computed using fallback rules."
        );
    }
}

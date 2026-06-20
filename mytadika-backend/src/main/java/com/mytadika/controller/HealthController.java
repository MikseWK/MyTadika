package com.mytadika.controller;

import com.mytadika.model.AllergyProfile;
import com.mytadika.model.HealthRecord;
import com.mytadika.repository.AllergyProfileRepository;
import com.mytadika.repository.HealthRecordRepository;
import com.mytadika.service.AiPredictionClient;
import com.mytadika.service.HealthAdviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*") // Adjust origins in production settings
@Validated
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final HealthRecordRepository healthRecordRepository;
    private final AllergyProfileRepository allergyProfileRepository;
    private final AiPredictionClient aiPredictionClient;
    private final HealthAdviceService healthAdviceService;

    public HealthController(
            HealthRecordRepository healthRecordRepository,
            AllergyProfileRepository allergyProfileRepository,
            AiPredictionClient aiPredictionClient,
            HealthAdviceService healthAdviceService) {
        this.healthRecordRepository = healthRecordRepository;
        this.allergyProfileRepository = allergyProfileRepository;
        this.aiPredictionClient = aiPredictionClient;
        this.healthAdviceService = healthAdviceService;
    }

    // DTO for incoming measurements & advice requests
    public static class HealthRequestDTO {
        @NotBlank(message = "childId is required")
        private String childId;

        @NotNull(message = "ageMonths is required")
        @Min(value = 0, message = "ageMonths must be >= 0")
        @Max(value = 72, message = "ageMonths must be <= 72")
        private Double ageMonths;

        @NotNull(message = "weightKg is required")
        @Min(value = 2, message = "weightKg must be >= 2.5")
        @Max(value = 30, message = "weightKg must be <= 30")
        private Double weightKg;

        @NotNull(message = "heightCm is required")
        @Min(value = 45, message = "heightCm must be >= 45")
        @Max(value = 130, message = "heightCm must be <= 130")
        private Double heightCm;

        private Double muacCm;

        private String gender;

        @NotNull(message = "activityLevel is required")
        @Min(value = 0, message = "activityLevel must be 0, 1, or 2")
        @Max(value = 2, message = "activityLevel must be 0, 1, or 2")
        private Integer activityLevel;

        private List<String> allergies;
        private List<String> shownAdviceIds;
        private Long recordedBy;

        public HealthRequestDTO() {}

        public String getChildId() {
            return childId;
        }

        public void setChildId(String childId) {
            this.childId = childId;
        }

        public Double getAgeMonths() {
            return ageMonths;
        }

        public void setAgeMonths(Double ageMonths) {
            this.ageMonths = ageMonths;
        }

        public Double getWeightKg() {
            return weightKg;
        }

        public void setWeightKg(Double weightKg) {
            this.weightKg = weightKg;
        }

        public Double getHeightCm() {
            return heightCm;
        }

        public void setHeightCm(Double heightCm) {
            this.heightCm = heightCm;
        }

        public Double getMuacCm() {
            return muacCm;
        }

        public void setMuacCm(Double muacCm) {
            this.muacCm = muacCm;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public Integer getActivityLevel() {
            return activityLevel;
        }

        public void setActivityLevel(Integer activityLevel) {
            this.activityLevel = activityLevel;
        }

        public List<String> getAllergies() {
            return allergies;
        }

        public void setAllergies(List<String> allergies) {
            this.allergies = allergies;
        }

        public List<String> getShownAdviceIds() {
            return shownAdviceIds;
        }

        public void setShownAdviceIds(List<String> shownAdviceIds) {
            this.shownAdviceIds = shownAdviceIds;
        }

        public Long getRecordedBy() {
            return recordedBy;
        }

        public void setRecordedBy(Long recordedBy) {
            this.recordedBy = recordedBy;
        }
    }

    // Wrapper response indicating saved record ID and generated advice
    public static class RecordResponseDTO {
        private Long recordId;
        private HealthRecord healthRecord;
        private HealthAdviceService.AdviceResult advice;

        public RecordResponseDTO() {}

        public RecordResponseDTO(Long recordId, HealthRecord healthRecord, HealthAdviceService.AdviceResult advice) {
            this.recordId = recordId;
            this.healthRecord = healthRecord;
            this.advice = advice;
        }

        public Long getRecordId() {
            return recordId;
        }

        public void setRecordId(Long recordId) {
            this.recordId = recordId;
        }

        public HealthRecord getHealthRecord() {
            return healthRecord;
        }

        public void setHealthRecord(HealthRecord healthRecord) {
            this.healthRecord = healthRecord;
        }

        public HealthAdviceService.AdviceResult getAdvice() {
            return advice;
        }

        public void setAdvice(HealthAdviceService.AdviceResult advice) {
            this.advice = advice;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long recordId;
            private HealthRecord healthRecord;
            private HealthAdviceService.AdviceResult advice;

            public Builder recordId(Long recordId) {
                this.recordId = recordId;
                return this;
            }

            public Builder healthRecord(HealthRecord healthRecord) {
                this.healthRecord = healthRecord;
                return this;
            }

            public Builder advice(HealthAdviceService.AdviceResult advice) {
                this.advice = advice;
                return this;
            }

            public RecordResponseDTO build() {
                return new RecordResponseDTO(recordId, healthRecord, advice);
            }
        }
    }

    // DTO for allergy updates
    public static class AllergyUpdateDTO {
        private List<String> allergies;

        public AllergyUpdateDTO() {}

        public List<String> getAllergies() {
            return allergies;
        }

        public void setAllergies(List<String> allergies) {
            this.allergies = allergies;
        }
    }

    /**
     * Endpoint to run direct prediction and advice generation on user input.
     * Does NOT persist any records.
     */
    @PostMapping("/predict")
    public ResponseEntity<HealthAdviceService.AdviceResult> predictAndAdvise(
            @Valid @RequestBody HealthRequestDTO request) {
        log.info("REST request to predict and generate advice for child: {}", request.getChildId());

        // Invoke ML model via microservice client
        AiPredictionClient.PredictionResponse modelResponse = aiPredictionClient.predict(
                request.getChildId(),
                request.getAgeMonths(),
                request.getWeightKg(),
                request.getHeightCm(),
                request.getMuacCm(),
                request.getGender()
        );

        // Run Rules Engine port
        HealthAdviceService.AdviceResult adviceResult = healthAdviceService.generateAdvice(
                request.getChildId(),
                request.getAgeMonths(),
                request.getActivityLevel(),
                request.getAllergies() != null ? request.getAllergies() : Collections.emptyList(),
                request.getShownAdviceIds() != null ? request.getShownAdviceIds() : Collections.emptyList(),
                modelResponse
        );

        return ResponseEntity.ok(adviceResult);
    }

    /**
     * Endpoint to record new measurement, run AI checks, and persist the record.
     */
    @PostMapping("/record")
    public ResponseEntity<RecordResponseDTO> recordMeasurement(
            @Valid @RequestBody HealthRequestDTO request) {
        log.info("REST request to record measurements and advise for student: {}", request.getChildId());

        // 1. Map child ID to database ID
        Long studentId;
        try {
            studentId = Long.parseLong(request.getChildId());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }

        // 2. Fetch AI advice result
        AiPredictionClient.PredictionResponse modelResponse = aiPredictionClient.predict(
                request.getChildId(),
                request.getAgeMonths(),
                request.getWeightKg(),
                request.getHeightCm(),
                request.getMuacCm(),
                request.getGender()
        );

        HealthAdviceService.AdviceResult adviceResult = healthAdviceService.generateAdvice(
                request.getChildId(),
                request.getAgeMonths(),
                request.getActivityLevel(),
                request.getAllergies() != null ? request.getAllergies() : Collections.emptyList(),
                request.getShownAdviceIds() != null ? request.getShownAdviceIds() : Collections.emptyList(),
                modelResponse
        );

        // 3. Map & Save HealthRecord Entity
        double heightM = request.getHeightCm() / 100.0;
        double bmi = request.getWeightKg() / (heightM * heightM);

        HealthRecord record = HealthRecord.builder()
                .studentId(studentId)
                .ageMonths(request.getAgeMonths())
                .weightKg(request.getWeightKg())
                .heightCm(request.getHeightCm())
                .muacCm(request.getMuacCm())
                .bmi(bmi)
                .nutritionStatus(adviceResult.getStatus())
                .recordedBy(request.getRecordedBy())
                .recordedAt(LocalDateTime.now())
                .build();

        HealthRecord savedRecord = healthRecordRepository.save(record);

        // 4. Update/Save AllergyProfile if allergies list is provided
        if (request.getAllergies() != null) {
            AllergyProfile allergyProfile = allergyProfileRepository.findById(studentId)
                    .orElse(AllergyProfile.builder().studentId(studentId).build());
            allergyProfile.setAllergiesList(request.getAllergies());
            allergyProfileRepository.save(allergyProfile);
        }

        RecordResponseDTO response = RecordResponseDTO.builder()
                .recordId(savedRecord.getId())
                .healthRecord(savedRecord)
                .advice(adviceResult)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve health measurement logs for a child sorted by newest first.
     */
    @GetMapping("/history/{studentId}")
    public ResponseEntity<List<HealthRecord>> getHistory(@PathVariable Long studentId) {
        log.info("REST request to fetch health log history for student: {}", studentId);
        List<HealthRecord> history = healthRecordRepository.findByStudentIdOrderByRecordedAtDesc(studentId);
        return ResponseEntity.ok(history);
    }

    /**
     * Fetches current advice for a student using their most recent record and allergy configuration.
     */
    @GetMapping("/advice/{studentId}")
    public ResponseEntity<HealthAdviceService.AdviceResult> getLatestAdvice(
            @PathVariable Long studentId,
            @RequestParam(required = false, defaultValue = "1") int activityLevel,
            @RequestParam(required = false) List<String> shownAdviceIds
    ) {
        log.info("REST request to fetch latest AI advice cards for student: {}", studentId);

        Optional<HealthRecord> latestRecordOpt = healthRecordRepository.findFirstByStudentIdOrderByRecordedAtDesc(studentId);
        if (latestRecordOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HealthRecord record = latestRecordOpt.get();

        // Retrieve allergies
        List<String> allergies = Collections.emptyList();
        Optional<AllergyProfile> allergyOpt = allergyProfileRepository.findById(studentId);
        if (allergyOpt.isPresent()) {
            allergies = allergyOpt.get().getAllergiesList();
        }

        // Mock prediction response to represent stored record status
        AiPredictionClient.PredictionResponse storedModelResponse = new AiPredictionClient.PredictionResponse(
                String.valueOf(studentId),
                record.getNutritionStatus(),
                record.getNutritionStatus().equals("normal") ? 0 : (record.getNutritionStatus().equals("moderate") ? 1 : 2),
                1.0, // Stored record has full certainty
                new AiPredictionClient.ProbabilityBreakdown(
                        record.getNutritionStatus().equals("normal") ? 1.0 : 0.0,
                        record.getNutritionStatus().equals("moderate") ? 1.0 : 0.0,
                        record.getNutritionStatus().equals("severe") ? 1.0 : 0.0
                ),
                new AiPredictionClient.ClinicalFlags(
                        record.getNutritionStatus().equals("severe"),
                        false, // Flags not stored in history; fallback defaults
                        record.getNutritionStatus().equals("severe")
                ),
                "HistoricalRecord",
                "Fitted against historical database entry."
        );

        HealthAdviceService.AdviceResult adviceResult = healthAdviceService.generateAdvice(
                String.valueOf(studentId),
                record.getAgeMonths(),
                activityLevel,
                allergies,
                shownAdviceIds != null ? shownAdviceIds : Collections.emptyList(),
                storedModelResponse
        );

        return ResponseEntity.ok(adviceResult);
    }

    /**
     * Updates allergy profile for a child.
     */
    @PutMapping("/allergies/{studentId}")
    public ResponseEntity<AllergyProfile> updateAllergies(
            @PathVariable Long studentId,
            @RequestBody AllergyUpdateDTO updateRequest
            ) {
        log.info("REST request to update active allergies list for student: {}", studentId);
        AllergyProfile profile = allergyProfileRepository.findById(studentId)
                .orElse(AllergyProfile.builder().studentId(studentId).build());

        profile.setAllergiesList(updateRequest.getAllergies());
        AllergyProfile saved = allergyProfileRepository.save(profile);
        return ResponseEntity.ok(saved);
    }
}

package com.mytadika.controller;

import com.mytadika.exception.ResourceNotFoundException;
import com.mytadika.exception.UnauthorizedAccessException;
import com.mytadika.model.Account;
import com.mytadika.model.AllergyProfile;
import com.mytadika.model.HealthRecord;
import com.mytadika.model.Role;
import com.mytadika.model.Student;
import com.mytadika.repository.AllergyProfileRepository;
import com.mytadika.repository.HealthRecordRepository;
import com.mytadika.repository.StudentRepository;
import com.mytadika.service.AiPredictionClient;
import com.mytadika.service.HealthAdviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
@Validated
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final HealthRecordRepository healthRecordRepository;
    private final AllergyProfileRepository allergyProfileRepository;
    private final StudentRepository studentRepository;
    private final AiPredictionClient aiPredictionClient;
    private final HealthAdviceService healthAdviceService;

    public HealthController(
            HealthRecordRepository healthRecordRepository,
            AllergyProfileRepository allergyProfileRepository,
            StudentRepository studentRepository,
            AiPredictionClient aiPredictionClient,
            HealthAdviceService healthAdviceService) {
        this.healthRecordRepository = healthRecordRepository;
        this.allergyProfileRepository = allergyProfileRepository;
        this.studentRepository = studentRepository;
        this.aiPredictionClient = aiPredictionClient;
        this.healthAdviceService = healthAdviceService;
    }

    private void assertCanAccessStudent(Long studentId, Account currentUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
        if (currentUser.getRole() == Role.PARENT
                && !student.getParent().getAccountId().equals(currentUser.getAccountId())) {
            throw new UnauthorizedAccessException("Cannot access another parent's child.");
        }
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

        public String getChildId() { return childId; }
        public void setChildId(String childId) { this.childId = childId; }
        public Double getAgeMonths() { return ageMonths; }
        public void setAgeMonths(Double ageMonths) { this.ageMonths = ageMonths; }
        public Double getWeightKg() { return weightKg; }
        public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
        public Double getHeightCm() { return heightCm; }
        public void setHeightCm(Double heightCm) { this.heightCm = heightCm; }
        public Double getMuacCm() { return muacCm; }
        public void setMuacCm(Double muacCm) { this.muacCm = muacCm; }
        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }
        public Integer getActivityLevel() { return activityLevel; }
        public void setActivityLevel(Integer activityLevel) { this.activityLevel = activityLevel; }
        public List<String> getAllergies() { return allergies; }
        public void setAllergies(List<String> allergies) { this.allergies = allergies; }
        public List<String> getShownAdviceIds() { return shownAdviceIds; }
        public void setShownAdviceIds(List<String> shownAdviceIds) { this.shownAdviceIds = shownAdviceIds; }
        public Long getRecordedBy() { return recordedBy; }
        public void setRecordedBy(Long recordedBy) { this.recordedBy = recordedBy; }
    }

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

        public Long getRecordId() { return recordId; }
        public void setRecordId(Long recordId) { this.recordId = recordId; }
        public HealthRecord getHealthRecord() { return healthRecord; }
        public void setHealthRecord(HealthRecord healthRecord) { this.healthRecord = healthRecord; }
        public HealthAdviceService.AdviceResult getAdvice() { return advice; }
        public void setAdvice(HealthAdviceService.AdviceResult advice) { this.advice = advice; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private Long recordId;
            private HealthRecord healthRecord;
            private HealthAdviceService.AdviceResult advice;

            public Builder recordId(Long recordId) { this.recordId = recordId; return this; }
            public Builder healthRecord(HealthRecord healthRecord) { this.healthRecord = healthRecord; return this; }
            public Builder advice(HealthAdviceService.AdviceResult advice) { this.advice = advice; return this; }
            public RecordResponseDTO build() { return new RecordResponseDTO(recordId, healthRecord, advice); }
        }
    }

    public static class AllergyUpdateDTO {
        private List<String> allergies;
        public AllergyUpdateDTO() {}
        public List<String> getAllergies() { return allergies; }
        public void setAllergies(List<String> allergies) { this.allergies = allergies; }
    }

    @PostMapping("/predict")
    @PreAuthorize("hasAnyRole('TEACHER','PARENT','ADMIN')")
    public ResponseEntity<HealthAdviceService.AdviceResult> predictAndAdvise(
            @Valid @RequestBody HealthRequestDTO request) {
        log.info("REST request to predict and generate advice for child: {}", request.getChildId());

        AiPredictionClient.PredictionResponse modelResponse = aiPredictionClient.predict(
                request.getChildId(), request.getAgeMonths(), request.getWeightKg(),
                request.getHeightCm(), request.getMuacCm(), request.getGender());

        HealthAdviceService.AdviceResult adviceResult = healthAdviceService.generateAdvice(
                request.getChildId(), request.getAgeMonths(), request.getActivityLevel(),
                request.getAllergies() != null ? request.getAllergies() : Collections.emptyList(),
                request.getShownAdviceIds() != null ? request.getShownAdviceIds() : Collections.emptyList(),
                modelResponse);

        return ResponseEntity.ok(adviceResult);
    }

    @PostMapping("/record")
    @PreAuthorize("hasAnyRole('TEACHER','PARENT','ADMIN')")
    public ResponseEntity<RecordResponseDTO> recordMeasurement(
            @Valid @RequestBody HealthRequestDTO request,
            @AuthenticationPrincipal Account currentUser) {
        log.info("REST request to record measurements and advise for student: {}", request.getChildId());

        Long studentId;
        try {
            studentId = Long.parseLong(request.getChildId());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
        assertCanAccessStudent(studentId, currentUser);

        AiPredictionClient.PredictionResponse modelResponse = aiPredictionClient.predict(
                request.getChildId(), request.getAgeMonths(), request.getWeightKg(),
                request.getHeightCm(), request.getMuacCm(), request.getGender());

        HealthAdviceService.AdviceResult adviceResult = healthAdviceService.generateAdvice(
                request.getChildId(), request.getAgeMonths(), request.getActivityLevel(),
                request.getAllergies() != null ? request.getAllergies() : Collections.emptyList(),
                request.getShownAdviceIds() != null ? request.getShownAdviceIds() : Collections.emptyList(),
                modelResponse);

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

        if (request.getAllergies() != null) {
            AllergyProfile allergyProfile = allergyProfileRepository.findById(studentId)
                    .orElse(AllergyProfile.builder().studentId(studentId).build());
            allergyProfile.setAllergiesList(request.getAllergies());
            allergyProfileRepository.save(allergyProfile);
        }

        return ResponseEntity.ok(RecordResponseDTO.builder()
                .recordId(savedRecord.getId())
                .healthRecord(savedRecord)
                .advice(adviceResult)
                .build());
    }

    @GetMapping("/history/{studentId}")
    public ResponseEntity<List<HealthRecord>> getHistory(
            @PathVariable Long studentId,
            @AuthenticationPrincipal Account currentUser) {
        log.info("REST request to fetch health log history for student: {}", studentId);
        assertCanAccessStudent(studentId, currentUser);
        return ResponseEntity.ok(healthRecordRepository.findByStudentIdOrderByRecordedAtDesc(studentId));
    }

    @GetMapping("/advice/{studentId}")
    public ResponseEntity<HealthAdviceService.AdviceResult> getLatestAdvice(
            @PathVariable Long studentId,
            @RequestParam(required = false, defaultValue = "1") int activityLevel,
            @RequestParam(required = false) List<String> shownAdviceIds,
            @AuthenticationPrincipal Account currentUser) {
        log.info("REST request to fetch latest AI advice cards for student: {}", studentId);
        assertCanAccessStudent(studentId, currentUser);

        Optional<HealthRecord> latestRecordOpt = healthRecordRepository.findFirstByStudentIdOrderByRecordedAtDesc(studentId);
        if (latestRecordOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HealthRecord record = latestRecordOpt.get();

        List<String> allergies = allergyProfileRepository.findById(studentId)
                .map(AllergyProfile::getAllergiesList)
                .orElse(Collections.emptyList());

        AiPredictionClient.PredictionResponse storedModelResponse = new AiPredictionClient.PredictionResponse(
                String.valueOf(studentId),
                record.getNutritionStatus(),
                record.getNutritionStatus().equals("normal") ? 0 : (record.getNutritionStatus().equals("moderate") ? 1 : 2),
                1.0,
                new AiPredictionClient.ProbabilityBreakdown(
                        record.getNutritionStatus().equals("normal") ? 1.0 : 0.0,
                        record.getNutritionStatus().equals("moderate") ? 1.0 : 0.0,
                        record.getNutritionStatus().equals("severe") ? 1.0 : 0.0),
                new AiPredictionClient.ClinicalFlags(
                        record.getNutritionStatus().equals("severe"), false,
                        record.getNutritionStatus().equals("severe")),
                "HistoricalRecord",
                "Fitted against historical database entry.");

        HealthAdviceService.AdviceResult adviceResult = healthAdviceService.generateAdvice(
                String.valueOf(studentId), record.getAgeMonths(), activityLevel, allergies,
                shownAdviceIds != null ? shownAdviceIds : Collections.emptyList(),
                storedModelResponse);

        return ResponseEntity.ok(adviceResult);
    }

    @GetMapping("/allergies/{studentId}")
    public ResponseEntity<List<String>> getAllergies(
            @PathVariable Long studentId,
            @AuthenticationPrincipal Account currentUser) {
        assertCanAccessStudent(studentId, currentUser);
        return ResponseEntity.ok(allergyProfileRepository.findById(studentId)
                .map(AllergyProfile::getAllergiesList)
                .orElse(Collections.emptyList()));
    }

    @PutMapping("/allergies/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','PARENT','ADMIN')")
    public ResponseEntity<AllergyProfile> updateAllergies(
            @PathVariable Long studentId,
            @RequestBody AllergyUpdateDTO updateRequest,
            @AuthenticationPrincipal Account currentUser) {
        log.info("REST request to update active allergies list for student: {}", studentId);
        assertCanAccessStudent(studentId, currentUser);
        AllergyProfile profile = allergyProfileRepository.findById(studentId)
                .orElse(AllergyProfile.builder().studentId(studentId).build());
        profile.setAllergiesList(updateRequest.getAllergies());
        return ResponseEntity.ok(allergyProfileRepository.save(profile));
    }
}

package com.mytadika.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.mytadika.model.AllergyProfile;
import com.mytadika.model.HealthRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.mytadika.repository.AllergyProfileRepository;
import com.mytadika.repository.HealthRecordRepository;
import com.mytadika.service.AiPredictionClient;
import com.mytadika.service.HealthAdviceService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*") // Adjust origins in production settings
@Validated
@Slf4j
@RequiredArgsConstructor
public class HealthController {

        private final HealthRecordRepository healthRecordRepository;
        private final AllergyProfileRepository allergyProfileRepository;
        private final AiPredictionClient aiPredictionClient;
        private final HealthAdviceService healthAdviceService;

        // DTO for incoming measurements & advice requests
        @Data
        public static class HealthRequestDTO {
                @NotBlank(message = "childId is required")
                private String childId;

                @NotNull(message = "ageMonths is required")
                @Min(value = 0, message = "ageMonths must be >= 0")
                @Max(value = 72, message = "ageMonths must be <= 72")
                private Double ageMonths;

                @NotNull(message = "weightKg is required")
                @DecimalMin(value = "2.5", message = "weightKg must be >= 2.5")
                @DecimalMax(value = "30", message = "weightKg must be <= 30")
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
        }

        // Wrapper response indicating saved record ID and generated advice
        @Data
        @Builder
        public static class RecordResponseDTO {
                private Long recordId;
                private HealthRecord healthRecord;
                private HealthAdviceService.AdviceResult advice;
        }

        // DTO for allergy updates
        @Data
        public static class AllergyUpdateDTO {
                private List<String> allergies;
        }

        // Surfaces the specific @Valid failure message (e.g. "weightKg must be >= 2.5")
        // instead of Spring's default blank "Bad Request" body, so the frontend can show
        // the caller exactly what was wrong instead of a generic "failed to save".
        @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
        public ResponseEntity<?> handleValidationError(org.springframework.web.bind.MethodArgumentNotValidException e) {
                String message = e.getBindingResult().getFieldErrors().stream()
                                .map(err -> err.getDefaultMessage())
                                .filter(m -> m != null)
                                .findFirst()
                                .orElse("Invalid request.");
                return ResponseEntity.badRequest().body(Map.of("error", message));
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
                                request.getGender());

                // Run Rules Engine port
                HealthAdviceService.AdviceResult adviceResult = healthAdviceService.generateAdvice(
                                request.getChildId(),
                                request.getAgeMonths(),
                                request.getActivityLevel(),
                                request.getAllergies() != null ? request.getAllergies() : Collections.emptyList(),
                                request.getShownAdviceIds() != null ? request.getShownAdviceIds()
                                                : Collections.emptyList(),
                                modelResponse);

                return ResponseEntity.ok(adviceResult);
        }

        /**
         * Endpoint to record new measurement, run AI checks, and persist the record.
         */
        @PostMapping("/record")
        @Transactional
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
                                request.getGender());

                HealthAdviceService.AdviceResult adviceResult = healthAdviceService.generateAdvice(
                                request.getChildId(),
                                request.getAgeMonths(),
                                request.getActivityLevel(),
                                request.getAllergies() != null ? request.getAllergies() : Collections.emptyList(),
                                request.getShownAdviceIds() != null ? request.getShownAdviceIds()
                                                : Collections.emptyList(),
                                modelResponse);

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
         * Updates an existing health measurement record — recomputes BMI and
         * nutrition status via the same AI prediction path used when creating one.
         */
        @PutMapping("/records/{id}")
        @Transactional
        public ResponseEntity<?> updateRecord(@PathVariable Long id, @Valid @RequestBody HealthRequestDTO request) {
                HealthRecord record = healthRecordRepository.findById(id).orElse(null);
                if (record == null) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Health record not found"));
                }

                AiPredictionClient.PredictionResponse modelResponse = aiPredictionClient.predict(
                                request.getChildId(),
                                request.getAgeMonths(),
                                request.getWeightKg(),
                                request.getHeightCm(),
                                request.getMuacCm(),
                                request.getGender());

                HealthAdviceService.AdviceResult adviceResult = healthAdviceService.generateAdvice(
                                request.getChildId(),
                                request.getAgeMonths(),
                                request.getActivityLevel(),
                                request.getAllergies() != null ? request.getAllergies() : Collections.emptyList(),
                                request.getShownAdviceIds() != null ? request.getShownAdviceIds()
                                                : Collections.emptyList(),
                                modelResponse);

                double heightM = request.getHeightCm() / 100.0;
                double bmi = request.getWeightKg() / (heightM * heightM);

                record.setAgeMonths(request.getAgeMonths());
                record.setWeightKg(request.getWeightKg());
                record.setHeightCm(request.getHeightCm());
                record.setMuacCm(request.getMuacCm());
                record.setBmi(bmi);
                record.setNutritionStatus(adviceResult.getStatus());

                HealthRecord saved = healthRecordRepository.save(record);

                if (request.getAllergies() != null) {
                        Long studentId = saved.getStudentId();
                        AllergyProfile allergyProfile = allergyProfileRepository.findById(studentId)
                                        .orElse(AllergyProfile.builder().studentId(studentId).build());
                        allergyProfile.setAllergiesList(request.getAllergies());
                        allergyProfileRepository.save(allergyProfile);
                }

                RecordResponseDTO response = RecordResponseDTO.builder()
                                .recordId(saved.getId())
                                .healthRecord(saved)
                                .advice(adviceResult)
                                .build();

                return ResponseEntity.ok(response);
        }

        /**
         * Deletes a health measurement record.
         */
        @DeleteMapping("/records/{id}")
        public ResponseEntity<?> deleteRecord(@PathVariable Long id) {
                if (!healthRecordRepository.existsById(id)) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Health record not found"));
                }
                healthRecordRepository.deleteById(id);
                return ResponseEntity.ok(Map.of("status", "deleted"));
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
         * Fetches current advice for a student using their most recent record and
         * allergy configuration.
         */
        @GetMapping("/advice/{studentId}")
        public ResponseEntity<HealthAdviceService.AdviceResult> getLatestAdvice(
                        @PathVariable Long studentId,
                        @RequestParam(required = false, defaultValue = "1") int activityLevel,
                        @RequestParam(required = false) List<String> shownAdviceIds) {
                log.info("REST request to fetch latest AI advice cards for student: {}", studentId);

                Optional<HealthRecord> latestRecordOpt = healthRecordRepository
                                .findFirstByStudentIdOrderByRecordedAtDesc(studentId);
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
                                record.getNutritionStatus().equals("normal") ? 0
                                                : (record.getNutritionStatus().equals("moderate") ? 1 : 2),
                                1.0, // Stored record has full certainty
                                new AiPredictionClient.ProbabilityBreakdown(
                                                record.getNutritionStatus().equals("normal") ? 1.0 : 0.0,
                                                record.getNutritionStatus().equals("moderate") ? 1.0 : 0.0,
                                                record.getNutritionStatus().equals("severe") ? 1.0 : 0.0),
                                new AiPredictionClient.ClinicalFlags(
                                                record.getNutritionStatus().equals("severe"),
                                                false, // Flags not stored in history; fallback defaults
                                                record.getNutritionStatus().equals("severe")),
                                "HistoricalRecord",
                                "Fitted against historical database entry.");

                HealthAdviceService.AdviceResult adviceResult = healthAdviceService.generateAdvice(
                                String.valueOf(studentId),
                                record.getAgeMonths(),
                                activityLevel,
                                allergies,
                                shownAdviceIds != null ? shownAdviceIds : Collections.emptyList(),
                                storedModelResponse);

                return ResponseEntity.ok(adviceResult);
        }

        /**
         * Fetches the active allergy list for a child.
         */
        @GetMapping("/allergies/{studentId}")
        public ResponseEntity<List<String>> getAllergies(@PathVariable Long studentId) {
                return ResponseEntity.ok(allergyProfileRepository.findById(studentId)
                                .map(AllergyProfile::getAllergiesList)
                                .orElse(Collections.emptyList()));
        }

        /**
         * Updates allergy profile for a child.
         */
        @PutMapping("/allergies/{studentId}")
        public ResponseEntity<AllergyProfile> updateAllergies(
                        @PathVariable Long studentId,
                        @RequestBody AllergyUpdateDTO updateRequest) {
                log.info("REST request to update active allergies list for student: {}", studentId);
                AllergyProfile profile = allergyProfileRepository.findById(studentId)
                                .orElse(AllergyProfile.builder().studentId(studentId).build());

                profile.setAllergiesList(updateRequest.getAllergies());
                AllergyProfile saved = allergyProfileRepository.save(profile);
                return ResponseEntity.ok(saved);
        }
}

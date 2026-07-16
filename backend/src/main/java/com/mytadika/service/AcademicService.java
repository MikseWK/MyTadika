package com.mytadika.service;

import com.mytadika.model.AcademicRecord;
import com.mytadika.model.AcademicScoreItem;
import com.mytadika.repository.AcademicRecordRepository;
import com.mytadika.repository.AcademicScoreItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AcademicService {

    private final AcademicRecordRepository academicRecordRepository;
    private final AcademicScoreItemRepository academicScoreItemRepository;
    private final GradeCalculationService gradeCalculationService;

    public AcademicService(AcademicRecordRepository academicRecordRepository,
                           AcademicScoreItemRepository academicScoreItemRepository,
                           GradeCalculationService gradeCalculationService) {
        this.academicRecordRepository = academicRecordRepository;
        this.academicScoreItemRepository = academicScoreItemRepository;
        this.gradeCalculationService = gradeCalculationService;
    }

    public List<Map<String, Object>> getRecordsByStudent(Long studentId) {
        return academicRecordRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getRecord(Long id) {
        AcademicRecord record = academicRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Academic record not found"));
        return toMap(record);
    }

    @Transactional
    public Map<String, Object> createRecord(Long studentId, String academicTerm, List<Map<String, Object>> subjects) {
        if (academicTerm == null || academicTerm.isBlank())
            throw new IllegalArgumentException("Academic term is required");
        if (subjects == null || subjects.isEmpty())
            throw new IllegalArgumentException("At least one subject score is required");

        List<Double> scores = subjects.stream()
                .map(this::extractScore)
                .collect(Collectors.toList());
        double average = gradeCalculationService.calculateAverage(scores);
        String grade = gradeCalculationService.calculateGrade(average);

        AcademicRecord record = AcademicRecord.builder()
                .studentId(studentId)
                .academicTerm(academicTerm)
                .averageMark(average)
                .finalGrade(grade)
                .build();
        record = academicRecordRepository.save(record);

        saveScoreItems(record.getId(), subjects);
        return toMap(record);
    }

    @Transactional
    public Map<String, Object> updateRecord(Long id, String academicTerm, List<Map<String, Object>> subjects) {
        AcademicRecord record = academicRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Academic record not found"));
        if (academicTerm == null || academicTerm.isBlank())
            throw new IllegalArgumentException("Academic term is required");
        if (subjects == null || subjects.isEmpty())
            throw new IllegalArgumentException("At least one subject score is required");

        List<Double> scores = subjects.stream()
                .map(this::extractScore)
                .collect(Collectors.toList());
        double average = gradeCalculationService.calculateAverage(scores);
        String grade = gradeCalculationService.calculateGrade(average);

        record.setAcademicTerm(academicTerm);
        record.setAverageMark(average);
        record.setFinalGrade(grade);
        record = academicRecordRepository.save(record);

        academicScoreItemRepository.deleteByAcademicRecordId(record.getId());
        saveScoreItems(record.getId(), subjects);
        return toMap(record);
    }

    @Transactional
    public void deleteRecord(Long id) {
        if (!academicRecordRepository.existsById(id)) {
            throw new RuntimeException("Academic record not found");
        }
        academicScoreItemRepository.deleteByAcademicRecordId(id);
        academicRecordRepository.deleteById(id);
    }

    private double validateScore(double score) {
        if (score < 0 || score > 100)
            throw new IllegalArgumentException("Scores must be between 0 and 100");
        return score;
    }

    // Subjects arrive as a raw Map from JSON, so a missing/blank "subject" or a
    // non-numeric "score" (or a missing one) would otherwise throw an uncaught
    // NullPointerException/ClassCastException instead of a clean validation error.
    private double extractScore(Map<String, Object> subject) {
        Object rawSubjectName = subject.get("subject");
        if (!(rawSubjectName instanceof String) || ((String) rawSubjectName).isBlank()) {
            throw new IllegalArgumentException("Each subject entry must include a subject name");
        }
        Object rawScore = subject.get("score");
        if (!(rawScore instanceof Number)) {
            throw new IllegalArgumentException("Each subject must have a valid numeric score");
        }
        return validateScore(((Number) rawScore).doubleValue());
    }

    private void saveScoreItems(Long recordId, List<Map<String, Object>> subjects) {
        for (Map<String, Object> s : subjects) {
            AcademicScoreItem item = AcademicScoreItem.builder()
                    .academicRecordId(recordId)
                    .subjectName((String) s.get("subject"))
                    .score(extractScore(s))
                    .build();
            academicScoreItemRepository.save(item);
        }
    }

    private Map<String, Object> toMap(AcademicRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("studentId", record.getStudentId());
        map.put("academicTerm", record.getAcademicTerm());
        map.put("averageMark", record.getAverageMark());
        map.put("finalGrade", record.getFinalGrade());
        map.put("gradeLabel", gradeCalculationService.gradeLabel(record.getFinalGrade()));
        map.put("createdAt", record.getCreatedAt() != null ? record.getCreatedAt().toString() : null);

        List<Map<String, Object>> items = academicScoreItemRepository.findByAcademicRecordId(record.getId()).stream()
                .map(item -> {
                    Map<String, Object> im = new LinkedHashMap<>();
                    im.put("id", item.getId());
                    im.put("subject", item.getSubjectName());
                    im.put("score", item.getScore());
                    return im;
                })
                .collect(Collectors.toList());
        map.put("scores", items);
        return map;
    }
}

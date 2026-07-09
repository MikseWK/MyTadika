package com.mytadika.service;

import com.mytadika.model.AcademicRecord;
import com.mytadika.model.AcademicScoreItem;
import com.mytadika.model.Assignment;
import com.mytadika.model.Student;
import com.mytadika.model.StudentClassroom;
import com.mytadika.repository.AcademicRecordRepository;
import com.mytadika.repository.AcademicScoreItemRepository;
import com.mytadika.repository.AssignmentRepository;
import com.mytadika.repository.ClassworkCompletionRepository;
import com.mytadika.repository.StudentClassroomRepository;
import com.mytadika.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentAnalysisService {

    private final StudentRepository studentRepository;
    private final AcademicRecordRepository academicRecordRepository;
    private final AcademicScoreItemRepository academicScoreItemRepository;
    private final StudentClassroomRepository studentClassroomRepository;
    private final AssignmentRepository assignmentRepository;
    private final ClassworkCompletionRepository classworkCompletionRepository;
    private final AcademicPredictionClient predictionClient;
    private final AcademicAdviceService academicAdviceService;

    // Domain taxonomy — must stay in sync with AI/academic/api/schemas.py's
    // DOMAINS / SUBJECT_TO_DOMAIN. "General" (an Assignment.topic value with no
    // academic-score equivalent) is intentionally left unmapped and ignored.
    private static final Map<String, String> SUBJECT_TO_DOMAIN = Map.of(
            "Bahasa Melayu", "language_literacy",
            "English", "language_literacy",
            "Math", "stem_logic",
            "Science", "stem_logic",
            "Creative Arts", "creative_arts",
            "Physical Education", "physical_movement");

    private static final List<String> DOMAINS = List.of(
            "language_literacy", "stem_logic", "creative_arts", "physical_movement");

    private static final String NOT_ENOUGH_DATA_MESSAGE =
            "Not enough academic data yet to generate a progress summary.";

    public StudentAnalysisService(StudentRepository studentRepository,
            AcademicRecordRepository academicRecordRepository,
            AcademicScoreItemRepository academicScoreItemRepository,
            StudentClassroomRepository studentClassroomRepository,
            AssignmentRepository assignmentRepository,
            ClassworkCompletionRepository classworkCompletionRepository,
            AcademicPredictionClient predictionClient,
            AcademicAdviceService academicAdviceService) {
        this.studentRepository = studentRepository;
        this.academicRecordRepository = academicRecordRepository;
        this.academicScoreItemRepository = academicScoreItemRepository;
        this.studentClassroomRepository = studentClassroomRepository;
        this.assignmentRepository = assignmentRepository;
        this.classworkCompletionRepository = classworkCompletionRepository;
        this.predictionClient = predictionClient;
        this.academicAdviceService = academicAdviceService;
    }

    public Map<String, Object> getProgressSummary(Long studentId, Long recordId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        List<AcademicRecord> allRecords = academicRecordRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        if (allRecords.isEmpty()) {
            return notEnoughData(studentId);
        }

        // Anchor on whichever term the caller selected (e.g. the term dropdown), not
        // always the newest — the two terms before it (if any) still feed the trend
        // comparison. Falls back to the newest term if recordId is missing/unknown.
        List<AcademicRecord> records = allRecords;
        if (recordId != null) {
            for (int i = 0; i < allRecords.size(); i++) {
                if (allRecords.get(i).getId().equals(recordId)) {
                    records = allRecords.subList(i, allRecords.size());
                    break;
                }
            }
        }

        // Selected term first; use up to 2 terms before it for trend comparison.
        List<AcademicRecord> recentRecords = records.size() > 3 ? records.subList(0, 3) : records;
        List<Long> recordIds = recentRecords.stream().map(AcademicRecord::getId).collect(Collectors.toList());
        List<AcademicScoreItem> allItems = academicScoreItemRepository.findByAcademicRecordIdIn(recordIds);
        Map<Long, List<AcademicScoreItem>> itemsByRecord = allItems.stream()
                .collect(Collectors.groupingBy(AcademicScoreItem::getAcademicRecordId));

        // termDomainAvgs.get(0) = newest term's per-domain averages, get(1) = the term before that, etc.
        List<Map<String, Double>> termDomainAvgs = new ArrayList<>();
        for (AcademicRecord record : recentRecords) {
            List<AcademicScoreItem> items = itemsByRecord.getOrDefault(record.getId(), Collections.emptyList());
            Map<String, List<Double>> scoresByDomain = new HashMap<>();
            for (AcademicScoreItem item : items) {
                String domain = SUBJECT_TO_DOMAIN.get(item.getSubjectName());
                if (domain == null) continue;
                scoresByDomain.computeIfAbsent(domain, k -> new ArrayList<>()).add(item.getScore());
            }
            Map<String, Double> domainAvgs = new HashMap<>();
            scoresByDomain.forEach((domain, scores) ->
                    domainAvgs.put(domain, scores.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
            termDomainAvgs.add(domainAvgs);
        }

        Map<String, Double> latestDomainAvgs = termDomainAvgs.get(0);
        if (latestDomainAvgs.isEmpty()) {
            return notEnoughData(studentId);
        }

        // Classwork completion/overdue per domain, across all of the student's classrooms.
        List<StudentClassroom> links = studentClassroomRepository.findByStudentId(studentId);
        List<Long> classroomIds = links.stream().map(StudentClassroom::getClassroomId).distinct().collect(Collectors.toList());
        List<Assignment> assignments = classroomIds.isEmpty()
                ? Collections.emptyList()
                : assignmentRepository.findByClassroomIdIn(classroomIds);
        Set<Long> completedIds = classworkCompletionRepository.findCompletedAssignmentIdsByStudentId(studentId);
        String today = LocalDate.now().toString(); // YYYY-MM-DD, matches Assignment.dueDate's plain-string format

        Map<String, List<Assignment>> assignmentsByDomain = new HashMap<>();
        for (Assignment a : assignments) {
            String domain = SUBJECT_TO_DOMAIN.get(a.getTopic());
            if (domain == null) continue; // "General" or an unmapped topic
            assignmentsByDomain.computeIfAbsent(domain, k -> new ArrayList<>()).add(a);
        }

        // Ipsative cross-domain stats, computed only over domains present this term.
        double[] scoresArr = latestDomainAvgs.values().stream().mapToDouble(Double::doubleValue).toArray();
        double crossMean = Arrays.stream(scoresArr).average().orElse(0);
        double variance = Arrays.stream(scoresArr).map(s -> Math.pow(s - crossMean, 2)).average().orElse(0);
        double crossStd = Math.max(Math.sqrt(variance), 1e-6);

        List<AcademicPredictionClient.DomainFeatureRequest> domainRows = new ArrayList<>();
        for (String domain : DOMAINS) {
            Double avgScore = latestDomainAvgs.get(domain);
            if (avgScore == null) continue; // no score data for this domain this term

            String trend = deriveTrend(domain, avgScore, termDomainAvgs);

            List<Assignment> domainAssignments = assignmentsByDomain.getOrDefault(domain, Collections.emptyList());
            double completionRate;
            int overdueCount;
            if (domainAssignments.isEmpty()) {
                // No tracked classwork in this domain yet — neutral default so an
                // untracked domain isn't mistaken for a struggling one.
                completionRate = 1.0;
                overdueCount = 0;
            } else {
                long completed = domainAssignments.stream().filter(a -> completedIds.contains(a.getId())).count();
                completionRate = (double) completed / domainAssignments.size();
                overdueCount = (int) domainAssignments.stream()
                        .filter(a -> !completedIds.contains(a.getId()))
                        .filter(a -> a.getDueDate() != null && a.getDueDate().compareTo(today) < 0)
                        .count();
            }

            double z = (avgScore - crossMean) / crossStd;
            domainRows.add(new AcademicPredictionClient.DomainFeatureRequest(
                    domain, avgScore, trend, completionRate, overdueCount, z));
        }

        AcademicPredictionClient.PredictionResponse prediction =
                predictionClient.predict(String.valueOf(studentId), domainRows);

        Map<String, AcademicPredictionClient.DomainFeatureRequest> rowsByDomain = domainRows.stream()
                .collect(Collectors.toMap(AcademicPredictionClient.DomainFeatureRequest::domain, r -> r));

        // The model's level is purely ipsative (relative to the child's OTHER domains
        // this term) — it has no concept of an absolute failing score. Left alone, a
        // uniformly-low first term (e.g. 11/1/2/3/4/5) still labels whichever domain is
        // "least bad" as a Strength. Apply an absolute floor using the same grade bands
        // GradeCalculationService already uses, so a failing domain is never called a
        // Strength, and a barely-passing one is never called better than Developing.
        List<AcademicPredictionClient.DomainPredictionResult> adjustedDomains = prediction.domains().stream()
                .map(d -> applyAbsoluteFloor(d, rowsByDomain.get(d.domain())))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", studentId);
        result.put("hasData", true);
        result.put("narrative", buildNarrative(student.getFullName(), adjustedDomains));
        result.put("domains", adjustedDomains.stream()
                .map(d -> domainResultToMap(d, rowsByDomain.get(d.domain())))
                .collect(Collectors.toList()));
        result.put("modelVersion", prediction.model_version());
        result.put("disclaimer", prediction.disclaimer());
        return result;
    }

    private static final double ABSOLUTE_FAIL_THRESHOLD = 40; // matches GradeCalculationService's "F" cutoff
    private static final double ABSOLUTE_PASS_THRESHOLD = 60; // matches GradeCalculationService's "C" cutoff

    private AcademicPredictionClient.DomainPredictionResult applyAbsoluteFloor(
            AcademicPredictionClient.DomainPredictionResult d, AcademicPredictionClient.DomainFeatureRequest row) {
        if (row == null) return d;
        double avgScore = row.domain_avg_score();
        String level = d.level();
        if (avgScore < ABSOLUTE_FAIL_THRESHOLD) {
            level = "Emerging";
        } else if (avgScore < ABSOLUTE_PASS_THRESHOLD && "Strength".equals(level)) {
            level = "Developing";
        }
        if (level.equals(d.level())) return d;
        return new AcademicPredictionClient.DomainPredictionResult(d.domain(), d.domain_display_name(), level, d.confidence());
    }

    private String deriveTrend(String domain, double latestAvg, List<Map<String, Double>> termDomainAvgs) {
        List<Double> priorAvgs = new ArrayList<>();
        for (int i = 1; i < termDomainAvgs.size(); i++) {
            Double prior = termDomainAvgs.get(i).get(domain);
            if (prior != null) priorAvgs.add(prior);
        }
        if (priorAvgs.isEmpty()) return "stable"; // only one term of history — nothing to compare yet

        double priorMean = priorAvgs.stream().mapToDouble(Double::doubleValue).average().orElse(latestAvg);
        double diff = latestAvg - priorMean;
        if (diff > 3) return "improving";
        if (diff < -3) return "declining";
        return "stable";
    }

    private Map<String, Object> notEnoughData(Long studentId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("studentId", studentId);
        map.put("hasData", false);
        map.put("message", NOT_ENOUGH_DATA_MESSAGE);
        return map;
    }

    private Map<String, Object> domainResultToMap(AcademicPredictionClient.DomainPredictionResult d,
            AcademicPredictionClient.DomainFeatureRequest row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("domain", d.domain());
        m.put("domainDisplayName", d.domain_display_name());
        m.put("level", d.level());
        m.put("confidence", d.confidence());
        if (row != null) {
            m.put("advice", academicAdviceService.getAdvice(d.domain(), d.level(), row.domain_trend(), row.domain_completion_rate()));
        } else {
            m.put("advice", List.of());
        }
        return m;
    }

    // Phrased around strengths/opportunities, never deficits — matching the tone
    // already established in the health-advice disclaimers.
    private String buildNarrative(String childName, List<AcademicPredictionClient.DomainPredictionResult> domains) {
        String name = (childName == null || childName.isBlank()) ? "Your child" : childName;

        Optional<AcademicPredictionClient.DomainPredictionResult> top = domains.stream()
                .filter(d -> "Strength".equals(d.level()))
                .max(Comparator.comparingDouble(AcademicPredictionClient.DomainPredictionResult::confidence));

        Optional<AcademicPredictionClient.DomainPredictionResult> growth = domains.stream()
                .filter(d -> "Emerging".equals(d.level()))
                .filter(d -> top.isEmpty() || !d.domain().equals(top.get().domain()))
                .max(Comparator.comparingDouble(AcademicPredictionClient.DomainPredictionResult::confidence));

        if (top.isPresent() && growth.isPresent()) {
            return String.format("%s shows strong engagement in %s, with room to explore in %s.",
                    name, top.get().domain_display_name(), growth.get().domain_display_name());
        }
        if (top.isPresent()) {
            return String.format("%s shows strong engagement in %s, and is developing steadily across other areas.",
                    name, top.get().domain_display_name());
        }
        if (growth.isPresent()) {
            return String.format("%s is developing steadily across most areas, with %s as a good area to explore further together.",
                    name, growth.get().domain_display_name());
        }
        return String.format("%s is showing steady, well-rounded engagement across all areas this term.", name);
    }
}

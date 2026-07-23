package com.mytadika.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GradeCalculationService {

    public double calculateAverage(List<Double> scores) {
        if (scores == null || scores.isEmpty()) return 0.0;
        return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public String calculateGrade(double average) {
        if (average >= 80) return "A";
        if (average >= 70) return "B";
        if (average >= 60) return "C";
        if (average >= 50) return "D";
        if (average >= 40) return "E";
        return "F";
    }

    public String gradeLabel(String grade) {
        return switch (grade) {
            case "A" -> "Excellent";
            case "B" -> "Good";
            case "C" -> "Satisfactory";
            case "D" -> "Passing";
            case "E" -> "Borderline";
            default -> "Unsatisfactory";
        };
    }
}

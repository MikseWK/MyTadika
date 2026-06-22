package com.mytadika.service;

import org.springframework.stereotype.Service;

import java.util.List;

/** Malaysia preschool grading scale (CLAUDE.md §6.3). */
@Service
public class GradeCalculationService {

    public double calculateAverage(List<Double> scores) {
        return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public String calculateGrade(double averageMark) {
        if (averageMark >= 80) return "A";
        if (averageMark >= 70) return "B";
        if (averageMark >= 60) return "C";
        if (averageMark >= 50) return "D";
        if (averageMark >= 40) return "E";
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

package com.mytadika.dto;

import com.mytadika.model.AcademicRecord;

import java.time.LocalDateTime;
import java.util.List;

public class AcademicRecordResponseDTO {

    private Long id;
    private Long studentId;
    private String studentName;
    private String academicTerm;
    private Double averageMark;
    private String finalGrade;
    private String gradeLabel;
    private List<ScoreItemResponseDTO> scores;
    private LocalDateTime createdAt;

    public static AcademicRecordResponseDTO from(AcademicRecord record, String gradeLabel) {
        AcademicRecordResponseDTO dto = new AcademicRecordResponseDTO();
        dto.id = record.getId();
        dto.studentId = record.getStudent().getId();
        dto.studentName = record.getStudent().getFullName();
        dto.academicTerm = record.getAcademicTerm();
        dto.averageMark = record.getAverageMark();
        dto.finalGrade = record.getFinalGrade();
        dto.gradeLabel = gradeLabel;
        dto.scores = record.getScoreItems().stream().map(ScoreItemResponseDTO::from).toList();
        dto.createdAt = record.getCreatedAt();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getAcademicTerm() {
        return academicTerm;
    }

    public Double getAverageMark() {
        return averageMark;
    }

    public String getFinalGrade() {
        return finalGrade;
    }

    public String getGradeLabel() {
        return gradeLabel;
    }

    public List<ScoreItemResponseDTO> getScores() {
        return scores;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

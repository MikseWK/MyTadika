package com.mytadika.dto;

import com.mytadika.model.AcademicScoreItem;

public class ScoreItemResponseDTO {

    private Long id;
    private String subjectName;
    private Double score;

    public static ScoreItemResponseDTO from(AcademicScoreItem item) {
        ScoreItemResponseDTO dto = new ScoreItemResponseDTO();
        dto.id = item.getId();
        dto.subjectName = item.getSubjectName();
        dto.score = item.getScore();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public Double getScore() {
        return score;
    }
}

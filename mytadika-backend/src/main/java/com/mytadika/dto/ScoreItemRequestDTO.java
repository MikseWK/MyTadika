package com.mytadika.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ScoreItemRequestDTO {

    @NotBlank(message = "Please complete all mandatory fields before saving.")
    @Size(max = 50)
    private String subjectName;

    @NotNull(message = "Invalid score entered. Scores must be between 0 and 100.")
    @DecimalMin(value = "0", message = "Invalid score entered. Scores must be between 0 and 100.")
    @DecimalMax(value = "100", message = "Invalid score entered. Scores must be between 0 and 100.")
    private Double score;

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}

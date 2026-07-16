package com.mytadika.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Used for both submitting a new record (POST) and resubmitting/recalculating
 * an existing one (PUT) — both replace the term + full score list, so one
 * shape covers both per the report's UC003 basic/alt flows.
 */
public class AcademicRecordRequestDTO {

    @NotBlank(message = "Please complete all mandatory fields before saving.")
    @Size(max = 50)
    private String academicTerm;

    @NotEmpty(message = "Please provide at least one subject score.")
    @Valid
    private List<ScoreItemRequestDTO> scores;

    public String getAcademicTerm() {
        return academicTerm;
    }

    public void setAcademicTerm(String academicTerm) {
        this.academicTerm = academicTerm;
    }

    public List<ScoreItemRequestDTO> getScores() {
        return scores;
    }

    public void setScores(List<ScoreItemRequestDTO> scores) {
        this.scores = scores;
    }
}

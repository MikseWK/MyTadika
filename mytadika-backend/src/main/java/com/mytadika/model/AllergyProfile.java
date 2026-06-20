package com.mytadika.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "allergy_profiles")
public class AllergyProfile {

    @Id
    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "active_allergies", columnDefinition = "TEXT")
    private String activeAllergies;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AllergyProfile() {}

    private AllergyProfile(Builder builder) {
        this.studentId = builder.studentId;
        this.activeAllergies = builder.activeAllergies;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long studentId;
        private String activeAllergies;

        public Builder studentId(Long v)          { this.studentId = v; return this; }
        public Builder activeAllergies(String v)  { this.activeAllergies = v; return this; }
        public AllergyProfile build()             { return new AllergyProfile(this); }
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // Getters
    public Long getStudentId()        { return studentId; }
    public String getActiveAllergies(){ return activeAllergies; }
    public LocalDateTime getUpdatedAt(){ return updatedAt; }

    // Setters
    public void setStudentId(Long v)        { this.studentId = v; }
    public void setActiveAllergies(String v){ this.activeAllergies = v; }

    public List<String> getAllergiesList() {
        if (activeAllergies == null || activeAllergies.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(activeAllergies.split(","))
                .map(String::trim).map(String::toLowerCase)
                .filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    public void setAllergiesList(List<String> allergies) {
        if (allergies == null || allergies.isEmpty()) {
            this.activeAllergies = "";
        } else {
            this.activeAllergies = allergies.stream()
                    .map(String::trim).map(String::toLowerCase)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(","));
        }
    }
}

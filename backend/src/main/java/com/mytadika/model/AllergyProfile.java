package com.mytadika.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "allergy_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllergyProfile {

    @Id
    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "active_allergies", columnDefinition = "TEXT")
    private String activeAllergies; // Comma-separated values, e.g., "milk,peanut"

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Helper to retrieve list of active allergies normalized to lower-case.
     */
    public List<String> getAllergiesList() {
        if (activeAllergies == null || activeAllergies.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(activeAllergies.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Helper to set allergies list as comma-separated string.
     */
    public void setAllergiesList(List<String> allergies) {
        if (allergies == null || allergies.isEmpty()) {
            this.activeAllergies = "";
        } else {
            this.activeAllergies = allergies.stream()
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(","));
        }
    }
}

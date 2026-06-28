package com.mytadika.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "health_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "age_months", nullable = false)
    private Double ageMonths;

    @Column(name = "weight_kg", nullable = false)
    private Double weightKg;

    @Column(name = "height_cm", nullable = false)
    private Double heightCm;

    @Column(name = "muac_cm")
    private Double muacCm;

    @Column(nullable = false)
    private Double bmi;

    @Column(name = "nutrition_status", nullable = false, length = 50)
    private String nutritionStatus;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
        if (bmi == null && weightKg != null && heightCm != null) {
            double heightM = heightCm / 100.0;
            bmi = weightKg / (heightM * heightM);
        }
    }
}

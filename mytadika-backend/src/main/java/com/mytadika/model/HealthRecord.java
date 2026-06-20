package com.mytadika.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "health_records")
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

    protected HealthRecord() {}

    private HealthRecord(Builder builder) {
        this.studentId = builder.studentId;
        this.ageMonths = builder.ageMonths;
        this.weightKg = builder.weightKg;
        this.heightCm = builder.heightCm;
        this.muacCm = builder.muacCm;
        this.bmi = builder.bmi;
        this.nutritionStatus = builder.nutritionStatus;
        this.recordedBy = builder.recordedBy;
        this.recordedAt = builder.recordedAt;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long studentId;
        private Double ageMonths, weightKg, heightCm, muacCm, bmi;
        private String nutritionStatus;
        private Long recordedBy;
        private LocalDateTime recordedAt;

        public Builder studentId(Long v)          { this.studentId = v; return this; }
        public Builder ageMonths(Double v)         { this.ageMonths = v; return this; }
        public Builder weightKg(Double v)          { this.weightKg = v; return this; }
        public Builder heightCm(Double v)          { this.heightCm = v; return this; }
        public Builder muacCm(Double v)            { this.muacCm = v; return this; }
        public Builder bmi(double v)               { this.bmi = v; return this; }
        public Builder nutritionStatus(String v)   { this.nutritionStatus = v; return this; }
        public Builder recordedBy(Long v)          { this.recordedBy = v; return this; }
        public Builder recordedAt(LocalDateTime v) { this.recordedAt = v; return this; }
        public HealthRecord build()                { return new HealthRecord(this); }
    }

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
        if (bmi == null && weightKg != null && heightCm != null) {
            double hm = heightCm / 100.0;
            bmi = weightKg / (hm * hm);
        }
    }

    // Getters
    public Long getId()               { return id; }
    public Long getStudentId()        { return studentId; }
    public Double getAgeMonths()      { return ageMonths; }
    public Double getWeightKg()       { return weightKg; }
    public Double getHeightCm()       { return heightCm; }
    public Double getMuacCm()         { return muacCm; }
    public Double getBmi()            { return bmi; }
    public String getNutritionStatus(){ return nutritionStatus; }
    public Long getRecordedBy()       { return recordedBy; }
    public LocalDateTime getRecordedAt() { return recordedAt; }

    // Setters
    public void setStudentId(Long v)        { this.studentId = v; }
    public void setAgeMonths(Double v)      { this.ageMonths = v; }
    public void setWeightKg(Double v)       { this.weightKg = v; }
    public void setHeightCm(Double v)       { this.heightCm = v; }
    public void setMuacCm(Double v)         { this.muacCm = v; }
    public void setBmi(Double v)            { this.bmi = v; }
    public void setNutritionStatus(String v){ this.nutritionStatus = v; }
    public void setRecordedBy(Long v)       { this.recordedBy = v; }
    public void setRecordedAt(LocalDateTime v) { this.recordedAt = v; }
}

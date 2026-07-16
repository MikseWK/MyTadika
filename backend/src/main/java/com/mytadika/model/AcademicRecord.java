package com.mytadika.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "academic_record")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcademicRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "academic_term", nullable = false, length = 50)
    private String academicTerm;

    @Column(name = "average_mark", nullable = false)
    private Double averageMark;

    @Column(name = "final_grade", nullable = false, length = 2)
    private String finalGrade;

    @Builder.Default
    @OneToMany(mappedBy = "academicRecord", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AcademicScoreItem> scoreItems = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

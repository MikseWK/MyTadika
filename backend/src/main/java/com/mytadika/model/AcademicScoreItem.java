package com.mytadika.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "academic_score_item")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcademicScoreItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_record_id", nullable = false)
    private AcademicRecord academicRecord;

    @Column(name = "subject_name", nullable = false, length = 50)
    private String subjectName;

    @Column(nullable = false)
    private Double score;
}

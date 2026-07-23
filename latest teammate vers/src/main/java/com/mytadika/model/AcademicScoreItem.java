package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "academic_score_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcademicScoreItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "academic_record_id", nullable = false) private Long academicRecordId;
    @Column(name = "subject_name", nullable = false) private String subjectName;
    @Column(nullable = false) private Double score;
}

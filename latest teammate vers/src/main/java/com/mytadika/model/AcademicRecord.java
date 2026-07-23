package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "academic_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcademicRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(name = "academic_term", nullable = false) private String academicTerm;
    @Column(name = "average_mark", nullable = false) private Double averageMark;
    @Column(name = "final_grade", length = 2, nullable = false) private String finalGrade;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "classwork_completions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"assignment_id", "student_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClassworkCompletion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "assignment_id", nullable = false) private Long assignmentId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(name = "marked_by") private String markedBy;
    @Column(name = "marked_at") private LocalDateTime markedAt;
    @PrePersist protected void onMark() { if (markedAt == null) markedAt = LocalDateTime.now(); }
}

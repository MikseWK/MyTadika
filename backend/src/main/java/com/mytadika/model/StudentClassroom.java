package com.mytadika.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_classrooms",
    uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "classroom_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentClassroom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(name = "classroom_id", nullable = false) private Long classroomId;
    @Column(name = "joined_at") private LocalDateTime joinedAt;
    @PrePersist protected void onJoin() { if (joinedAt == null) joinedAt = LocalDateTime.now(); }
}

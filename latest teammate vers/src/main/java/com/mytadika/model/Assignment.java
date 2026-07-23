package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "assignments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Assignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "classroom_id", nullable = false) private Long classroomId;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "due_date", length = 10) private String dueDate;
    private Integer points;
    @Column(length = 100) private String topic;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

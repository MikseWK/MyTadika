package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "classrooms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Classroom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column private String section;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "teacher_account_id", nullable = false) private String teacherAccountId;
    @Column(length = 30) @Builder.Default private String color = "indigo";
    @Column(name = "class_code", length = 20, unique = true) private String classCode;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

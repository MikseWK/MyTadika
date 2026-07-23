package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "class_members", uniqueConstraints = @UniqueConstraint(columnNames = {"classroom_id","account_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClassMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "classroom_id", nullable = false) private Long classroomId;
    @Column(name = "account_id", nullable = false) private String accountId;
    @Column(nullable = false, length = 20) @Builder.Default private String role = "student";
    @Column(name = "joined_at") private LocalDateTime joinedAt;
    @PrePersist protected void onJoin() { if (joinedAt == null) joinedAt = LocalDateTime.now(); }
}

package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "memory_comments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MemoryComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "memory_post_id", nullable = false) private Long memoryPostId;
    @Column(name = "author_account_id", nullable = false) private String authorAccountId;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "memory_posts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MemoryPost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "classroom_id", nullable = false) private Long classroomId;
    @Column(name = "author_account_id", nullable = false) private String authorAccountId;
    @Column(columnDefinition = "TEXT") private String caption;
    @Column(name = "cover_media_id") private Long coverMediaId;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

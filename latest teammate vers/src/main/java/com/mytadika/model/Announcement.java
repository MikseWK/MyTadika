package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "announcements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Announcement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "classroom_id", nullable = false) private Long classroomId;
    @Column(name = "author_account_id", nullable = false) private String authorAccountId;
    @Column(length = 150) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(length = 100) private String topic;
    @Column(nullable = false) @Builder.Default private Boolean pinned = false;
    @Column(name = "image_url", length = 500) private String imageUrl;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

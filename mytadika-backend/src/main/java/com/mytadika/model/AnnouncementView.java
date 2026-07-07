package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "announcement_views",
    uniqueConstraints = @UniqueConstraint(columnNames = {"announcement_id", "viewer_account_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnnouncementView {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "announcement_id", nullable = false) private Long announcementId;
    @Column(name = "viewer_account_id", nullable = false) private String viewerAccountId;
    @Column(name = "viewed_at") private LocalDateTime viewedAt;
    @PrePersist protected void onView() { if (viewedAt == null) viewedAt = LocalDateTime.now(); }
}

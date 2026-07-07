package com.mytadika.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "school_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SchoolEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "event_date", nullable = false, length = 10)
    private String eventDate;

    @Column(name = "event_type", nullable = false, length = 50)
    @Builder.Default
    private String eventType = "general";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by_account_id")
    private String createdByAccountId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

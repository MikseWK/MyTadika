package com.mytadika.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_contacts", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"parent_account_id", "teacher_account_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ChatContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_account_id", nullable = false, length = 28)
    private String parentAccountId;

    @Column(name = "teacher_account_id", nullable = false, length = 28)
    private String teacherAccountId;

    @Column
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }
}

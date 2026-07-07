package com.mytadika.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "teacher_contacts", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"teacher_account_id_a", "teacher_account_id_b"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TeacherContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_account_id_a", nullable = false, length = 28)
    private String teacherAccountIdA;

    @Column(name = "teacher_account_id_b", nullable = false, length = 28)
    private String teacherAccountIdB;

    @Column
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }
}

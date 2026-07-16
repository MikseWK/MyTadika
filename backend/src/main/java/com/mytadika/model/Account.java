package com.mytadika.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @Column(length = 28)
    private String accountId;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleType roleType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 500)
    private String address;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String qualification;

    @Column(length = 100)
    private String experience;

    @Column(length = 100)
    private String focusArea;

    @Column
    private LocalDateTime lastActiveAt;

    public enum RoleType {
        TEACHER,
        PARENT,
        ADMIN
    }
}
package com.mytadika.dto;

import com.mytadika.model.Account;
import com.mytadika.model.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public class AccountResponseDTO {

    private Long id;
    private UUID authUserId;
    private String fullName;
    private String email;
    private Role role;
    private String phoneNumber;
    private LocalDateTime createdAt;

    public static AccountResponseDTO from(Account account) {
        AccountResponseDTO dto = new AccountResponseDTO();
        dto.id = account.getId();
        dto.authUserId = account.getAuthUserId();
        dto.fullName = account.getFullName();
        dto.email = account.getEmail();
        dto.role = account.getRole();
        dto.phoneNumber = account.getPhoneNumber();
        dto.createdAt = account.getCreatedAt();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public UUID getAuthUserId() {
        return authUserId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

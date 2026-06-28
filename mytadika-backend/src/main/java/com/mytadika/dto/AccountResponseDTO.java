package com.mytadika.dto;

import com.mytadika.model.Account;
import com.mytadika.model.Role;

import java.time.LocalDateTime;

public class AccountResponseDTO {

    private String accountId;
    private String fullName;
    private String email;
    private Role role;
    private String phoneNumber;
    private String address;
    private String profileImageUrl;
    private LocalDateTime createdAt;

    public static AccountResponseDTO from(Account account) {
        AccountResponseDTO dto = new AccountResponseDTO();
        dto.accountId = account.getAccountId();
        dto.fullName = account.getFullName();
        dto.email = account.getEmail();
        dto.role = account.getRole();
        dto.phoneNumber = account.getPhoneNumber();
        dto.address = account.getAddress();
        dto.profileImageUrl = account.getProfileImageUrl();
        dto.createdAt = account.getCreatedAt();
        return dto;
    }

    public String getAccountId() { return accountId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getAddress() { return address; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

package com.mytadika.dto;

import com.mytadika.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Admin-only payload to provision the local Account row for a teacher/admin
 * whose Supabase auth.users record already exists (created via Supabase invite
 * or normal sign-up). authUserId/email must match that existing Supabase user.
 */
public class RegisterStaffRequestDTO {

    @NotNull(message = "authUserId is required")
    private UUID authUserId;

    @NotBlank(message = "Please complete all mandatory fields before saving.")
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank(message = "Please complete all mandatory fields before saving.")
    @Size(max = 100)
    private String fullName;

    @NotNull(message = "role is required")
    private Role role;

    @Size(max = 20)
    private String phoneNumber;

    public RegisterStaffRequestDTO() {}

    public UUID getAuthUserId() {
        return authUserId;
    }

    public void setAuthUserId(UUID authUserId) {
        this.authUserId = authUserId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}

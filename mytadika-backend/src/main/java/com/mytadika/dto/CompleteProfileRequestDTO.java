package com.mytadika.dto;

import com.mytadika.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * First-login self-registration payload. 'role' is optional and, if present,
 * must be PARENT — staff accounts (TEACHER/ADMIN) are provisioned by an admin
 * via /api/accounts/register-staff, not self-service, to avoid a client being
 * able to grant itself elevated privileges.
 */
public class CompleteProfileRequestDTO {

    @NotBlank(message = "Please complete all mandatory fields before saving.")
    @Size(max = 100)
    private String fullName;

    @Size(max = 20)
    private String phoneNumber;

    private Role role;

    public CompleteProfileRequestDTO() {}

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}

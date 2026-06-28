package com.mytadika.dto;

import com.mytadika.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RegisterStaffRequestDTO {

    @NotBlank(message = "Please complete all mandatory fields before saving.")
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank(message = "Please complete all mandatory fields before saving.")
    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Please complete all mandatory fields before saving.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    private String password;

    @NotNull(message = "role is required")
    private Role role;

    @Size(max = 20)
    private String phoneNumber;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}

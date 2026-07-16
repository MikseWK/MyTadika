package com.mytadika.dto;

public class LoginResponseDTO {

    private String token;
    private String accountId;
    private String role;
    private String fullName;
    private String email;

    public LoginResponseDTO() {}

    public LoginResponseDTO(String token, String accountId, String role, String fullName, String email) {
        this.token = token;
        this.accountId = accountId;
        this.role = role;
        this.fullName = fullName;
        this.email = email;
    }

    public String getToken() { return token; }
    public String getAccountId() { return accountId; }
    public String getRole() { return role; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
}

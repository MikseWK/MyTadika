package com.mytadika.dto;

import lombok.Builder;
import lombok.Data;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AuthResponse {
    private String token;
    private String accountId;
    private String roleType;
    private String fullName;
    private String email;
}
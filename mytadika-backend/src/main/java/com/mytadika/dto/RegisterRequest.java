package com.mytadika.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class RegisterRequest {
    private String fullName;
    private String email;
    private String password;
}

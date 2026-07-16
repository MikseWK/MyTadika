package com.mytadika.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class ResetPasswordRequest {
    private String token;
    private String newPassword;
}

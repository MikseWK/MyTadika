package com.mytadika.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class ForgotPasswordRequest {
    private String email;
}

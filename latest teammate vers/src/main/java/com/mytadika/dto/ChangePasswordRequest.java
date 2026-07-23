package com.mytadika.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class ChangePasswordRequest {
    private String accountId;
    private String currentPassword;
    private String newPassword;
}

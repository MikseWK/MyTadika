package com.mytadika.dto;

import lombok.Data;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AuthRequest {
    private String email;
    private String password;
}

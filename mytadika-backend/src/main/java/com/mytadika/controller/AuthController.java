package com.mytadika.controller;

import com.mytadika.dto.AccountResponseDTO;
import com.mytadika.security.AccountResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountResolver accountResolver;

    public AuthController(AccountResolver accountResolver) {
        this.accountResolver = accountResolver;
    }

    @GetMapping("/me")
    public ResponseEntity<AccountResponseDTO> me() {
        return ResponseEntity.ok(AccountResponseDTO.from(accountResolver.requireCurrentAccount()));
    }
}

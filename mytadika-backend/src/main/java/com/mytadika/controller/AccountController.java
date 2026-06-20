package com.mytadika.controller;

import com.mytadika.dto.AccountResponseDTO;
import com.mytadika.dto.CompleteProfileRequestDTO;
import com.mytadika.dto.RegisterStaffRequestDTO;
import com.mytadika.model.Account;
import com.mytadika.security.AccountResolver;
import com.mytadika.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccountResolver accountResolver;

    public AccountController(AccountService accountService, AccountResolver accountResolver) {
        this.accountService = accountService;
        this.accountResolver = accountResolver;
    }

    @PostMapping("/complete-profile")
    public ResponseEntity<AccountResponseDTO> completeProfile(@Valid @RequestBody CompleteProfileRequestDTO request) {
        Account account = accountService.completeProfile(
                accountResolver.currentAuthUserId(), accountResolver.currentEmail(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponseDTO.from(account));
    }

    @PostMapping("/register-staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponseDTO> registerStaff(@Valid @RequestBody RegisterStaffRequestDTO request) {
        Account account = accountService.registerStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponseDTO.from(account));
    }
}

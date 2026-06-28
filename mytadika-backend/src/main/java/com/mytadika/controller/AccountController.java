package com.mytadika.controller;

import com.mytadika.dto.AccountResponseDTO;
import com.mytadika.dto.RegisterStaffRequestDTO;
import com.mytadika.dto.UpdateProfileRequestDTO;
import com.mytadika.model.Account;
import com.mytadika.service.AccountService;
import com.mytadika.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;

    public AccountController(AccountService accountService, AuthService authService) {
        this.accountService = accountService;
        this.authService = authService;
    }

    @PostMapping("/register-staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponseDTO> registerStaff(@Valid @RequestBody RegisterStaffRequestDTO request) {
        Account account = authService.registerStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponseDTO.from(account));
    }

    @PutMapping("/me")
    public ResponseEntity<AccountResponseDTO> updateMyProfile(
            @AuthenticationPrincipal Account currentUser,
            @Valid @RequestBody UpdateProfileRequestDTO request) {
        Account account = accountService.updateProfile(currentUser, request);
        return ResponseEntity.ok(AccountResponseDTO.from(account));
    }

    @PostMapping("/me/profile-image")
    public ResponseEntity<AccountResponseDTO> uploadMyProfileImage(
            @AuthenticationPrincipal Account currentUser,
            @RequestParam("file") MultipartFile file) {
        Account account = accountService.updateProfileImage(currentUser, file);
        return ResponseEntity.ok(AccountResponseDTO.from(account));
    }
}

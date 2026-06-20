package com.mytadika.service;

import com.mytadika.dto.AuthRequest;
import com.mytadika.dto.AuthResponse;
import com.mytadika.dto.ForgotPasswordRequest;
import com.mytadika.dto.RegisterRequest;
import com.mytadika.dto.ResetPasswordRequest;
import com.mytadika.model.Account;
import com.mytadika.model.PasswordResetToken;
import com.mytadika.repository.AccountRepository;
import com.mytadika.repository.PasswordResetTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AccountRepository accountRepository,
                       PasswordResetTokenRepository resetTokenRepository,
                       EmailService emailService) {
        this.accountRepository = accountRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.emailService = emailService;
    }

    public AuthResponse login(AuthRequest request) {
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        String fakeToken = "TOKEN_" + account.getAccountId();

        return AuthResponse.builder()
                .token(fakeToken)
                .accountId(account.getAccountId())
                .roleType(account.getRoleType().name())
                .fullName(account.getFullName())
                .email(account.getEmail())
                .build();
    }

    public void register(RegisterRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("An account with this email already exists");
        }

        String accountId = UUID.randomUUID().toString().replace("-", "").substring(0, 28);

        Account account = Account.builder()
                .accountId(accountId)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roleType(Account.RoleType.PARENT)
                .createdAt(LocalDateTime.now())
                .build();

        accountRepository.save(account);
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        // Only send email if account exists — silently succeed otherwise
        accountRepository.findByEmail(request.getEmail()).ifPresent(account -> {
            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .email(account.getEmail())
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();

            resetTokenRepository.save(resetToken);
            emailService.sendPasswordResetEmail(account.getEmail(), token);
        });
    }

    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = resetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset link"));

        if (resetToken.isUsed()) {
            throw new RuntimeException("This reset link has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("This reset link has expired. Please request a new one");
        }

        Account account = accountRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        accountRepository.save(account);

        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);
    }
}

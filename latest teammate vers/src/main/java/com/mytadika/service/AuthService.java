package com.mytadika.service;

import com.mytadika.dto.AuthRequest;
import com.mytadika.dto.AuthResponse;
import com.mytadika.dto.ChangePasswordRequest;
import com.mytadika.dto.ForgotPasswordRequest;
import com.mytadika.dto.RegisterRequest;
import com.mytadika.dto.ResetPasswordRequest;
import com.mytadika.model.Account;
import com.mytadika.model.PasswordResetToken;
import com.mytadika.repository.AccountRepository;
import com.mytadika.repository.PasswordResetTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AccountRepository accountRepository,
                       PasswordResetTokenRepository resetTokenRepository,
                       EmailService emailService,
                       NotificationService notificationService) {
        this.accountRepository = accountRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    public AuthResponse login(AuthRequest request) {
        Account account = accountRepository.findByEmail(request.getEmail().trim().toLowerCase())
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
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (accountRepository.existsByEmail(normalizedEmail)) {
            throw new RuntimeException("An account with this email already exists");
        }

        String accountId = UUID.randomUUID().toString().replace("-", "").substring(0, 28);

        Account account = Account.builder()
                .accountId(accountId)
                .fullName(request.getFullName())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .roleType(Account.RoleType.PARENT)
                .createdAt(LocalDateTime.now())
                .build();

        accountRepository.save(account);
        notifyAdminsOfNewRegistration(account);
    }

    private void notifyAdminsOfNewRegistration(Account account) {
        List<Account> admins = accountRepository.findByRoleType(Account.RoleType.ADMIN);
        String title = "New parent registered: " + account.getFullName();
        String body = account.getEmail() + " just created a parent account.";
        for (Account admin : admins)
            notificationService.create(admin.getAccountId(), title, body, "/admin/adminaccounts.html?role=PARENT");
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        // Only send email if account exists — silently succeed otherwise
        accountRepository.findByEmail(request.getEmail().trim().toLowerCase()).ifPresent(account -> {
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

    @Transactional
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

    public void changePassword(ChangePasswordRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), account.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new RuntimeException("New password must be at least 6 characters");
        }

        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        accountRepository.save(account);
    }
}

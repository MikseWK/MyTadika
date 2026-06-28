package com.mytadika.service;

import com.mytadika.dto.*;
import com.mytadika.exception.ConflictException;
import com.mytadika.exception.InvalidInputException;
import com.mytadika.exception.ResourceNotFoundException;
import com.mytadika.model.Account;
import com.mytadika.model.PasswordResetToken;
import com.mytadika.model.Role;
import com.mytadika.repository.AccountRepository;
import com.mytadika.repository.PasswordResetTokenRepository;
import com.mytadika.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AccountRepository accountRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AccountRepository accountRepository,
                       PasswordResetTokenRepository resetTokenRepository,
                       EmailService emailService,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponseDTO login(LoginRequestDTO request) {
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidInputException("Incorrect email or password. Please try again."));

        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new InvalidInputException("Incorrect email or password. Please try again.");
        }

        String token = jwtService.generateToken(account);
        return new LoginResponseDTO(
                token,
                account.getAccountId(),
                account.getRole().name(),
                account.getFullName(),
                account.getEmail());
    }

    public void register(RegisterRequestDTO request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("An account with this email already exists.");
        }

        String accountId = UUID.randomUUID().toString().replace("-", "").substring(0, 28);

        Account account = Account.builder()
                .accountId(accountId)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.PARENT)
                .build();

        accountRepository.save(account);
    }

    /** Admin-only: provision a staff account with a specified role. */
    public Account registerStaff(RegisterStaffRequestDTO request) {
        if (request.getRole() == Role.PARENT) {
            throw new InvalidInputException(
                    "Parent accounts are self-registered, not provisioned through this endpoint.");
        }
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("An account with this email already exists.");
        }

        String accountId = UUID.randomUUID().toString().replace("-", "").substring(0, 28);

        Account account = Account.builder()
                .accountId(accountId)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .phoneNumber(request.getPhoneNumber())
                .build();

        return accountRepository.save(account);
    }

    public void forgotPassword(ForgotPasswordRequestDTO request) {
        // Silently succeed if account not found — prevents email enumeration
        accountRepository.findByEmail(request.getEmail()).ifPresent(account -> {
            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .email(account.getEmail())
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();

            resetTokenRepository.save(resetToken);
            try {
                emailService.sendPasswordResetEmail(account.getEmail(), token);
            } catch (Exception e) {
                // SMTP not configured or failed — token is already saved.
                // Log for the developer; the API still returns 200 to the caller.
                log.warn("Password reset email could not be sent to {}: {}", account.getEmail(), e.getMessage());
            }
        });
    }

    public void resetPassword(ResetPasswordRequestDTO request) {
        PasswordResetToken resetToken = resetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new InvalidInputException("Invalid or expired reset link."));

        if (resetToken.isUsed()) {
            throw new InvalidInputException("This reset link has already been used.");
        }
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidInputException("This reset link has expired. Please request a new one.");
        }

        Account account = accountRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));

        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        accountRepository.save(account);

        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);
    }
}

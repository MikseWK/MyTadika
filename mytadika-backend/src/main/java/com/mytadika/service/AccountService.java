package com.mytadika.service;

import com.mytadika.dto.CompleteProfileRequestDTO;
import com.mytadika.dto.RegisterStaffRequestDTO;
import com.mytadika.exception.ConflictException;
import com.mytadika.exception.InvalidInputException;
import com.mytadika.model.Account;
import com.mytadika.model.Role;
import com.mytadika.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * First-login profile creation. Idempotent: if the Account already exists for
     * this authUserId, the existing record is returned as-is rather than overwritten,
     * so a duplicate call can't be used to silently change an established identity.
     */
    public Account completeProfile(UUID authUserId, String email, CompleteProfileRequestDTO request) {
        return accountRepository.findByAuthUserId(authUserId).orElseGet(() -> {
            if (request.getRole() != null && request.getRole() != Role.PARENT) {
                throw new InvalidInputException(
                        "Only parent accounts can self-register. Contact your administrator for staff accounts.");
            }
            if (accountRepository.findByEmail(email).isPresent()) {
                throw new ConflictException("An account with this email already exists.");
            }

            Account account = Account.builder()
                    .authUserId(authUserId)
                    .email(email)
                    .fullName(request.getFullName())
                    .role(Role.PARENT)
                    .phoneNumber(request.getPhoneNumber())
                    .build();
            return accountRepository.save(account);
        });
    }

    /** Admin-only provisioning for a teacher/admin whose Supabase auth.users record already exists. */
    public Account registerStaff(RegisterStaffRequestDTO request) {
        if (request.getRole() == Role.PARENT) {
            throw new InvalidInputException(
                    "Parent accounts are self-registered, not provisioned through this endpoint.");
        }
        if (accountRepository.findByAuthUserId(request.getAuthUserId()).isPresent()) {
            throw new ConflictException("An account already exists for this Supabase user.");
        }
        if (accountRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("An account with this email already exists.");
        }

        Account account = Account.builder()
                .authUserId(request.getAuthUserId())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .role(request.getRole())
                .phoneNumber(request.getPhoneNumber())
                .build();
        return accountRepository.save(account);
    }
}

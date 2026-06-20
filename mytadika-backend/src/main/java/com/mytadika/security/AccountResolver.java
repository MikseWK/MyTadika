package com.mytadika.security;

import com.mytadika.exception.ResourceNotFoundException;
import com.mytadika.model.Account;
import com.mytadika.repository.AccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Resolves the currently authenticated Supabase identity / local Account from the SecurityContext. */
@Component
public class AccountResolver {

    private final AccountRepository accountRepository;

    public AccountResolver(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResourceNotFoundException("No authenticated Supabase session found.");
        }
        return jwt;
    }

    public UUID currentAuthUserId() {
        return UUID.fromString(currentJwt().getSubject());
    }

    public String currentEmail() {
        return currentJwt().getClaimAsString("email");
    }

    public Account requireCurrentAccount() {
        return accountRepository.findByAuthUserId(currentAuthUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No profile found for this account. Please complete your profile first."));
    }
}

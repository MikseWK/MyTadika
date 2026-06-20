package com.mytadika.security;

import com.mytadika.repository.AccountRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Bridges a validated Supabase JWT to local app authorities. The JWT only proves
 * *who* the caller is (the Supabase auth.users id in the 'sub' claim); this looks
 * up the matching local Account to find out *what role* they have.
 *
 * A token with no matching Account (first login, before complete-profile) is still
 * a valid authentication with zero authorities, rather than a rejection — that's
 * what lets the complete-profile endpoint accept it.
 */
@Component
public class SupabaseJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AccountRepository accountRepository;

    public SupabaseJwtAuthConverter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, resolveAuthorities(jwt));
    }

    private Collection<GrantedAuthority> resolveAuthorities(Jwt jwt) {
        UUID authUserId;
        try {
            authUserId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            return Collections.emptyList();
        }

        return accountRepository.findByAuthUserId(authUserId)
                .<Collection<GrantedAuthority>>map(account ->
                        List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())))
                .orElse(Collections.emptyList());
    }
}

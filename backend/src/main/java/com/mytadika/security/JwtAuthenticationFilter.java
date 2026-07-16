package com.mytadika.security;

import com.mytadika.repository.AccountRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final AccountRepository accountRepository;

    public JwtAuthenticationFilter(JwtService jwtService, AccountRepository accountRepository) {
        this.jwtService = jwtService;
        this.accountRepository = accountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String token = header.substring(7);
            Claims claims = jwtService.parseToken(token);
            String accountId = claims.getSubject();
            log.debug("[JWT] Token parsed OK — subject (accountId): {}", accountId);

            boolean found = accountRepository.findById(accountId).map(account -> {
                var auth = new UsernamePasswordAuthenticationToken(
                        account, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("[JWT] Authenticated as {} with role {}", accountId, account.getRole());
                return true;
            }).orElse(false);

            if (!found) {
                log.warn("[JWT] Account not found in DB for accountId: {}", accountId);
            }
        } catch (Exception e) {
            log.warn("[JWT] Token rejected — {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
        chain.doFilter(request, response);
    }
}

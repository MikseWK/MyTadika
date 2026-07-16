package com.mytadika.service;

import com.mytadika.model.Account;
import com.mytadika.repository.AccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public CustomUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        Account acc = accountRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new User(
                acc.getEmail(),
                acc.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + acc.getRoleType().name())));
    }
}
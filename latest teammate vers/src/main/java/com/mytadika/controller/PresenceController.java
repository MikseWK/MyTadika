package com.mytadika.controller;

import com.mytadika.model.Account;
import com.mytadika.repository.AccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final AccountRepository accountRepository;

    public PresenceController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @PostMapping("/heartbeat/{accountId}")
    public ResponseEntity<?> heartbeat(@PathVariable String accountId) {
        Optional<Account> opt = accountRepository.findById(accountId);
        opt.ifPresent(a -> {
            a.setLastActiveAt(LocalDateTime.now());
            accountRepository.save(a);
        });
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}

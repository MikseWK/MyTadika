package com.mytadika.repository;

import com.mytadika.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAuthUserId(UUID authUserId);

    Optional<Account> findByEmail(String email);
}

package com.mytadika.repository;

import com.mytadika.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Account> findByRoleType(Account.RoleType roleType);

    Optional<Account> findFirstByRoleTypeOrderByCreatedAtAsc(Account.RoleType roleType);
}
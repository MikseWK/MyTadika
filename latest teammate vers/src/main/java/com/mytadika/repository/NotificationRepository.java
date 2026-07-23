package com.mytadika.repository;

import com.mytadika.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByAccountIdOrderByCreatedAtDesc(String accountId);

    long countByAccountIdAndRead(String accountId, boolean read);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.read = true WHERE n.accountId = :accountId AND n.read = false")
    void markAllReadByAccountId(@Param("accountId") String accountId);

    @Modifying
    @Transactional
    void deleteByAccountId(String accountId);
}

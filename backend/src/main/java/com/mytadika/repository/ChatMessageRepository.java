package com.mytadika.repository;

import com.mytadika.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m WHERE " +
           "((m.senderId = :a AND m.receiverId = :b) OR (m.senderId = :b AND m.receiverId = :a)) " +
           "AND m.deleted = false ORDER BY m.sentAt ASC")
    List<ChatMessage> findConversation(@Param("a") String a, @Param("b") String b);

    // Contact-list previews only need the newest message, not the whole thread —
    // this avoids pulling the entire (potentially long) history just to read the last row.
    @Query("SELECT m FROM ChatMessage m WHERE " +
           "((m.senderId = :a AND m.receiverId = :b) OR (m.senderId = :b AND m.receiverId = :a)) " +
           "AND m.deleted = false ORDER BY m.sentAt DESC")
    List<ChatMessage> findLatestInConversation(@Param("a") String a, @Param("b") String b, Pageable pageable);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE " +
           "m.receiverId = :receiverId AND m.senderId = :senderId AND m.read = false AND m.deleted = false")
    long countUnread(@Param("receiverId") String receiverId, @Param("senderId") String senderId);

    // Batched versions of the two queries above — used to build a contact list without
    // firing one findLatestInConversation/countUnread pair per contact.
    @Query("SELECT m FROM ChatMessage m WHERE m.deleted = false AND (" +
           "(m.senderId = :me AND m.receiverId IN :others) OR (m.receiverId = :me AND m.senderId IN :others)) " +
           "ORDER BY m.sentAt DESC")
    List<ChatMessage> findLatestPerContact(@Param("me") String me, @Param("others") List<String> others);

    @Query("SELECT m.senderId, COUNT(m) FROM ChatMessage m WHERE " +
           "m.receiverId = :receiverId AND m.senderId IN :senderIds AND m.read = false AND m.deleted = false " +
           "GROUP BY m.senderId")
    List<Object[]> countUnreadGrouped(@Param("receiverId") String receiverId, @Param("senderIds") List<String> senderIds);

    @Modifying
    @Transactional
    @Query("UPDATE ChatMessage m SET m.read = true WHERE " +
           "m.receiverId = :receiverId AND m.senderId = :senderId AND m.read = false")
    void markAsRead(@Param("receiverId") String receiverId, @Param("senderId") String senderId);

    @Query("SELECT DISTINCT CASE WHEN m.senderId = :inboxId THEN m.receiverId ELSE m.senderId END " +
           "FROM ChatMessage m WHERE (m.senderId = :inboxId OR m.receiverId = :inboxId) AND m.deleted = false")
    List<String> findDistinctContactsOf(@Param("inboxId") String inboxId);
}

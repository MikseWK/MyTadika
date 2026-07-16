package com.mytadika.service;

import com.mytadika.model.Notification;
import com.mytadika.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class NotificationService {

    private final NotificationRepository notifRepo;

    public NotificationService(NotificationRepository notifRepo) {
        this.notifRepo = notifRepo;
    }

    public void create(String accountId, String title, String body, String link) {
        notifRepo.save(Notification.builder()
                .accountId(accountId)
                .title(title)
                .body(body)
                .link(link)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public long countUnread(String accountId) {
        return notifRepo.countByAccountIdAndRead(accountId, false);
    }

    public List<Map<String, Object>> getNotifications(String accountId) {
        List<Notification> list = notifRepo.findByAccountIdOrderByCreatedAtDesc(accountId);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification n : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", n.getId());
            map.put("title", n.getTitle());
            map.put("body", n.getBody());
            map.put("link", n.getLink());
            map.put("isRead", n.isRead());
            map.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().format(fmt) : "");
            result.add(map);
        }
        return result;
    }

    @Transactional
    public void markRead(Long id) {
        notifRepo.findById(id).ifPresent(n -> {
            n.setRead(true);
            notifRepo.save(n);
        });
    }

    @Transactional
    public void markAllRead(String accountId) {
        notifRepo.markAllReadByAccountId(accountId);
    }

    public void delete(Long id) {
        notifRepo.deleteById(id);
    }

    public void deleteAll(String accountId) {
        notifRepo.deleteByAccountId(accountId);
    }
}

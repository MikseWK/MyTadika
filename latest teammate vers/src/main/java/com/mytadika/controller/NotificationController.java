package com.mytadika.controller;

import com.mytadika.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifService;

    public NotificationController(NotificationService notifService) {
        this.notifService = notifService;
    }

    @GetMapping("/my/{accountId}")
    public ResponseEntity<?> getNotifications(@PathVariable String accountId) {
        return ResponseEntity.ok(notifService.getNotifications(accountId));
    }

    @GetMapping("/my/{accountId}/unread-count")
    public ResponseEntity<?> getUnreadCount(@PathVariable String accountId) {
        return ResponseEntity.ok(Map.of("count", notifService.countUnread(accountId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        notifService.markRead(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/read-all/{accountId}")
    public ResponseEntity<?> markAllRead(@PathVariable String accountId) {
        notifService.markAllRead(accountId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        notifService.delete(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @DeleteMapping("/all/{accountId}")
    public ResponseEntity<?> deleteAll(@PathVariable String accountId) {
        notifService.deleteAll(accountId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}

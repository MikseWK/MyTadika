package com.mytadika.controller;

import com.mytadika.dto.ChatMessageRequest;
import com.mytadika.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/teachers")
    public ResponseEntity<?> getTeachers() {
        return ResponseEntity.ok(chatService.getTeachers());
    }

    @GetMapping("/parents")
    public ResponseEntity<?> getParents() {
        return ResponseEntity.ok(chatService.getParents());
    }

    @GetMapping("/contacts/{accountId}")
    public ResponseEntity<?> getContacts(@PathVariable String accountId) {
        try {
            return ResponseEntity.ok(chatService.getContacts(accountId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/contacts")
    public ResponseEntity<?> addContact(@RequestBody Map<String, String> body) {
        chatService.addContact(body.get("parentId"), body.get("teacherId"));
        return ResponseEntity.ok(Map.of("status", "added"));
    }

    @PostMapping("/teacher-contacts")
    public ResponseEntity<?> addTeacherContact(@RequestBody Map<String, String> body) {
        chatService.addTeacherContact(body.get("teacherIdA"), body.get("teacherIdB"));
        return ResponseEntity.ok(Map.of("status", "added"));
    }

    @PutMapping("/read/{viewerId}/{senderId}")
    public ResponseEntity<?> markAsRead(@PathVariable String viewerId, @PathVariable String senderId) {
        chatService.markAsRead(viewerId, senderId);
        return ResponseEntity.ok(Map.of("status", "read"));
    }

    @GetMapping("/messages/{accountId}/{contactId}")
    public ResponseEntity<?> getMessages(@PathVariable String accountId,
            @PathVariable String contactId) {
        return ResponseEntity.ok(chatService.getMessages(accountId, contactId));
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody ChatMessageRequest request) {
        try {
            chatService.sendMessage(request);
            return ResponseEntity.ok(Map.of("status", "sent"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/messages/{id}/edit")
    public ResponseEntity<?> editMessage(@PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            chatService.editMessage(id, body.get("senderId"), body.get("content"));
            return ResponseEntity.ok(Map.of("status", "updated"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/messages/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long id,
            @RequestParam String senderId) {
        try {
            chatService.deleteMessage(id, senderId);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/admin-inbox/identity")
    public ResponseEntity<?> getAdminInboxIdentity() {
        try {
            return ResponseEntity.ok(chatService.getAdminInboxIdentity());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/admin-inbox/contacts")
    public ResponseEntity<?> getAdminInboxContacts() {
        try {
            return ResponseEntity.ok(chatService.getAdminInboxContacts());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String url = chatService.uploadImage(file);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

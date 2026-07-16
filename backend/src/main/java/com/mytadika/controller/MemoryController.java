package com.mytadika.controller;

import com.mytadika.service.MemoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;
    public MemoryController(MemoryService memoryService) { this.memoryService = memoryService; }

    @PostMapping
    public ResponseEntity<?> createPost(@RequestParam("classroomId") Long classroomId,
                                         @RequestParam("authorAccountId") String authorAccountId,
                                         @RequestParam(value = "caption", required = false) String caption,
                                         @RequestParam("files") List<MultipartFile> files,
                                         @RequestParam(value = "coverIndex", required = false) Integer coverIndex) {
        try {
            return ResponseEntity.ok(memoryService.createPost(classroomId, authorAccountId, caption, files, coverIndex));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload photos: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> editPost(@PathVariable Long id,
                                       @RequestParam(value = "caption", required = false) String caption,
                                       @RequestParam(value = "removeImageIds", required = false) List<Long> removeImageIds,
                                       @RequestParam(value = "files", required = false) List<MultipartFile> files,
                                       @RequestParam(value = "coverMediaId", required = false) Long coverMediaId) {
        try {
            return ResponseEntity.ok(memoryService.editPost(id, caption, removeImageIds, files, coverMediaId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update post: " + e.getMessage()));
        }
    }

    @GetMapping("/classroom/{classroomId}")
    public ResponseEntity<?> getForClassroom(@PathVariable Long classroomId,
                                              @RequestParam(value = "viewerAccountId", required = false) String viewerAccountId) {
        return ResponseEntity.ok(memoryService.getForClassroom(classroomId, viewerAccountId));
    }

    @GetMapping("/teacher/{accountId}")
    public ResponseEntity<?> getForTeacher(@PathVariable String accountId) {
        return ResponseEntity.ok(memoryService.getForTeacher(accountId));
    }

    @GetMapping("/parent/{accountId}")
    public ResponseEntity<?> getForParent(@PathVariable String accountId) {
        return ResponseEntity.ok(memoryService.getForParent(accountId));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllForAdmin() {
        return ResponseEntity.ok(memoryService.getAllForAdmin());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        memoryService.deletePost(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/{id}/reactions/toggle")
    public ResponseEntity<?> toggleReaction(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(memoryService.toggleReaction(id, body.get("accountId")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<?> getComments(@PathVariable Long id) {
        return ResponseEntity.ok(memoryService.getComments(id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(memoryService.addComment(id, body.get("accountId"), body.get("content")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId) {
        memoryService.deleteComment(commentId);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}

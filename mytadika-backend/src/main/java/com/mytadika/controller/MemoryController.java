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
                                         @RequestParam("files") List<MultipartFile> files) {
        try {
            return ResponseEntity.ok(memoryService.createPost(classroomId, authorAccountId, caption, files));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload photos: " + e.getMessage()));
        }
    }

    @GetMapping("/classroom/{classroomId}")
    public ResponseEntity<?> getForClassroom(@PathVariable Long classroomId) {
        return ResponseEntity.ok(memoryService.getForClassroom(classroomId));
    }

    @GetMapping("/teacher/{accountId}")
    public ResponseEntity<?> getForTeacher(@PathVariable String accountId) {
        return ResponseEntity.ok(memoryService.getForTeacher(accountId));
    }

    @GetMapping("/parent/{accountId}")
    public ResponseEntity<?> getForParent(@PathVariable String accountId) {
        return ResponseEntity.ok(memoryService.getForParent(accountId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        memoryService.deletePost(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}

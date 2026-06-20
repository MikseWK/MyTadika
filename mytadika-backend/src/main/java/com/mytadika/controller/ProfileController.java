package com.mytadika.controller;

import com.mytadika.dto.UpdateProfileRequest;
import com.mytadika.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<?> getProfile(@PathVariable String accountId) {
        try {
            return ResponseEntity.ok(profileService.getProfile(accountId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<?> updateProfile(@PathVariable String accountId,
                                           @RequestBody UpdateProfileRequest request) {
        try {
            profileService.updateProfile(accountId, request);
            return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{accountId}/image")
    public ResponseEntity<?> uploadImage(@PathVariable String accountId,
                                         @RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = profileService.uploadProfileImage(accountId, file);
            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

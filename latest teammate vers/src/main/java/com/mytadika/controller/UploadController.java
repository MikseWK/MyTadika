package com.mytadika.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final long MAX_BYTES = 5 * 1024 * 1024; // 5 MB

    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "No file provided."));
        if (file.getSize() > MAX_BYTES) return ResponseEntity.badRequest().body(Map.of("error", "File too large (max 5 MB)."));
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed."));

        try {
            Path uploadDir = Paths.get(System.getProperty("user.dir"), "uploads");
            Files.createDirectories(uploadDir);
            String ext = "";
            String orig = file.getOriginalFilename();
            if (orig != null && orig.contains(".")) ext = orig.substring(orig.lastIndexOf('.'));
            String filename = UUID.randomUUID() + ext;
            Files.copy(file.getInputStream(), uploadDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok(Map.of("url", "/uploads/" + filename));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Upload failed."));
        }
    }
}

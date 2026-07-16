package com.mytadika.controller;

import com.mytadika.service.AcademicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/academic")
public class AcademicController {

    private final AcademicService academicService;

    public AcademicController(AcademicService academicService) {
        this.academicService = academicService;
    }

    // Fixed school-wide subject list — mirrors the same TOPICS taxonomy used for
    // classroom assignments/announcements (see teacherclassroom.html), trimmed to
    // the subjects that score naturally on a 0-100 scale ("General" and the more
    // values-based topics like Islamic/Moral Education are classroom-only tags).
    private static final List<String> SUBJECTS = List.of(
            "Bahasa Melayu", "English", "Math", "Science", "Creative Arts", "Physical Education");

    @GetMapping("/subjects")
    public ResponseEntity<?> getSubjects() {
        return ResponseEntity.ok(SUBJECTS);
    }

    @GetMapping("/students/{studentId}/records")
    public ResponseEntity<?> getRecords(@PathVariable Long studentId) {
        return ResponseEntity.ok(academicService.getRecordsByStudent(studentId));
    }

    @PostMapping("/students/{studentId}/records")
    public ResponseEntity<?> createRecord(@PathVariable Long studentId, @RequestBody Map<String, Object> body) {
        try {
            String term = (String) body.get("term");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subjects = (List<Map<String, Object>>) body.get("subjects");
            return ResponseEntity.ok(academicService.createRecord(studentId, term, subjects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<?> getRecord(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(academicService.getRecord(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/records/{id}")
    public ResponseEntity<?> updateRecord(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String term = (String) body.get("term");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subjects = (List<Map<String, Object>>) body.get("subjects");
            return ResponseEntity.ok(academicService.updateRecord(id, term, subjects));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/records/{id}")
    public ResponseEntity<?> deleteRecord(@PathVariable Long id) {
        try {
            academicService.deleteRecord(id);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

package com.mytadika.controller;

import com.mytadika.dto.AcademicRecordRequestDTO;
import com.mytadika.dto.AcademicRecordResponseDTO;
import com.mytadika.model.Account;
import com.mytadika.service.AcademicService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<List<String>> getSubjects() {
        return ResponseEntity.ok(SUBJECTS);
    }

    @GetMapping("/students/{studentId}/records")
    public ResponseEntity<List<AcademicRecordResponseDTO>> listRecords(
            @PathVariable Long studentId,
            @AuthenticationPrincipal Account currentUser) {
        return ResponseEntity.ok(academicService.listForStudent(studentId, currentUser));
    }

    @PostMapping("/students/{studentId}/records")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AcademicRecordResponseDTO> createRecord(
            @PathVariable Long studentId,
            @Valid @RequestBody AcademicRecordRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicService.createRecord(studentId, request));
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<AcademicRecordResponseDTO> getRecord(
            @PathVariable Long id,
            @AuthenticationPrincipal Account currentUser) {
        return ResponseEntity.ok(academicService.getRecordScoped(id, currentUser));
    }

    @PutMapping("/records/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AcademicRecordResponseDTO> updateRecord(
            @PathVariable Long id,
            @Valid @RequestBody AcademicRecordRequestDTO request) {
        return ResponseEntity.ok(academicService.updateRecord(id, request));
    }
}

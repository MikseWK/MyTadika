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

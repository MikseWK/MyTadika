package com.mytadika.controller;

import com.mytadika.dto.AcademicRecordRequestDTO;
import com.mytadika.dto.AcademicRecordResponseDTO;
import com.mytadika.security.AccountResolver;
import com.mytadika.service.AcademicService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/academic")
public class AcademicController {

    private final AcademicService academicService;
    private final AccountResolver accountResolver;

    public AcademicController(AcademicService academicService, AccountResolver accountResolver) {
        this.academicService = academicService;
        this.accountResolver = accountResolver;
    }

    @GetMapping("/students/{studentId}/records")
    public ResponseEntity<List<AcademicRecordResponseDTO>> listRecords(@PathVariable Long studentId) {
        return ResponseEntity.ok(
                academicService.listForStudent(studentId, accountResolver.requireCurrentAccount()));
    }

    @PostMapping("/students/{studentId}/records")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AcademicRecordResponseDTO> createRecord(
            @PathVariable Long studentId, @Valid @RequestBody AcademicRecordRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicService.createRecord(studentId, request));
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<AcademicRecordResponseDTO> getRecord(@PathVariable Long id) {
        return ResponseEntity.ok(academicService.getRecordScoped(id, accountResolver.requireCurrentAccount()));
    }

    @PutMapping("/records/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AcademicRecordResponseDTO> updateRecord(
            @PathVariable Long id, @Valid @RequestBody AcademicRecordRequestDTO request) {
        return ResponseEntity.ok(academicService.updateRecord(id, request));
    }
}

package com.mytadika.controller;

import com.mytadika.dto.StudentCreateRequestDTO;
import com.mytadika.dto.StudentResponseDTO;
import com.mytadika.dto.StudentUpdateRequestDTO;
import com.mytadika.model.Account;
import com.mytadika.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/api/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<List<StudentResponseDTO>> listStudents(
            @RequestParam(required = false) Long classroomId) {
        return ResponseEntity.ok(studentService.listAll(classroomId));
    }

    @GetMapping("/api/students/my-children")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<StudentResponseDTO>> listMyChildren(
            @AuthenticationPrincipal Account currentUser) {
        return ResponseEntity.ok(studentService.listMyChildren(currentUser));
    }

    @GetMapping("/api/students/{id}")
    public ResponseEntity<StudentResponseDTO> getStudent(
            @PathVariable Long id,
            @AuthenticationPrincipal Account currentUser) {
        return ResponseEntity.ok(studentService.getStudentScoped(id, currentUser));
    }

    @PostMapping("/api/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<StudentResponseDTO> createStudent(
            @Valid @RequestBody StudentCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.createStudent(request));
    }

    @PutMapping("/api/students/{id}")
    public ResponseEntity<StudentResponseDTO> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody StudentUpdateRequestDTO request,
            @AuthenticationPrincipal Account currentUser) {
        return ResponseEntity.ok(studentService.updateStudent(id, request, currentUser));
    }

    @DeleteMapping("/api/students/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        studentService.softDeleteStudent(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/student/by-parent/{accountId}")
    public ResponseEntity<?> getByParent(@PathVariable String accountId) {
        return ResponseEntity.ok(studentService.getStudentsByParent(accountId));
    }

    @PostMapping("/api/student/{id}/join")
    public ResponseEntity<?> joinByCode(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            studentService.joinClassroomByCode(id, body.get("classCode"));
            return ResponseEntity.ok(Map.of("status", "joined"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/student/{id}/classroom/{classroomId}")
    public ResponseEntity<?> removeFromClassroom(@PathVariable Long id, @PathVariable Long classroomId) {
        try {
            studentService.removeFromClassroom(id, classroomId);
            return ResponseEntity.ok(Map.of("status", "removed"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

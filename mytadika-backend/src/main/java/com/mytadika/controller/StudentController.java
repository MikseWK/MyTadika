package com.mytadika.controller;

import com.mytadika.dto.StudentCreateRequestDTO;
import com.mytadika.dto.StudentResponseDTO;
import com.mytadika.dto.StudentUpdateRequestDTO;
import com.mytadika.security.AccountResolver;
import com.mytadika.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;
    private final AccountResolver accountResolver;

    public StudentController(StudentService studentService, AccountResolver accountResolver) {
        this.studentService = studentService;
        this.accountResolver = accountResolver;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<List<StudentResponseDTO>> listStudents(
            @RequestParam(required = false) Long classroomId) {
        return ResponseEntity.ok(studentService.listAll(classroomId));
    }

    @GetMapping("/my-children")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<StudentResponseDTO>> listMyChildren() {
        return ResponseEntity.ok(studentService.listMyChildren(accountResolver.requireCurrentAccount()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentResponseDTO> getStudent(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getStudentScoped(id, accountResolver.requireCurrentAccount()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<StudentResponseDTO> createStudent(@Valid @RequestBody StudentCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.createStudent(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StudentResponseDTO> updateStudent(
            @PathVariable Long id, @Valid @RequestBody StudentUpdateRequestDTO request) {
        return ResponseEntity.ok(
                studentService.updateStudent(id, request, accountResolver.requireCurrentAccount()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        studentService.softDeleteStudent(id);
        return ResponseEntity.noContent().build();
    }
}

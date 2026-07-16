package com.mytadika.controller;
import com.mytadika.service.StudentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentService studentService;
    public StudentController(StudentService studentService) { this.studentService = studentService; }

    @GetMapping("/by-parent/{accountId}")
    public ResponseEntity<?> getByParent(@PathVariable String accountId) {
        return ResponseEntity.ok(studentService.getStudentsByParent(accountId));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinByCode(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            studentService.joinClassroomByCode(id, body.get("classCode"));
            return ResponseEntity.ok(Map.of("status", "joined"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/classroom/{classroomId}")
    public ResponseEntity<?> removeFromClassroom(@PathVariable Long id, @PathVariable Long classroomId) {
        try {
            studentService.removeFromClassroom(id, classroomId);
            return ResponseEntity.ok(Map.of("status", "removed"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

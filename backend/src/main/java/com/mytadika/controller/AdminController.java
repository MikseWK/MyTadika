package com.mytadika.controller;

import com.mytadika.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/accounts")
    public ResponseEntity<?> listAccounts(@RequestParam(defaultValue = "") String role) {
        return ResponseEntity.ok(adminService.listAccounts(role));
    }

    @PostMapping("/accounts")
    public ResponseEntity<?> createAccount(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(adminService.createAccount(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/accounts/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            adminService.updateAccountRole(id, body.get("role"));
            return ResponseEntity.ok(Map.of("status", "updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/accounts/{id}")
    public ResponseEntity<?> updateAccount(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            adminService.updateAccount(id, body);
            return ResponseEntity.ok(Map.of("status", "updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable String id) {
        try {
            adminService.deleteAccount(id);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/students")
    public ResponseEntity<?> listStudents(@RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok(adminService.listStudents(search));
    }

    @PostMapping("/students")
    public ResponseEntity<?> createStudent(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(adminService.createStudent(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/students/{id}")
    public ResponseEntity<?> updateStudent(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            adminService.updateStudent(id, body);
            return ResponseEntity.ok(Map.of("status", "updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/students/{id}")
    public ResponseEntity<?> deleteStudent(@PathVariable Long id) {
        try {
            adminService.deleteStudent(id);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

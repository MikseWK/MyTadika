package com.mytadika.controller;

import com.mytadika.service.FeeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fees")
public class FeeController {

    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    @GetMapping
    public ResponseEntity<?> getAllFees() {
        return ResponseEntity.ok(feeService.getAllFees());
    }

    @GetMapping("/students/{studentId}")
    public ResponseEntity<?> getFeesByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(feeService.getFeesByStudent(studentId));
    }

    @GetMapping("/pending-count/{parentAccountId}")
    public ResponseEntity<?> getPendingCountForParent(@PathVariable String parentAccountId) {
        return ResponseEntity.ok(Map.of("count", feeService.getPendingCountForParent(parentAccountId)));
    }

    @GetMapping("/pending-counts/{parentAccountId}")
    public ResponseEntity<?> getPendingCountsByStudent(@PathVariable String parentAccountId) {
        return ResponseEntity.ok(feeService.getPendingCountsByStudentForParent(parentAccountId));
    }

    @PostMapping("/students/{studentId}")
    public ResponseEntity<?> createFee(@PathVariable Long studentId, @RequestBody Map<String, Object> body) {
        try {
            String description = (String) body.get("description");
            Double amount = body.get("amount") != null ? ((Number) body.get("amount")).doubleValue() : null;
            String dueDate = (String) body.get("dueDate");
            return ResponseEntity.ok(feeService.createFee(studentId, description, amount, dueDate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/classroom/{classroomId}")
    public ResponseEntity<?> createFeeForClassroom(@PathVariable Long classroomId, @RequestBody Map<String, Object> body) {
        try {
            String description = (String) body.get("description");
            Double amount = body.get("amount") != null ? ((Number) body.get("amount")).doubleValue() : null;
            String dueDate = (String) body.get("dueDate");
            return ResponseEntity.ok(feeService.createFeeForClassroom(classroomId, description, amount, dueDate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/bulk")
    public ResponseEntity<?> bulkUpdateFees(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Number> rawIds = (List<Number>) body.get("feeIds");
            List<Long> feeIds = rawIds == null ? null : rawIds.stream().map(Number::longValue).collect(Collectors.toList());
            String description = (String) body.get("description");
            Double amount = body.get("amount") != null ? ((Number) body.get("amount")).doubleValue() : null;
            String dueDate = (String) body.get("dueDate");
            return ResponseEntity.ok(feeService.bulkUpdateFees(feeIds, description, amount, dueDate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<?> bulkDeleteFees(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Number> rawIds = (List<Number>) body.get("feeIds");
            List<Long> feeIds = rawIds == null ? null : rawIds.stream().map(Number::longValue).collect(Collectors.toList());
            int count = feeService.bulkDeleteFees(feeIds);
            return ResponseEntity.ok(Map.of("deleted", count));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateFee(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String description = (String) body.get("description");
            Double amount = body.get("amount") != null ? ((Number) body.get("amount")).doubleValue() : null;
            String dueDate = (String) body.get("dueDate");
            return ResponseEntity.ok(feeService.updateFee(id, description, amount, dueDate));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/pay")
    public ResponseEntity<?> markPaid(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(feeService.markPaid(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFee(@PathVariable Long id) {
        try {
            feeService.deleteFee(id);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

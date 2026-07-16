package com.mytadika.controller;

import com.mytadika.service.StudentAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentAnalysisController {

    private final StudentAnalysisService studentAnalysisService;

    public StudentAnalysisController(StudentAnalysisService studentAnalysisService) {
        this.studentAnalysisService = studentAnalysisService;
    }

    @GetMapping("/{id}/progress-summary")
    public ResponseEntity<?> getProgressSummary(@PathVariable Long id,
            @RequestParam(required = false) Long recordId) {
        try {
            return ResponseEntity.ok(studentAnalysisService.getProgressSummary(id, recordId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

package com.mytadika.controller;

import com.mytadika.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    // ── Public Holidays ───────────────────────────────────────────────────────

    @GetMapping("/holidays")
    public ResponseEntity<?> getAllHolidays() {
        return ResponseEntity.ok(eventService.getAllHolidays());
    }

    @GetMapping("/holidays/year/{year}")
    public ResponseEntity<?> getHolidaysByYear(@PathVariable Integer year) {
        return ResponseEntity.ok(eventService.getHolidaysByYear(year));
    }

    @PostMapping("/holidays")
    public ResponseEntity<?> createHoliday(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(eventService.createHoliday(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "A holiday already exists on this date."));
        }
    }

    @PutMapping("/holidays/{id}")
    public ResponseEntity<?> updateHoliday(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(eventService.updateHoliday(id, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "A holiday already exists on this date."));
        }
    }

    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<?> deleteHoliday(@PathVariable Long id) {
        eventService.deleteHoliday(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // ── School Events ─────────────────────────────────────────────────────────

    @GetMapping("/school")
    public ResponseEntity<?> getAllSchoolEvents() {
        return ResponseEntity.ok(eventService.getAllSchoolEvents());
    }

    @PostMapping("/school")
    public ResponseEntity<?> createSchoolEvent(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(eventService.createSchoolEvent(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/school/{id}")
    public ResponseEntity<?> updateSchoolEvent(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(eventService.updateSchoolEvent(id, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/school/{id}")
    public ResponseEntity<?> deleteSchoolEvent(@PathVariable Long id) {
        eventService.deleteSchoolEvent(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}

package com.mytadika.controller;
import com.mytadika.service.ClassroomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/classroom")
public class ClassroomController {

    private final ClassroomService classroomService;
    public ClassroomController(ClassroomService classroomService) { this.classroomService = classroomService; }

    @GetMapping("/all")
    public ResponseEntity<?> getAllClassrooms() {
        return ResponseEntity.ok(classroomService.getAllClassroomsBasic());
    }

    @GetMapping("/my/{accountId}")
    public ResponseEntity<?> getMyClassrooms(@PathVariable String accountId) {
        return ResponseEntity.ok(classroomService.getMyClassrooms(accountId));
    }

    @GetMapping("/my-students/{accountId}")
    public ResponseEntity<?> getMyStudents(@PathVariable String accountId) {
        return ResponseEntity.ok(classroomService.getMyStudents(accountId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getClassroom(@PathVariable Long id) {
        return classroomService.getClassroomById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<?> getStream(@PathVariable Long id) {
        return ResponseEntity.ok(classroomService.getStream(id));
    }

    @GetMapping("/{id}/classwork")
    public ResponseEntity<?> getClasswork(@PathVariable Long id,
            @RequestParam(required = false) Long studentId) {
        return ResponseEntity.ok(classroomService.getClasswork(id, studentId));
    }

    @GetMapping("/{id}/people")
    public ResponseEntity<?> getPeople(@PathVariable Long id) {
        return ResponseEntity.ok(classroomService.getPeople(id));
    }

    @PostMapping
    public ResponseEntity<?> createClassroom(@RequestBody Map<String, String> body) {
        try { return ResponseEntity.ok(classroomService.createClassroom(body)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/{id}/enroll")
    public ResponseEntity<?> enroll(@PathVariable Long id, @RequestBody Map<String, String> body) {
        classroomService.enrollStudent(id, body.get("accountId"));
        return ResponseEntity.ok(Map.of("status", "enrolled"));
    }

    @PostMapping("/{id}/enroll-by-code")
    public ResponseEntity<?> enrollByCode(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            classroomService.enrollByStudentCode(id, body.get("studentCode"));
            return ResponseEntity.ok(Map.of("status", "enrolled"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to enroll student: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/announcements")
    public ResponseEntity<?> postAnnouncement(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try { return ResponseEntity.ok(classroomService.postAnnouncement(id, body)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable Long id) {
        classroomService.deleteAnnouncement(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/announcements/{id}/viewers")
    public ResponseEntity<?> getAnnouncementViewers(@PathVariable Long id) {
        return ResponseEntity.ok(classroomService.getAnnouncementViewers(id));
    }

    @GetMapping("/assignments/{id}/completions")
    public ResponseEntity<?> getAssignmentCompletions(@PathVariable Long id) {
        return ResponseEntity.ok(classroomService.getAssignmentCompletions(id));
    }

    @PostMapping("/announcements/{id}/pin")
    public ResponseEntity<?> togglePin(@PathVariable Long id) {
        classroomService.togglePin(id);
        return ResponseEntity.ok(Map.of("status", "toggled"));
    }

    @PostMapping("/{id}/stream/viewed")
    public ResponseEntity<?> markStreamViewed(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String accountId = body.get("accountId");
        if (accountId != null) classroomService.markStreamViewed(id, accountId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<?> createAssignment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try { return ResponseEntity.ok(classroomService.createAssignment(id, body)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<?> deleteAssignment(@PathVariable Long id) {
        classroomService.deleteAssignment(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/assignments/{id}/done")
    public ResponseEntity<?> markDone(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long studentId = body.get("studentId") instanceof Number n ? n.longValue() : null;
        String markedBy = (String) body.get("markedBy");
        if (studentId == null) return ResponseEntity.badRequest().body(Map.of("error", "studentId required"));
        classroomService.markDone(id, studentId, markedBy);
        return ResponseEntity.ok(Map.of("status", "done"));
    }

    @DeleteMapping("/assignments/{id}/done")
    public ResponseEntity<?> unmarkDone(@PathVariable Long id, @RequestParam Long studentId) {
        classroomService.unmarkDone(id, studentId);
        return ResponseEntity.ok(Map.of("status", "undone"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateClassroom(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try { return ResponseEntity.ok(classroomService.updateClassroom(id, body)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClassroom(@PathVariable Long id) {
        classroomService.deleteClassroom(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @DeleteMapping("/{id}/students/{studentId}")
    public ResponseEntity<?> removeStudentFromClassroom(@PathVariable Long id, @PathVariable Long studentId) {
        classroomService.removeStudentFromClassroom(id, studentId);
        return ResponseEntity.ok(Map.of("status", "removed"));
    }

    @DeleteMapping("/{id}/members/{accountId}")
    public ResponseEntity<?> removeStudent(@PathVariable Long id, @PathVariable String accountId) {
        classroomService.removeStudent(id, accountId);
        return ResponseEntity.ok(Map.of("status", "removed"));
    }

    @PostMapping("/{id}/teachers")
    public ResponseEntity<?> addTeacher(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try { return ResponseEntity.ok(classroomService.addTeacher(id, body.get("email"))); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @DeleteMapping("/{id}/teachers/{accountId}")
    public ResponseEntity<?> removeTeacher(@PathVariable Long id, @PathVariable String accountId) {
        try { classroomService.removeTeacher(id, accountId); return ResponseEntity.ok(Map.of("status", "removed")); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }
}

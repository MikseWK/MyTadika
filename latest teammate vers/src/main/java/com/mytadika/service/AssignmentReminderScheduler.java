package com.mytadika.service;

import com.mytadika.model.Assignment;
import com.mytadika.model.Classroom;
import com.mytadika.model.Student;
import com.mytadika.repository.AssignmentRepository;
import com.mytadika.repository.ClassroomRepository;
import com.mytadika.repository.ClassworkCompletionRepository;
import com.mytadika.repository.StudentClassroomRepository;
import com.mytadika.repository.StudentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Daily job that reminds parents of assignments due today or tomorrow,
// skipping any child who has already marked the assignment done.
@Component
@Slf4j
public class AssignmentReminderScheduler {

    private final AssignmentRepository assignmentRepo;
    private final ClassroomRepository classroomRepo;
    private final StudentRepository studentRepo;
    private final StudentClassroomRepository studentClassroomRepo;
    private final ClassworkCompletionRepository completionRepo;
    private final NotificationService notifService;

    public AssignmentReminderScheduler(AssignmentRepository assignmentRepo, ClassroomRepository classroomRepo,
            StudentRepository studentRepo, StudentClassroomRepository studentClassroomRepo,
            ClassworkCompletionRepository completionRepo, NotificationService notifService) {
        this.assignmentRepo = assignmentRepo;
        this.classroomRepo = classroomRepo;
        this.studentRepo = studentRepo;
        this.studentClassroomRepo = studentClassroomRepo;
        this.completionRepo = completionRepo;
        this.notifService = notifService;
    }

    @Scheduled(cron = "0 0 7 * * *")
    public void sendDueDateReminders() {
        LocalDate today = LocalDate.now();
        remindForDate(today.toString(), "today");
        remindForDate(today.plusDays(1).toString(), "tomorrow");
    }

    private void remindForDate(String dueDate, String label) {
        for (Assignment a : assignmentRepo.findByDueDate(dueDate)) {
            try {
                remindForAssignment(a, label);
            } catch (Exception e) {
                log.error("[Reminder] Failed for assignment {}: {}", a.getId(), e.getMessage(), e);
            }
        }
    }

    private void remindForAssignment(Assignment a, String label) {
        String classroomName = classroomRepo.findById(a.getClassroomId()).map(Classroom::getName).orElse("classroom");

        Map<String, List<Student>> byParent = new LinkedHashMap<>();
        studentClassroomRepo.findByClassroomId(a.getClassroomId()).forEach(sc ->
            studentRepo.findById(sc.getStudentId()).ifPresent(s -> {
                if (s.getParentId() == null) return;
                if (completionRepo.existsByAssignmentIdAndStudentId(a.getId(), s.getId())) return;
                byParent.computeIfAbsent(s.getParentId(), k -> new ArrayList<>()).add(s);
            })
        );

        byParent.forEach((parentId, kids) -> {
            String childNames = kids.stream().map(Student::getFullName).collect(Collectors.joining(", "));
            String title = "Reminder: " + a.getTitle() + " due " + label + " (" + classroomName + ") — " + childNames;
            String link = "/parent/parentclassroom.html?childId=" + kids.get(0).getId()
                    + "&classroomId=" + a.getClassroomId() + "&tab=classwork&postId=" + a.getId();
            notifService.create(parentId, title, null, link);
        });
    }
}

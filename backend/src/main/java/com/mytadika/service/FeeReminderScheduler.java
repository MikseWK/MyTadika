package com.mytadika.service;

import com.mytadika.model.Fee;
import com.mytadika.model.Student;
import com.mytadika.repository.FeeRepository;
import com.mytadika.repository.StudentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// Daily job that reminds parents of pending fees due in 3, 2, or 1 day(s),
// mirroring AssignmentReminderScheduler's pattern.
@Component
@Slf4j
public class FeeReminderScheduler {

    private final FeeRepository feeRepository;
    private final StudentRepository studentRepository;
    private final NotificationService notificationService;

    public FeeReminderScheduler(FeeRepository feeRepository, StudentRepository studentRepository,
                                NotificationService notificationService) {
        this.feeRepository = feeRepository;
        this.studentRepository = studentRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void sendDueDateReminders() {
        LocalDate today = LocalDate.now();
        remindForDate(today.plusDays(3).toString(), "in 3 days");
        remindForDate(today.plusDays(2).toString(), "in 2 days");
        remindForDate(today.plusDays(1).toString(), "tomorrow");
    }

    private void remindForDate(String dueDate, String label) {
        for (Fee fee : feeRepository.findAllByOrderByDueDateAsc()) {
            if (!"PENDING".equals(fee.getStatus()) || !dueDate.equals(fee.getDueDate())) continue;
            try {
                remindForFee(fee, label);
            } catch (Exception e) {
                log.error("[FeeReminder] Failed for fee {}: {}", fee.getId(), e.getMessage(), e);
            }
        }
    }

    private void remindForFee(Fee fee, String label) {
        Student student = studentRepository.findById(fee.getStudentId()).orElse(null);
        if (student == null || student.getParentId() == null) return;

        String title = "Fee reminder: " + fee.getDescription() + " due " + label + " (" + student.getFullName() + ")";
        String body = "RM " + String.format("%.2f", fee.getAmount()) + " due " + fee.getDueDate()
                + ". A " + (int) (FeeService.LATE_FEE_RATE * 100) + "% late fee applies if unpaid after the due date.";
        String link = "/parent/parentfees.html?studentId=" + student.getId();
        notificationService.create(student.getParentId(), title, body, link);
    }
}

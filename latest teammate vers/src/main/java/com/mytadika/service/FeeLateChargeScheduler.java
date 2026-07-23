package com.mytadika.service;

import com.mytadika.model.Account;
import com.mytadika.model.Fee;
import com.mytadika.model.Student;
import com.mytadika.repository.AccountRepository;
import com.mytadika.repository.FeeRepository;
import com.mytadika.repository.StudentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Daily job that applies a one-time late fee surcharge to any pending fee that has
// gone past its due date, and notifies the parent. Runs after FeeReminderScheduler
// so a fee gets its "due tomorrow" reminder before it ever reaches this charge.
// Admins get a single end-of-run digest instead of one notification per fee.
@Component
@Slf4j
public class FeeLateChargeScheduler {

    private final FeeRepository feeRepository;
    private final StudentRepository studentRepository;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;

    public FeeLateChargeScheduler(FeeRepository feeRepository, StudentRepository studentRepository,
                                  AccountRepository accountRepository, NotificationService notificationService) {
        this.feeRepository = feeRepository;
        this.studentRepository = studentRepository;
        this.accountRepository = accountRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 30 8 * * *")
    public void applyLateCharges() {
        String today = LocalDate.now().toString();
        List<String> charged = new ArrayList<>();
        for (Fee fee : feeRepository.findAllByOrderByDueDateAsc()) {
            boolean overdue = "PENDING".equals(fee.getStatus()) && fee.getDueDate() != null && fee.getDueDate().compareTo(today) < 0;
            boolean alreadyCharged = fee.getLateFeeAmount() != null;
            if (!overdue || alreadyCharged) continue;

            try {
                charged.add(applyLateCharge(fee));
            } catch (Exception e) {
                log.error("[FeeLateCharge] Failed for fee {}: {}", fee.getId(), e.getMessage(), e);
            }
        }
        if (!charged.isEmpty()) notifyAdminsDigest(charged);
    }

    private String applyLateCharge(Fee fee) {
        double lateFee = Math.round(fee.getAmount() * FeeService.LATE_FEE_RATE * 100) / 100.0;
        fee.setLateFeeAmount(lateFee);
        feeRepository.save(fee);

        Student student = studentRepository.findById(fee.getStudentId()).orElse(null);
        String studentName = student != null ? student.getFullName() : "a student";
        double total = fee.getAmount() + lateFee;

        if (student != null && student.getParentId() != null) {
            String title = "Fee overdue: " + fee.getDescription() + " (" + studentName + ")";
            String body = "A " + (int) (FeeService.LATE_FEE_RATE * 100) + "% late fee of RM " + String.format("%.2f", lateFee)
                    + " has been added. Total now due: RM " + String.format("%.2f", total) + ".";
            String link = "/parent/parentfees.html?studentId=" + student.getId();
            notificationService.create(student.getParentId(), title, body, link);
        }
        return studentName + " — " + fee.getDescription() + " (RM " + String.format("%.2f", total) + ")";
    }

    private void notifyAdminsDigest(List<String> charged) {
        List<Account> admins = accountRepository.findByRoleType(Account.RoleType.ADMIN);
        String title = charged.size() + " fee" + (charged.size() != 1 ? "s" : "") + " just went overdue";
        String body = String.join("; ", charged);
        for (Account admin : admins)
            notificationService.create(admin.getAccountId(), title, body, "/admin/adminfees.html?status=OVERDUE");
    }
}

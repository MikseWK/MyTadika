package com.mytadika.service;

import com.mytadika.model.Fee;
import com.mytadika.model.Student;
import com.mytadika.model.StudentClassroom;
import com.mytadika.repository.FeeRepository;
import com.mytadika.repository.StudentClassroomRepository;
import com.mytadika.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FeeService {

    // 5% surcharge automatically applied to a fee once it goes overdue unpaid — see FeeLateChargeScheduler.
    public static final double LATE_FEE_RATE = 0.05;

    private final FeeRepository feeRepository;
    private final StudentRepository studentRepository;
    private final StudentClassroomRepository studentClassroomRepository;
    private final NotificationService notificationService;

    public FeeService(FeeRepository feeRepository, StudentRepository studentRepository,
                      StudentClassroomRepository studentClassroomRepository,
                      NotificationService notificationService) {
        this.feeRepository = feeRepository;
        this.studentRepository = studentRepository;
        this.studentClassroomRepository = studentClassroomRepository;
        this.notificationService = notificationService;
    }

    public List<Map<String, Object>> getAllFees() {
        return feeRepository.findAllByOrderByDueDateAsc().stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getFeesByStudent(Long studentId) {
        return feeRepository.findByStudentIdOrderByDueDateDesc(studentId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    // Count of unpaid fees across every child linked to this parent — powers the sidebar badge.
    public int getPendingCountForParent(String parentAccountId) {
        List<Long> studentIds = studentRepository.findByParentId(parentAccountId).stream()
                .map(Student::getId).collect(Collectors.toList());
        if (studentIds.isEmpty()) return 0;
        return (int) feeRepository.findByStudentIdIn(studentIds).stream()
                .filter(f -> "PENDING".equals(f.getStatus()))
                .count();
    }

    // Per-child breakdown of unpaid fee counts — powers the child-switcher badges on the Fees page.
    public Map<Long, Long> getPendingCountsByStudentForParent(String parentAccountId) {
        List<Long> studentIds = studentRepository.findByParentId(parentAccountId).stream()
                .map(Student::getId).collect(Collectors.toList());
        if (studentIds.isEmpty()) return Map.of();
        return feeRepository.findByStudentIdIn(studentIds).stream()
                .filter(f -> "PENDING".equals(f.getStatus()))
                .collect(Collectors.groupingBy(f -> f.getStudentId(), Collectors.counting()));
    }

    public Map<String, Object> createFee(Long studentId, String description, Double amount, String dueDate) {
        validateFeeFields(description, amount, dueDate);

        Fee fee = Fee.builder()
                .studentId(studentId)
                .description(description)
                .amount(amount)
                .dueDate(dueDate)
                .status("PENDING")
                .build();
        fee = feeRepository.save(fee);
        notifyParent(fee);
        return toMap(fee);
    }

    // Bulk-assigns the same fee to every student currently enrolled in a classroom —
    // each student still gets their own independent Fee row (own status/due date/notification).
    public List<Map<String, Object>> createFeeForClassroom(Long classroomId, String description, Double amount, String dueDate) {
        validateFeeFields(description, amount, dueDate);

        List<StudentClassroom> links = studentClassroomRepository.findByClassroomId(classroomId);
        if (links.isEmpty())
            throw new IllegalArgumentException("This classroom has no enrolled students");

        List<Map<String, Object>> created = new ArrayList<>();
        for (StudentClassroom link : links) {
            Fee fee = Fee.builder()
                    .studentId(link.getStudentId())
                    .description(description)
                    .amount(amount)
                    .dueDate(dueDate)
                    .status("PENDING")
                    .build();
            fee = feeRepository.save(fee);
            notifyParent(fee);
            created.add(toMap(fee));
        }
        return created;
    }

    public Map<String, Object> updateFee(Long id, String description, Double amount, String dueDate) {
        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fee record not found"));
        validateFeeFields(description, amount, dueDate);

        fee.setDescription(description);
        fee.setAmount(amount);
        fee.setDueDate(dueDate);
        fee = feeRepository.save(fee);
        notifyParentOfUpdate(fee);
        return toMap(fee);
    }

    // Applies the same description/amount/dueDate edit to a whole set of existing fee
    // records at once — used by the admin ledger's "Bulk Edit" action for a filtered class.
    public List<Map<String, Object>> bulkUpdateFees(List<Long> feeIds, String description, Double amount, String dueDate) {
        validateFeeFields(description, amount, dueDate);
        if (feeIds == null || feeIds.isEmpty())
            throw new IllegalArgumentException("No fee records selected");

        List<Map<String, Object>> updated = new ArrayList<>();
        for (Long id : feeIds) {
            Fee fee = feeRepository.findById(id).orElse(null);
            if (fee == null) continue;
            fee.setDescription(description);
            fee.setAmount(amount);
            fee.setDueDate(dueDate);
            fee = feeRepository.save(fee);
            notifyParentOfUpdate(fee);
            updated.add(toMap(fee));
        }
        return updated;
    }

    private void validateFeeFields(String description, Double amount, String dueDate) {
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("Description is required");
        if (amount == null || amount <= 0)
            throw new IllegalArgumentException("Amount must be greater than 0");
        if (dueDate == null || dueDate.isBlank())
            throw new IllegalArgumentException("Due date is required");
    }

    private void notifyParent(Fee fee) {
        Student student = studentRepository.findById(fee.getStudentId()).orElse(null);
        if (student == null || student.getParentId() == null) return;

        String title = "New fee: " + fee.getDescription() + " for " + student.getFullName();
        String body = "RM " + String.format("%.2f", fee.getAmount()) + " due " + fee.getDueDate()
                + ". A " + (int) (LATE_FEE_RATE * 100) + "% late fee applies if unpaid after the due date.";
        String link = "/parent/parentfees.html?studentId=" + student.getId();
        notificationService.create(student.getParentId(), title, body, link);
    }

    private void notifyParentOfUpdate(Fee fee) {
        Student student = studentRepository.findById(fee.getStudentId()).orElse(null);
        if (student == null || student.getParentId() == null) return;

        String title = "Fee updated: " + fee.getDescription() + " for " + student.getFullName();
        String body = "Now RM " + String.format("%.2f", fee.getAmount()) + ", due " + fee.getDueDate() + ".";
        String link = "/parent/parentfees.html?studentId=" + student.getId();
        notificationService.create(student.getParentId(), title, body, link);
    }

    public Map<String, Object> markPaid(Long id) {
        return markPaid(id, "MANUAL");
    }

    public Map<String, Object> markPaid(Long id, String paymentMethod) {
        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fee record not found"));
        fee.setStatus("PAID");
        fee.setPaidAt(LocalDateTime.now());
        fee.setPaymentMethod(paymentMethod);
        return toMap(feeRepository.save(fee));
    }

    public void deleteFee(Long id) {
        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fee record not found"));
        feeRepository.deleteById(id);
        notifyParentOfDeletion(fee);
    }

    public int bulkDeleteFees(List<Long> feeIds) {
        if (feeIds == null || feeIds.isEmpty())
            throw new IllegalArgumentException("No fee records selected");
        List<Fee> existing = feeRepository.findAllById(feeIds);
        feeRepository.deleteAll(existing);
        existing.forEach(this::notifyParentOfDeletion);
        return existing.size();
    }

    private void notifyParentOfDeletion(Fee fee) {
        Student student = studentRepository.findById(fee.getStudentId()).orElse(null);
        if (student == null || student.getParentId() == null) return;

        String title = "Fee removed: " + fee.getDescription() + " for " + student.getFullName();
        String body = "The RM " + String.format("%.2f", fee.getAmount()) + " fee due " + fee.getDueDate()
                + " has been removed and no longer needs to be paid.";
        String link = "/parent/parentfees.html?studentId=" + student.getId();
        notificationService.create(student.getParentId(), title, body, link);
    }

    private Map<String, Object> toMap(Fee fee) {
        double lateFee = fee.getLateFeeAmount() != null ? fee.getLateFeeAmount() : 0.0;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", fee.getId());
        map.put("studentId", fee.getStudentId());
        map.put("description", fee.getDescription());
        map.put("amount", fee.getAmount());
        map.put("lateFeeAmount", fee.getLateFeeAmount());
        map.put("totalAmount", fee.getAmount() + lateFee);
        map.put("dueDate", fee.getDueDate());
        map.put("status", fee.getStatus());
        map.put("paidAt", fee.getPaidAt() != null ? fee.getPaidAt().toString() : null);
        map.put("paymentMethod", fee.getPaymentMethod());
        map.put("createdAt", fee.getCreatedAt() != null ? fee.getCreatedAt().toString() : null);
        return map;
    }
}

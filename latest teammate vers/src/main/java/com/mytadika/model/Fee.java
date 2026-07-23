package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "fees")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Fee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(nullable = false) private String description;
    @Column(nullable = false) private Double amount;
    @Column(name = "due_date", nullable = false) private String dueDate;
    @Column(nullable = false, length = 10) private String status;
    @Column(name = "late_fee_amount") private Double lateFeeAmount;
    @Column(name = "paid_at") private LocalDateTime paidAt;
    @Column(name = "payment_method", length = 20) private String paymentMethod;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); if (status == null) status = "PENDING"; }
}

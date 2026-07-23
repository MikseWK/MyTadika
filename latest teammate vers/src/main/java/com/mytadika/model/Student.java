package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "student")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Student {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "parent_id") private String parentId;
    @Column(name = "classroom_id") private Long classroomId;
    @Column(name = "full_name") private String fullName;
    @Column(name = "date_of_birth") private String dateOfBirth;
    @Column private String gender;
    @Column(name = "medical_info", columnDefinition = "TEXT") private String medicalInfo;
    @Column(name = "emergency_contact") private String emergencyContact;
    @Column(name = "student_code", unique = true) private String studentCode;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
}

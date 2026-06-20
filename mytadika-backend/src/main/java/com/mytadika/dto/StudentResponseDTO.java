package com.mytadika.dto;

import com.mytadika.model.Gender;
import com.mytadika.model.Student;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StudentResponseDTO {

    private Long id;
    private Long parentId;
    private String parentName;
    private Long classroomId;
    private String className;
    private String fullName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String medicalInfo;
    private String emergencyContact;
    private String studentCode;
    private LocalDateTime createdAt;

    public static StudentResponseDTO from(Student student) {
        StudentResponseDTO dto = new StudentResponseDTO();
        dto.id = student.getId();
        dto.parentId = student.getParent().getId();
        dto.parentName = student.getParent().getFullName();
        if (student.getClassroom() != null) {
            dto.classroomId = student.getClassroom().getId();
            dto.className = student.getClassroom().getClassName();
        }
        dto.fullName = student.getFullName();
        dto.dateOfBirth = student.getDateOfBirth();
        dto.gender = student.getGender();
        dto.medicalInfo = student.getMedicalInfo();
        dto.emergencyContact = student.getEmergencyContact();
        dto.studentCode = student.getStudentCode();
        dto.createdAt = student.getCreatedAt();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getParentName() {
        return parentName;
    }

    public Long getClassroomId() {
        return classroomId;
    }

    public String getClassName() {
        return className;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public String getMedicalInfo() {
        return medicalInfo;
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

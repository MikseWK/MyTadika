package com.mytadika.dto;

import com.mytadika.model.Gender;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Partial update — every field is optional. Which fields actually take effect
 * depends on the caller's role: PARENT may only change medicalInfo/emergencyContact,
 * TEACHER/ADMIN may change everything. Enforced in StudentService, not here.
 */
public class StudentUpdateRequestDTO {

    private Long classroomId;

    @Size(max = 100)
    private String fullName;

    @Past(message = "Date of birth must be in the past.")
    private LocalDate dateOfBirth;

    private Gender gender;

    private String medicalInfo;

    @Size(max = 20)
    private String emergencyContact;

    @Size(max = 20)
    private String studentCode;

    public Long getClassroomId() {
        return classroomId;
    }

    public void setClassroomId(Long classroomId) {
        this.classroomId = classroomId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getMedicalInfo() {
        return medicalInfo;
    }

    public void setMedicalInfo(String medicalInfo) {
        this.medicalInfo = medicalInfo;
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public void setStudentCode(String studentCode) {
        this.studentCode = studentCode;
    }
}

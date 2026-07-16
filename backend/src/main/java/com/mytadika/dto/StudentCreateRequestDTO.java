package com.mytadika.dto;

import com.mytadika.model.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Created by a TEACHER/ADMIN; the parent account is assigned by email (the
 * one identifier a teacher would realistically have on hand) rather than by
 * the parent's internal account ID, since there's no parent-account-search
 * endpoint in this module.
 */
public class StudentCreateRequestDTO {

    @NotBlank(message = "Please complete all mandatory fields before saving.")
    @Email(message = "Please enter a valid parent email address.")
    private String parentEmail;

    private Long classroomId;

    @NotBlank(message = "Please complete all mandatory fields before saving.")
    @Size(max = 100)
    private String fullName;

    @NotNull(message = "Please complete all mandatory fields before saving.")
    @Past(message = "Date of birth must be in the past.")
    private LocalDate dateOfBirth;

    @NotNull(message = "Please complete all mandatory fields before saving.")
    private Gender gender;

    private String medicalInfo;

    @NotBlank(message = "Please complete all mandatory fields before saving.")
    @Size(max = 20)
    private String emergencyContact;

    @Size(max = 20)
    private String studentCode;

    public String getParentEmail() {
        return parentEmail;
    }

    public void setParentEmail(String parentEmail) {
        this.parentEmail = parentEmail;
    }

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

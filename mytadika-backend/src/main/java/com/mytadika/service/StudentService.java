package com.mytadika.service;

import com.mytadika.dto.StudentCreateRequestDTO;
import com.mytadika.dto.StudentResponseDTO;
import com.mytadika.dto.StudentUpdateRequestDTO;
import com.mytadika.exception.InvalidInputException;
import com.mytadika.exception.ResourceNotFoundException;
import com.mytadika.exception.UnauthorizedAccessException;
import com.mytadika.model.Account;
import com.mytadika.model.Classroom;
import com.mytadika.model.Role;
import com.mytadika.model.Student;
import com.mytadika.repository.AccountRepository;
import com.mytadika.repository.ClassroomRepository;
import com.mytadika.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final AccountRepository accountRepository;
    private final ClassroomRepository classroomRepository;

    public StudentService(StudentRepository studentRepository,
                           AccountRepository accountRepository,
                           ClassroomRepository classroomRepository) {
        this.studentRepository = studentRepository;
        this.accountRepository = accountRepository;
        this.classroomRepository = classroomRepository;
    }

    public List<StudentResponseDTO> listAll(Long classroomId) {
        List<Student> students = classroomId != null
                ? studentRepository.findByClassroomIdAndDeletedAtIsNull(classroomId)
                : studentRepository.findByDeletedAtIsNull();
        return students.stream().map(StudentResponseDTO::from).toList();
    }

    public List<StudentResponseDTO> listMyChildren(Account currentUser) {
        return studentRepository.findByParentAccountIdAndDeletedAtIsNull(currentUser.getAccountId())
                .stream().map(StudentResponseDTO::from).toList();
    }

    public StudentResponseDTO getStudentScoped(Long id, Account currentUser) {
        Student student = requireActiveStudent(id);
        assertCanAccess(student, currentUser);
        return StudentResponseDTO.from(student);
    }

    public StudentResponseDTO createStudent(StudentCreateRequestDTO request) {
        Account parent = accountRepository.findByEmail(request.getParentEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account found for parent email " + request.getParentEmail() + "."));
        if (parent.getRole() != Role.PARENT) {
            throw new InvalidInputException("The assigned account must have the PARENT role.");
        }

        Classroom classroom = resolveClassroom(request.getClassroomId());

        Student student = Student.builder()
                .parent(parent)
                .classroom(classroom)
                .fullName(request.getFullName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .medicalInfo(request.getMedicalInfo())
                .emergencyContact(request.getEmergencyContact())
                .studentCode(request.getStudentCode())
                .build();

        return StudentResponseDTO.from(studentRepository.save(student));
    }

    public StudentResponseDTO updateStudent(Long id, StudentUpdateRequestDTO request, Account currentUser) {
        Student student = requireActiveStudent(id);
        assertCanAccess(student, currentUser);

        // Parents may only edit medical/emergency info; full edits (including
        // identity fields and classroom assignment) are teacher/admin-only.
        if (request.getMedicalInfo() != null) student.setMedicalInfo(request.getMedicalInfo());
        if (request.getEmergencyContact() != null) student.setEmergencyContact(request.getEmergencyContact());

        if (currentUser.getRole() != Role.PARENT) {
            if (request.getFullName() != null) student.setFullName(request.getFullName());
            if (request.getDateOfBirth() != null) student.setDateOfBirth(request.getDateOfBirth());
            if (request.getGender() != null) student.setGender(request.getGender());
            if (request.getStudentCode() != null) student.setStudentCode(request.getStudentCode());
            if (request.getClassroomId() != null) {
                student.setClassroom(resolveClassroom(request.getClassroomId()));
            }
        }

        return StudentResponseDTO.from(studentRepository.save(student));
    }

    public void softDeleteStudent(Long id) {
        Student student = requireActiveStudent(id);
        student.setDeletedAt(LocalDateTime.now());
        studentRepository.save(student);
    }

    private Classroom resolveClassroom(Long classroomId) {
        if (classroomId == null) return null;
        return classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found."));
    }

    private Student requireActiveStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
        if (student.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Student not found.");
        }
        return student;
    }

    private void assertCanAccess(Student student, Account currentUser) {
        if (currentUser.getRole() == Role.PARENT && !student.getParent().getAccountId().equals(currentUser.getAccountId())) {
            throw new UnauthorizedAccessException("Cannot access another parent's child.");
        }
    }
}

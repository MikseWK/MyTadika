package com.mytadika.service;

import com.mytadika.exception.UnauthorizedAccessException;
import com.mytadika.model.Account;
import com.mytadika.model.Gender;
import com.mytadika.model.Role;
import com.mytadika.model.Student;
import com.mytadika.repository.AccountRepository;
import com.mytadika.repository.ClassroomRepository;
import com.mytadika.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ClassroomRepository classroomRepository;

    private StudentService studentService;

    private Account owningParent;
    private Account otherParent;
    private Student student;

    @BeforeEach
    void setUp() {
        studentService = new StudentService(studentRepository, accountRepository, classroomRepository);

        owningParent = Account.builder().accountId("parent01").fullName("Owning Parent").role(Role.PARENT).build();
        otherParent = Account.builder().accountId("parent02").fullName("Other Parent").role(Role.PARENT).build();
        student = Student.builder()
                .id(10L)
                .parent(owningParent)
                .fullName("Kid")
                .dateOfBirth(LocalDate.of(2022, 1, 1))
                .gender(Gender.MALE)
                .emergencyContact("0123456789")
                .build();
    }

    @Test
    void getStudentScoped_allowsOwningParent() {
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));

        var dto = studentService.getStudentScoped(10L, owningParent);

        assertThat(dto.getId()).isEqualTo(10L);
    }

    @Test
    void getStudentScoped_rejectsNonOwningParent() {
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> studentService.getStudentScoped(10L, otherParent))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void getStudentScoped_allowsTeacherRegardlessOfOwnership() {
        Account teacher = Account.builder().accountId("teacher01").fullName("Teacher").role(Role.TEACHER).build();
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));

        var dto = studentService.getStudentScoped(10L, teacher);

        assertThat(dto.getId()).isEqualTo(10L);
    }

    @Test
    void getStudentScoped_allowsAdminRegardlessOfOwnership() {
        Account admin = Account.builder().accountId("admin01").fullName("Admin").role(Role.ADMIN).build();
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));

        var dto = studentService.getStudentScoped(10L, admin);

        assertThat(dto.getId()).isEqualTo(10L);
    }
}

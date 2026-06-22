package com.mytadika.service;

import com.mytadika.exception.UnauthorizedAccessException;
import com.mytadika.model.Account;
import com.mytadika.model.AcademicRecord;
import com.mytadika.model.Gender;
import com.mytadika.model.Role;
import com.mytadika.model.Student;
import com.mytadika.repository.AcademicRecordRepository;
import com.mytadika.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicServiceTest {

    @Mock
    private AcademicRecordRepository academicRecordRepository;
    @Mock
    private StudentRepository studentRepository;

    private AcademicService academicService;

    private Account owningParent;
    private Account otherParent;
    private Student student;
    private AcademicRecord record;

    @BeforeEach
    void setUp() {
        academicService = new AcademicService(academicRecordRepository, studentRepository, new GradeCalculationService());

        owningParent = Account.builder().id(1L).fullName("Owning Parent").role(Role.PARENT).build();
        otherParent = Account.builder().id(2L).fullName("Other Parent").role(Role.PARENT).build();
        student = Student.builder()
                .id(10L)
                .parent(owningParent)
                .fullName("Kid")
                .dateOfBirth(LocalDate.of(2022, 1, 1))
                .gender(Gender.MALE)
                .emergencyContact("0123456789")
                .build();
        record = AcademicRecord.builder()
                .id(100L)
                .student(student)
                .academicTerm("Term 1 - 2026")
                .averageMark(85.0)
                .finalGrade("A")
                .build();
    }

    @Test
    void getRecordScoped_allowsOwningParent() {
        when(academicRecordRepository.findById(100L)).thenReturn(Optional.of(record));

        var dto = academicService.getRecordScoped(100L, owningParent);

        assertThat(dto.getId()).isEqualTo(100L);
    }

    @Test
    void getRecordScoped_rejectsNonOwningParent() {
        when(academicRecordRepository.findById(100L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> academicService.getRecordScoped(100L, otherParent))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void listForStudent_rejectsNonOwningParent() {
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> academicService.listForStudent(10L, otherParent))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void listForStudent_allowsTeacher() {
        Account teacher = Account.builder().id(3L).fullName("Teacher").role(Role.TEACHER).build();
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));
        when(academicRecordRepository.findByStudentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(record));

        var records = academicService.listForStudent(10L, teacher);

        assertThat(records).hasSize(1);
    }
}

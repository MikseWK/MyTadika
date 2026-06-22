package com.mytadika.controller;

import com.mytadika.exception.UnauthorizedAccessException;
import com.mytadika.model.Account;
import com.mytadika.model.Gender;
import com.mytadika.model.HealthRecord;
import com.mytadika.model.Role;
import com.mytadika.model.Student;
import com.mytadika.repository.AllergyProfileRepository;
import com.mytadika.repository.HealthRecordRepository;
import com.mytadika.repository.StudentRepository;
import com.mytadika.security.AccountResolver;
import com.mytadika.service.AiPredictionClient;
import com.mytadika.service.HealthAdviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private HealthRecordRepository healthRecordRepository;
    @Mock
    private AllergyProfileRepository allergyProfileRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AiPredictionClient aiPredictionClient;
    @Mock
    private HealthAdviceService healthAdviceService;
    @Mock
    private AccountResolver accountResolver;

    private HealthController healthController;

    private Account owningParent;
    private Account otherParent;
    private Student student;

    @BeforeEach
    void setUp() {
        healthController = new HealthController(
                healthRecordRepository, allergyProfileRepository, studentRepository,
                aiPredictionClient, healthAdviceService, accountResolver);

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

        lenient().when(studentRepository.findById(10L)).thenReturn(Optional.of(student));
    }

    @Test
    void getHistory_rejectsNonOwningParent() {
        when(accountResolver.requireCurrentAccount()).thenReturn(otherParent);

        assertThatThrownBy(() -> healthController.getHistory(10L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void getHistory_allowsOwningParent() {
        when(accountResolver.requireCurrentAccount()).thenReturn(owningParent);
        when(healthRecordRepository.findByStudentIdOrderByRecordedAtDesc(10L)).thenReturn(Collections.emptyList());

        var response = healthController.getHistory(10L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void getHistory_allowsTeacherRegardlessOfOwnership() {
        Account teacher = Account.builder().id(3L).fullName("Teacher").role(Role.TEACHER).build();
        when(accountResolver.requireCurrentAccount()).thenReturn(teacher);
        when(healthRecordRepository.findByStudentIdOrderByRecordedAtDesc(10L)).thenReturn(Collections.emptyList());

        var response = healthController.getHistory(10L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void recordMeasurement_rejectsNonOwningParent() {
        when(accountResolver.requireCurrentAccount()).thenReturn(otherParent);

        var request = new HealthController.HealthRequestDTO();
        request.setChildId("10");
        request.setAgeMonths(24.0);
        request.setWeightKg(12.0);
        request.setHeightCm(85.0);
        request.setActivityLevel(1);

        assertThatThrownBy(() -> healthController.recordMeasurement(request))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void recordMeasurement_computesBmiFromWeightAndHeight() {
        when(accountResolver.requireCurrentAccount()).thenReturn(owningParent);
        when(aiPredictionClient.predict(anyString(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
                .thenReturn(new AiPredictionClient.PredictionResponse(
                        "10", "normal", 0, 0.9,
                        new AiPredictionClient.ProbabilityBreakdown(0.9, 0.05, 0.05),
                        new AiPredictionClient.ClinicalFlags(false, false, false),
                        "test", "disclaimer"));
        when(healthAdviceService.generateAdvice(anyString(), anyDouble(), anyInt(), anyList(), anyList(), any()))
                .thenReturn(HealthAdviceService.AdviceResult.builder().status("normal").build());
        when(healthRecordRepository.save(any(HealthRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new HealthController.HealthRequestDTO();
        request.setChildId("10");
        request.setAgeMonths(24.0);
        request.setWeightKg(12.0);
        request.setHeightCm(100.0); // 12.0 / (1.0 * 1.0) = 12.0 BMI
        request.setActivityLevel(1);

        var response = healthController.recordMeasurement(request);

        ArgumentCaptor<HealthRecord> captor = ArgumentCaptor.forClass(HealthRecord.class);
        org.mockito.Mockito.verify(healthRecordRepository).save(captor.capture());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(captor.getValue().getBmi()).isEqualTo(12.0);
    }
}

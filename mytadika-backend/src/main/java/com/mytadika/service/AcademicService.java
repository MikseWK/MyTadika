package com.mytadika.service;

import com.mytadika.dto.AcademicRecordRequestDTO;
import com.mytadika.dto.AcademicRecordResponseDTO;
import com.mytadika.dto.ScoreItemRequestDTO;
import com.mytadika.exception.ResourceNotFoundException;
import com.mytadika.exception.UnauthorizedAccessException;
import com.mytadika.model.Account;
import com.mytadika.model.AcademicRecord;
import com.mytadika.model.AcademicScoreItem;
import com.mytadika.model.Role;
import com.mytadika.model.Student;
import com.mytadika.repository.AcademicRecordRepository;
import com.mytadika.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AcademicService {

    private final AcademicRecordRepository academicRecordRepository;
    private final StudentRepository studentRepository;
    private final GradeCalculationService gradeCalculationService;

    public AcademicService(AcademicRecordRepository academicRecordRepository,
                            StudentRepository studentRepository,
                            GradeCalculationService gradeCalculationService) {
        this.academicRecordRepository = academicRecordRepository;
        this.studentRepository = studentRepository;
        this.gradeCalculationService = gradeCalculationService;
    }

    public List<AcademicRecordResponseDTO> listForStudent(Long studentId, Account currentUser) {
        Student student = requireStudent(studentId);
        assertCanAccess(student, currentUser);
        return academicRecordRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream().map(this::toResponse).toList();
    }

    public AcademicRecordResponseDTO getRecordScoped(Long recordId, Account currentUser) {
        AcademicRecord record = requireRecord(recordId);
        assertCanAccess(record.getStudent(), currentUser);
        return toResponse(record);
    }

    public AcademicRecordResponseDTO createRecord(Long studentId, AcademicRecordRequestDTO request) {
        Student student = requireStudent(studentId);

        AcademicRecord record = AcademicRecord.builder()
                .student(student)
                .academicTerm(request.getAcademicTerm())
                .build();
        applyScores(record, request.getScores());

        return toResponse(academicRecordRepository.save(record));
    }

    @Transactional
    public AcademicRecordResponseDTO updateRecord(Long recordId, AcademicRecordRequestDTO request) {
        AcademicRecord record = requireRecord(recordId);
        record.setAcademicTerm(request.getAcademicTerm());
        record.getScoreItems().clear();
        applyScores(record, request.getScores());

        return toResponse(academicRecordRepository.save(record));
    }

    private void applyScores(AcademicRecord record, List<ScoreItemRequestDTO> scores) {
        List<AcademicScoreItem> items = scores.stream()
                .map(s -> AcademicScoreItem.builder()
                        .academicRecord(record)
                        .subjectName(s.getSubjectName())
                        .score(s.getScore())
                        .build())
                .toList();
        record.getScoreItems().addAll(items);

        double average = gradeCalculationService.calculateAverage(
                scores.stream().map(ScoreItemRequestDTO::getScore).toList());
        record.setAverageMark(average);
        record.setFinalGrade(gradeCalculationService.calculateGrade(average));
    }

    private AcademicRecordResponseDTO toResponse(AcademicRecord record) {
        return AcademicRecordResponseDTO.from(record, gradeCalculationService.gradeLabel(record.getFinalGrade()));
    }

    private Student requireStudent(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
        if (student.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Student not found.");
        }
        return student;
    }

    private AcademicRecord requireRecord(Long recordId) {
        return academicRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic record not found."));
    }

    private void assertCanAccess(Student student, Account currentUser) {
        if (currentUser.getRole() == Role.PARENT && !student.getParent().getAccountId().equals(currentUser.getAccountId())) {
            throw new UnauthorizedAccessException("Cannot access another parent's child.");
        }
    }
}

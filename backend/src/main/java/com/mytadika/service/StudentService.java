package com.mytadika.service;

import com.mytadika.dto.StudentCreateRequestDTO;
import com.mytadika.dto.StudentResponseDTO;
import com.mytadika.dto.StudentUpdateRequestDTO;
import com.mytadika.exception.InvalidInputException;
import com.mytadika.exception.ResourceNotFoundException;
import com.mytadika.exception.UnauthorizedAccessException;
import com.mytadika.model.*;
import com.mytadika.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final AccountRepository accountRepository;
    private final ClassroomRepository classroomRepository;
    private final StudentClassroomRepository studentClassroomRepository;
    private final AssignmentRepository assignmentRepository;
    private final ClassworkCompletionRepository completionRepository;

    public StudentService(StudentRepository studentRepository,
                           AccountRepository accountRepository,
                           ClassroomRepository classroomRepository,
                           StudentClassroomRepository studentClassroomRepository,
                           AssignmentRepository assignmentRepository,
                           ClassworkCompletionRepository completionRepository) {
        this.studentRepository = studentRepository;
        this.accountRepository = accountRepository;
        this.classroomRepository = classroomRepository;
        this.studentClassroomRepository = studentClassroomRepository;
        this.assignmentRepository = assignmentRepository;
        this.completionRepository = completionRepository;
    }

    public List<StudentResponseDTO> listAll(Long classroomId) {
        List<Student> students = classroomId != null
                ? studentRepository.findByClassroomIdAndDeletedAtIsNull(classroomId)
                : studentRepository.findByDeletedAtIsNull();
        return students.stream().map(StudentResponseDTO::from).toList();
    }

    public List<StudentResponseDTO> listMyChildren(Account currentUser) {
        return studentRepository.findByParentIdAndDeletedAtIsNull(currentUser.getAccountId())
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

    // Returns each child with their full list of classrooms.
    // Batches all lookups by ID set instead of looping per-student/per-classroom —
    // each individual findById in a loop is a separate round-trip to the remote DB,
    // which added seconds of latency once a parent had more than one or two children.
    public List<Map<String, Object>> getStudentsByParent(String parentId) {
        List<Student> students = studentRepository.findByParentIdAndDeletedAtIsNull(parentId);
        if (students.isEmpty()) return new ArrayList<>();

        List<Long> studentIds = students.stream().map(Student::getId).collect(Collectors.toList());
        String today = LocalDate.now().toString();

        List<StudentClassroom> links = studentClassroomRepository.findByStudentIdIn(studentIds);
        List<Long> classroomIds = links.stream().map(StudentClassroom::getClassroomId).distinct().collect(Collectors.toList());

        Map<Long, Classroom> classroomsById = classroomRepository.findAllById(classroomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, c -> c));

        List<String> teacherIds = classroomsById.values().stream()
                .map(Classroom::getTeacherAccountId).distinct().collect(Collectors.toList());
        Map<String, Account> teachersById = accountRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));

        Map<Long, Set<Long>> completedIdsByStudent = new HashMap<>();
        for (ClassworkCompletion cc : completionRepository.findByStudentIdIn(studentIds))
            completedIdsByStudent.computeIfAbsent(cc.getStudentId(), k -> new HashSet<>()).add(cc.getAssignmentId());

        Map<Long, List<Assignment>> upcomingByClassroom = new HashMap<>();
        for (Assignment a : assignmentRepository.findByClassroomIdInAndDueDateGreaterThanEqualOrderByDueDateAsc(classroomIds, today))
            upcomingByClassroom.computeIfAbsent(a.getClassroomId(), k -> new ArrayList<>()).add(a);

        Map<Long, List<StudentClassroom>> linksByStudent = new HashMap<>();
        for (StudentClassroom sc : links)
            linksByStudent.computeIfAbsent(sc.getStudentId(), k -> new ArrayList<>()).add(sc);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("fullName", s.getFullName());
            map.put("studentCode", s.getStudentCode());
            map.put("gender", s.getGender());
            map.put("dateOfBirth", s.getDateOfBirth());

            Set<Long> completedIds = completedIdsByStudent.getOrDefault(s.getId(), Collections.emptySet());
            List<Map<String, Object>> classrooms = new ArrayList<>();
            for (StudentClassroom sc : linksByStudent.getOrDefault(s.getId(), Collections.emptyList())) {
                Classroom c = classroomsById.get(sc.getClassroomId());
                if (c == null) continue;

                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("id", c.getId());
                cm.put("name", c.getName());
                cm.put("section", c.getSection());
                cm.put("color", c.getColor());
                cm.put("classCode", c.getClassCode());
                Account t = teachersById.get(c.getTeacherAccountId());
                if (t != null) {
                    cm.put("teacherName", t.getFullName());
                    cm.put("teacherImage", t.getProfileImageUrl());
                }

                List<Assignment> upcoming = upcomingByClassroom.getOrDefault(c.getId(), Collections.emptyList())
                        .stream().filter(a -> !completedIds.contains(a.getId()))
                        .collect(Collectors.toList());
                List<Map<String, Object>> upcomingWork = new ArrayList<>();
                for (int i = 0; i < Math.min(3, upcoming.size()); i++) {
                    Assignment a = upcoming.get(i);
                    if (a.getDueDate() == null || a.getDueDate().isBlank()) continue;
                    Map<String, Object> aw = new LinkedHashMap<>();
                    aw.put("id", a.getId());
                    aw.put("title", a.getTitle());
                    aw.put("dueDate", a.getDueDate());
                    upcomingWork.add(aw);
                }
                cm.put("upcomingWork", upcomingWork);
                cm.put("totalUpcoming", upcoming.size());
                classrooms.add(cm);
            }
            map.put("classrooms", classrooms);
            result.add(map);
        }
        return result;
    }

    // Parent joins a classroom by class code — inserts into student_classrooms
    @Transactional
    public void joinClassroomByCode(Long studentId, String classCode) {
        if (classCode == null || classCode.isBlank())
            throw new IllegalArgumentException("Class code is required.");
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        Classroom classroom = classroomRepository.findByClassCode(classCode.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No classroom found with that code. Please check and try again."));
        if (studentClassroomRepository.existsByStudentIdAndClassroomId(student.getId(), classroom.getId()))
            throw new IllegalArgumentException("Student is already enrolled in this classroom.");
        if (!studentClassroomRepository.findByStudentId(student.getId()).isEmpty())
            throw new IllegalArgumentException("This student is already enrolled in a classroom. A student can only join one classroom at a time — leave the current class first.");
        studentClassroomRepository.save(StudentClassroom.builder()
                .studentId(student.getId())
                .classroomId(classroom.getId())
                .build());
    }

    // Remove student from a specific classroom
    @Transactional
    public void removeFromClassroom(Long studentId, Long classroomId) {
        studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        studentClassroomRepository.deleteByStudentIdAndClassroomId(studentId, classroomId);
    }
}

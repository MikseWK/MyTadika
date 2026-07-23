package com.mytadika.service;
import com.mytadika.model.*;
import com.mytadika.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class StudentService {

    private final StudentRepository studentRepo;
    private final ClassroomRepository classroomRepo;
    private final AccountRepository accountRepo;
    private final StudentClassroomRepository studentClassroomRepo;
    private final AssignmentRepository assignmentRepo;
    private final ClassworkCompletionRepository completionRepo;

    public StudentService(StudentRepository studentRepo, ClassroomRepository classroomRepo,
                          AccountRepository accountRepo, StudentClassroomRepository studentClassroomRepo,
                          AssignmentRepository assignmentRepo, ClassworkCompletionRepository completionRepo) {
        this.studentRepo = studentRepo;
        this.classroomRepo = classroomRepo;
        this.accountRepo = accountRepo;
        this.studentClassroomRepo = studentClassroomRepo;
        this.assignmentRepo = assignmentRepo;
        this.completionRepo = completionRepo;
    }

    // Returns each child with their full list of classrooms.
    // Batches all lookups by ID set instead of looping per-student/per-classroom —
    // each individual findById in a loop is a separate round-trip to the remote DB,
    // which added seconds of latency once a parent had more than one or two children.
    public List<Map<String, Object>> getStudentsByParent(String parentId) {
        List<Student> students = studentRepo.findByParentIdAndDeletedAtIsNull(parentId);
        if (students.isEmpty()) return new ArrayList<>();

        List<Long> studentIds = students.stream().map(Student::getId).collect(java.util.stream.Collectors.toList());
        String today = LocalDate.now().toString();

        List<StudentClassroom> links = studentClassroomRepo.findByStudentIdIn(studentIds);
        List<Long> classroomIds = links.stream().map(StudentClassroom::getClassroomId).distinct().collect(java.util.stream.Collectors.toList());

        Map<Long, Classroom> classroomsById = classroomRepo.findAllById(classroomIds).stream()
                .collect(java.util.stream.Collectors.toMap(Classroom::getId, c -> c));

        List<String> teacherIds = classroomsById.values().stream()
                .map(Classroom::getTeacherAccountId).distinct().collect(java.util.stream.Collectors.toList());
        Map<String, Account> teachersById = accountRepo.findAllById(teacherIds).stream()
                .collect(java.util.stream.Collectors.toMap(Account::getAccountId, a -> a));

        Map<Long, Set<Long>> completedIdsByStudent = new HashMap<>();
        for (ClassworkCompletion cc : completionRepo.findByStudentIdIn(studentIds))
            completedIdsByStudent.computeIfAbsent(cc.getStudentId(), k -> new HashSet<>()).add(cc.getAssignmentId());

        Map<Long, List<Assignment>> upcomingByClassroom = new HashMap<>();
        for (Assignment a : assignmentRepo.findByClassroomIdInAndDueDateGreaterThanEqualOrderByDueDateAsc(classroomIds, today))
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
                    cm.put("teacherAccountId", t.getAccountId());
                    cm.put("teacherName", t.getFullName());
                    cm.put("teacherImage", t.getProfileImageUrl());
                    cm.put("teacherEmail", t.getEmail());
                    cm.put("teacherPhone", t.getPhoneNumber());
                    cm.put("teacherQualification", t.getQualification());
                    cm.put("teacherExperience", t.getExperience());
                    cm.put("teacherFocusArea", t.getFocusArea());
                    cm.put("teacherDescription", t.getDescription());
                }

                List<Assignment> upcoming = upcomingByClassroom.getOrDefault(c.getId(), Collections.emptyList())
                        .stream().filter(a -> !completedIds.contains(a.getId()))
                        .collect(java.util.stream.Collectors.toList());
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
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        Classroom classroom = classroomRepo.findByClassCode(classCode.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No classroom found with that code. Please check and try again."));
        if (studentClassroomRepo.existsByStudentIdAndClassroomId(student.getId(), classroom.getId()))
            throw new IllegalArgumentException("Student is already enrolled in this classroom.");
        if (!studentClassroomRepo.findByStudentId(student.getId()).isEmpty())
            throw new IllegalArgumentException("This student is already enrolled in a classroom. A student can only join one classroom at a time — leave the current class first.");
        studentClassroomRepo.save(StudentClassroom.builder()
                .studentId(student.getId())
                .classroomId(classroom.getId())
                .build());
    }

    // Remove student from a specific classroom
    @Transactional
    public void removeFromClassroom(Long studentId, Long classroomId) {
        studentRepo.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        studentClassroomRepo.deleteByStudentIdAndClassroomId(studentId, classroomId);
    }

}

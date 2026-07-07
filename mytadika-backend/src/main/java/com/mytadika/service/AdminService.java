package com.mytadika.service;

import com.mytadika.model.*;
import com.mytadika.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final AccountRepository accountRepo;
    private final StudentRepository studentRepo;
    private final ClassroomRepository classroomRepo;
    private final StudentClassroomRepository studentClassroomRepo;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AccountRepository accountRepo, StudentRepository studentRepo,
                        ClassroomRepository classroomRepo, StudentClassroomRepository studentClassroomRepo,
                        PasswordEncoder passwordEncoder) {
        this.accountRepo = accountRepo;
        this.studentRepo = studentRepo;
        this.classroomRepo = classroomRepo;
        this.studentClassroomRepo = studentClassroomRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Dashboard Stats ─────────────────────────────────────────────────────
    public Map<String, Object> getDashboardStats() {
        long teachers = accountRepo.findByRoleType(Account.RoleType.TEACHER).size();
        long parents  = accountRepo.findByRoleType(Account.RoleType.PARENT).size();
        long admins   = accountRepo.findByRoleType(Account.RoleType.ADMIN).size();
        long students  = studentRepo.findAll().stream().filter(s -> s.getDeletedAt() == null).count();
        long classrooms = classroomRepo.findAll().size();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAccounts", teachers + parents + admins);
        stats.put("teachers", teachers);
        stats.put("parents", parents);
        stats.put("admins", admins);
        stats.put("students", students);
        stats.put("classrooms", classrooms);
        return stats;
    }

    // ── Account Management ──────────────────────────────────────────────────
    public List<Map<String, Object>> listAccounts(String roleFilter) {
        List<Account> accounts;
        if (roleFilter != null && !roleFilter.isBlank() && !roleFilter.equalsIgnoreCase("ALL")) {
            try { accounts = accountRepo.findByRoleType(Account.RoleType.valueOf(roleFilter.toUpperCase())); }
            catch (IllegalArgumentException e) { accounts = accountRepo.findAll(); }
        } else {
            accounts = accountRepo.findAll();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Account a : accounts) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("accountId", a.getAccountId());
            map.put("fullName", a.getFullName());
            map.put("email", a.getEmail());
            map.put("roleType", a.getRoleType().name());
            map.put("phoneNumber", a.getPhoneNumber());
            map.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toLocalDate().toString() : null);
            map.put("profileImageUrl", a.getProfileImageUrl());
            if (a.getRoleType() == Account.RoleType.PARENT) {
                map.put("childCount", studentRepo.findByParentIdAndDeletedAtIsNull(a.getAccountId()).size());
            }
            result.add(map);
        }
        result.sort(Comparator.comparing(m -> String.valueOf(m.get("fullName"))));
        return result;
    }

    @Transactional
    public void updateAccountRole(String accountId, String role) {
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        try { account.setRoleType(Account.RoleType.valueOf(role.toUpperCase())); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid role: " + role); }
        accountRepo.save(account);
    }

    @Transactional
    public void updateAccount(String accountId, Map<String, String> body) {
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (body.get("fullName") != null && !body.get("fullName").isBlank())
            account.setFullName(body.get("fullName").trim());
        if (body.get("email") != null && !body.get("email").isBlank()) {
            String newEmail = body.get("email").trim().toLowerCase();
            if (!newEmail.equals(account.getEmail()) && accountRepo.existsByEmail(newEmail))
                throw new IllegalArgumentException("An account with this email already exists.");
            account.setEmail(newEmail);
        }
        if (body.containsKey("phoneNumber")) account.setPhoneNumber(body.get("phoneNumber"));
        if (body.get("role") != null && !body.get("role").isBlank()) {
            try { account.setRoleType(Account.RoleType.valueOf(body.get("role").toUpperCase())); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid role: " + body.get("role")); }
        }
        accountRepo.save(account);
    }

    @Transactional
    public void deleteAccount(String accountId) {
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (account.getRoleType() == Account.RoleType.ADMIN
                && accountRepo.findByRoleType(Account.RoleType.ADMIN).size() <= 1) {
            throw new IllegalArgumentException("Cannot delete the last remaining admin account.");
        }
        accountRepo.deleteById(accountId);
    }

    @Transactional
    public Map<String, Object> createAccount(Map<String, String> body) {
        String email    = body.get("email");
        String fullName = body.get("fullName");
        String password = body.get("password");
        String role     = body.get("role");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required.");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Full name is required.");
        if (password == null || password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (role == null) throw new IllegalArgumentException("Role is required.");
        Account.RoleType roleType;
        try { roleType = Account.RoleType.valueOf(role.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid role."); }
        if (accountRepo.existsByEmail(email.trim().toLowerCase()))
            throw new IllegalArgumentException("An account with this email already exists.");
        String accountId = UUID.randomUUID().toString().replace("-", "").substring(0, 28);
        Account account = Account.builder()
                .accountId(accountId).fullName(fullName.trim())
                .email(email.trim().toLowerCase()).password(passwordEncoder.encode(password))
                .roleType(roleType).createdAt(LocalDateTime.now()).build();
        accountRepo.save(account);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", account.getAccountId());
        result.put("fullName", account.getFullName());
        result.put("email", account.getEmail());
        result.put("roleType", account.getRoleType().name());
        return result;
    }

    // ── Student Management ──────────────────────────────────────────────────
    public List<Map<String, Object>> listStudents(String search) {
        List<Student> students = studentRepo.findAll().stream()
                .filter(s -> s.getDeletedAt() == null).collect(Collectors.toList());
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            students = students.stream()
                    .filter(s -> (s.getFullName() != null && s.getFullName().toLowerCase().contains(q))
                            || (s.getStudentCode() != null && s.getStudentCode().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }
        // Classroom membership actually lives in the student_classrooms join table (a student
        // can be in more than one class) — Student.classroomId is a legacy column that's never
        // populated. Batch-fetch links + classrooms instead of looping per-student lookups.
        List<Long> studentIds = students.stream().map(Student::getId).collect(Collectors.toList());
        List<StudentClassroom> links = studentClassroomRepo.findByStudentIdIn(studentIds);
        Map<Long, Classroom> classroomsById = classroomRepo.findAllById(
                links.stream().map(StudentClassroom::getClassroomId).distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Classroom::getId, c -> c));
        Map<Long, List<Classroom>> classroomsByStudent = new HashMap<>();
        for (StudentClassroom link : links) {
            Classroom c = classroomsById.get(link.getClassroomId());
            if (c != null) classroomsByStudent.computeIfAbsent(link.getStudentId(), k -> new ArrayList<>()).add(c);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("studentCode", s.getStudentCode());
            map.put("fullName", s.getFullName());
            map.put("dateOfBirth", s.getDateOfBirth());
            map.put("gender", s.getGender());
            map.put("medicalInfo", s.getMedicalInfo());
            map.put("emergencyContact", s.getEmergencyContact());
            map.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toLocalDate().toString() : null);
            if (s.getParentId() != null) {
                accountRepo.findById(s.getParentId()).ifPresent(p -> {
                    map.put("parentId", p.getAccountId());
                    map.put("parentName", p.getFullName());
                    map.put("parentEmail", p.getEmail());
                });
            }
            List<Classroom> studentClassrooms = classroomsByStudent.getOrDefault(s.getId(), Collections.emptyList());
            if (!studentClassrooms.isEmpty()) {
                map.put("classroomId", studentClassrooms.get(0).getId());
                map.put("classroomName", studentClassrooms.stream().map(Classroom::getName).collect(Collectors.joining(", ")));
                map.put("classroomIds", studentClassrooms.stream().map(Classroom::getId).collect(Collectors.toList()));
            }
            result.add(map);
        }
        result.sort(Comparator.comparing(m -> String.valueOf(m.get("fullName"))));
        return result;
    }

    @Transactional
    public Map<String, Object> createStudent(Map<String, String> body) {
        String fullName = body.get("fullName");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Full name is required.");
        String studentCode = generateStudentCode();
        Student student = Student.builder()
                .fullName(fullName.trim())
                .dateOfBirth(body.get("dateOfBirth"))
                .gender(body.get("gender"))
                .medicalInfo(body.get("medicalInfo"))
                .emergencyContact(body.get("emergencyContact"))
                .studentCode(studentCode)
                .createdAt(LocalDateTime.now())
                .build();
        if (body.get("parentEmail") != null && !body.get("parentEmail").isBlank()) {
            accountRepo.findByEmail(body.get("parentEmail").trim().toLowerCase())
                    .ifPresent(p -> student.setParentId(p.getAccountId()));
        }
        studentRepo.save(student);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", student.getId());
        result.put("studentCode", student.getStudentCode());
        result.put("fullName", student.getFullName());
        return result;
    }

    @Transactional
    public void updateStudent(Long id, Map<String, String> body) {
        Student student = studentRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        if (body.containsKey("fullName") && body.get("fullName") != null && !body.get("fullName").isBlank())
            student.setFullName(body.get("fullName").trim());
        if (body.containsKey("dateOfBirth")) student.setDateOfBirth(body.get("dateOfBirth"));
        if (body.containsKey("gender")) student.setGender(body.get("gender"));
        if (body.containsKey("medicalInfo")) student.setMedicalInfo(body.get("medicalInfo"));
        if (body.containsKey("emergencyContact")) student.setEmergencyContact(body.get("emergencyContact"));
        if (body.containsKey("parentEmail")) {
            String pe = body.get("parentEmail");
            if (pe != null && !pe.isBlank()) {
                accountRepo.findByEmail(pe.trim().toLowerCase())
                        .ifPresent(p -> student.setParentId(p.getAccountId()));
            } else {
                student.setParentId(null);
            }
        }
        studentRepo.save(student);
    }

    @Transactional
    public void deleteStudent(Long id) {
        Student student = studentRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        student.setDeletedAt(LocalDateTime.now());
        studentRepo.save(student);
    }

    private String generateStudentCode() {
        int max = studentRepo.findAll().stream()
                .filter(s -> s.getStudentCode() != null && s.getStudentCode().matches("STU\\d+"))
                .mapToInt(s -> { try { return Integer.parseInt(s.getStudentCode().substring(3)); } catch (Exception e) { return 0; } })
                .max().orElse(20000);
        return "STU" + (max + 1);
    }
}

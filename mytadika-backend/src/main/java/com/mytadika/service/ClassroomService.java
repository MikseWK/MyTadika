package com.mytadika.service;
import com.mytadika.model.*;
import com.mytadika.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class ClassroomService {

    private final ClassroomRepository classroomRepo;
    private final ClassMemberRepository memberRepo;
    private final AnnouncementRepository announcementRepo;
    private final AssignmentRepository assignmentRepo;
    private final AccountRepository accountRepo;
    private final StudentRepository studentRepo;
    private final StudentClassroomRepository studentClassroomRepo;
    private final ClassworkCompletionRepository completionRepo;
    private final AnnouncementViewRepository announcementViewRepo;
    private final NotificationService notifService;
    private final MemoryPostRepository memoryPostRepo;
    private final MemoryImageRepository memoryImageRepo;

    public ClassroomService(ClassroomRepository classroomRepo, ClassMemberRepository memberRepo,
                            AnnouncementRepository announcementRepo, AssignmentRepository assignmentRepo,
                            AccountRepository accountRepo, StudentRepository studentRepo,
                            StudentClassroomRepository studentClassroomRepo,
                            ClassworkCompletionRepository completionRepo,
                            AnnouncementViewRepository announcementViewRepo,
                            NotificationService notifService,
                            MemoryPostRepository memoryPostRepo,
                            MemoryImageRepository memoryImageRepo) {
        this.classroomRepo = classroomRepo;
        this.memberRepo = memberRepo;
        this.announcementRepo = announcementRepo;
        this.assignmentRepo = assignmentRepo;
        this.accountRepo = accountRepo;
        this.studentRepo = studentRepo;
        this.studentClassroomRepo = studentClassroomRepo;
        this.completionRepo = completionRepo;
        this.announcementViewRepo = announcementViewRepo;
        this.notifService = notifService;
        this.memoryPostRepo = memoryPostRepo;
        this.memoryImageRepo = memoryImageRepo;
    }

    // All classrooms school-wide — used by admin pickers (bulk-assign fees) and the
    // admin classroom management page. Batches teacher lookups instead of one per classroom.
    public List<Map<String, Object>> getAllClassroomsBasic() {
        List<Classroom> classrooms = classroomRepo.findAll();
        List<String> teacherIds = classrooms.stream().map(Classroom::getTeacherAccountId).distinct().collect(Collectors.toList());
        Map<String, Account> teachersById = accountRepo.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Classroom c : classrooms) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getName());
            map.put("section", c.getSection());
            map.put("color", c.getColor());
            map.put("classCode", c.getClassCode());
            map.put("studentCount", studentClassroomRepo.countByClassroomId(c.getId()));
            map.put("teacherAccountId", c.getTeacherAccountId());
            Account teacher = teachersById.get(c.getTeacherAccountId());
            if (teacher != null) {
                map.put("teacherName", teacher.getFullName());
                map.put("teacherEmail", teacher.getEmail());
                map.put("teacherImage", teacher.getProfileImageUrl());
            }
            result.add(map);
        }
        result.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));
        return result;
    }

    // Get classrooms for a member (by accountId) — includes classrooms where they are teacher OR co-teacher
    public List<Map<String, Object>> getMyClassrooms(String accountId) {
        List<ClassMember> memberships = memberRepo.findByAccountId(accountId);
        Set<Long> classroomIds = new HashSet<>();
        memberships.forEach(m -> classroomIds.add(m.getClassroomId()));
        classroomRepo.findByTeacherAccountId(accountId).forEach(c -> classroomIds.add(c.getId()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long cid : classroomIds) {
            classroomRepo.findById(cid).ifPresent(c -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", c.getId());
                map.put("name", c.getName());
                map.put("section", c.getSection());
                map.put("description", c.getDescription());
                map.put("color", c.getColor());
                map.put("classCode", c.getClassCode());
                accountRepo.findById(c.getTeacherAccountId()).ifPresent(t -> {
                    map.put("teacherName", t.getFullName());
                    map.put("teacherImage", t.getProfileImageUrl());
                });
                map.put("studentCount", studentClassroomRepo.countByClassroomId(c.getId()));

                String today = LocalDate.now().toString(); // YYYY-MM-DD
                List<Assignment> upcoming = assignmentRepo
                        .findByClassroomIdAndDueDateGreaterThanEqualOrderByDueDateAsc(c.getId(), today);
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
                map.put("upcomingWork", upcomingWork);
                map.put("totalUpcoming", upcoming.size());
                result.add(map);
            });
        }
        result.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));
        return result;
    }

    public Optional<Classroom> getClassroomById(Long id) {
        return classroomRepo.findById(id);
    }

    // Every student across every classroom this teacher owns or co-teaches — used by the
    // Health/Academic "all my students" list pages so a teacher isn't limited to browsing
    // one classroom's People tab at a time. Batched to avoid a query per student.
    public List<Map<String, Object>> getMyStudents(String accountId) {
        Set<Long> classroomIds = new HashSet<>();
        memberRepo.findByAccountId(accountId).forEach(m -> classroomIds.add(m.getClassroomId()));
        classroomRepo.findByTeacherAccountId(accountId).forEach(c -> classroomIds.add(c.getId()));
        if (classroomIds.isEmpty()) return new ArrayList<>();

        Map<Long, Classroom> classroomsById = classroomRepo.findAllById(classroomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, c -> c));
        List<StudentClassroom> links = studentClassroomRepo.findByClassroomIdIn(new ArrayList<>(classroomIds));

        List<Long> studentIds = links.stream().map(StudentClassroom::getStudentId).distinct().collect(Collectors.toList());
        Map<Long, Student> studentsById = studentRepo.findAllById(studentIds).stream()
                .filter(s -> s.getDeletedAt() == null)
                .collect(Collectors.toMap(Student::getId, s -> s));

        List<Map<String, Object>> result = new ArrayList<>();
        for (StudentClassroom link : links) {
            Student s = studentsById.get(link.getStudentId());
            Classroom c = classroomsById.get(link.getClassroomId());
            if (s == null || c == null) continue;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("studentId", s.getId());
            map.put("studentName", s.getFullName());
            map.put("studentCode", s.getStudentCode());
            map.put("gender", s.getGender());
            map.put("dateOfBirth", s.getDateOfBirth());
            map.put("classroomId", c.getId());
            map.put("classroomName", c.getName());
            result.add(map);
        }
        result.sort(Comparator.comparing(m -> String.valueOf(m.get("studentName"))));
        return result;
    }

    // Stream: announcements with author info, pinned first
    public List<Map<String, Object>> getStream(Long classroomId) {
        List<Announcement> announcements = announcementRepo.findByClassroomIdOrderByCreatedAtDesc(classroomId);
        // Sort: pinned first, then by date desc
        announcements.sort((a, b) -> {
            boolean ap = Boolean.TRUE.equals(a.getPinned());
            boolean bp = Boolean.TRUE.equals(b.getPinned());
            if (ap != bp) return ap ? -1 : 1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");
        for (Announcement a : announcements) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", a.getId());
            map.put("title", a.getTitle());
            map.put("content", a.getContent());
            map.put("topic", a.getTopic());
            map.put("pinned", Boolean.TRUE.equals(a.getPinned()));
            map.put("imageUrl", a.getImageUrl());
            map.put("createdAt", a.getCreatedAt().format(fmt));
            map.put("seenCount", announcementViewRepo.countByAnnouncementId(a.getId()));
            accountRepo.findById(a.getAuthorAccountId()).ifPresent(author -> {
                map.put("authorName", author.getFullName());
                map.put("authorImage", author.getProfileImageUrl());
            });
            result.add(map);
        }
        return result;
    }

    @Transactional
    public void togglePin(Long announcementId) {
        announcementRepo.findById(announcementId).ifPresent(a -> {
            a.setPinned(!Boolean.TRUE.equals(a.getPinned()));
            announcementRepo.save(a);
        });
    }

    @Transactional
    public void markStreamViewed(Long classroomId, String viewerAccountId) {
        List<Announcement> all = announcementRepo.findByClassroomIdOrderByCreatedAtDesc(classroomId);
        for (Announcement a : all) {
            if (!announcementViewRepo.existsByAnnouncementIdAndViewerAccountId(a.getId(), viewerAccountId)) {
                announcementViewRepo.save(AnnouncementView.builder()
                        .announcementId(a.getId()).viewerAccountId(viewerAccountId)
                        .viewedAt(java.time.LocalDateTime.now()).build());
            }
        }
    }

    // Classwork: assignments
    // studentId != null → parent view: include completion status per student
    // studentId == null → teacher view: include completion count + total students
    public List<Map<String, Object>> getClasswork(Long classroomId, Long studentId) {
        List<Assignment> assignments = assignmentRepo.findByClassroomIdOrderByCreatedAtAsc(classroomId);
        Set<Long> done = studentId != null ? completionRepo.findCompletedAssignmentIdsByStudentId(studentId) : Set.of();
        int totalStudents = studentId == null ? studentClassroomRepo.countByClassroomId(classroomId) : 0;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Assignment a : assignments) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", a.getId());
            map.put("title", a.getTitle());
            map.put("description", a.getDescription());
            map.put("dueDate", a.getDueDate());
            map.put("points", a.getPoints());
            map.put("topic", a.getTopic());
            if (studentId != null) {
                map.put("completed", done.contains(a.getId()));
            } else {
                map.put("completionCount", completionRepo.countByAssignmentId(a.getId()));
                map.put("totalStudents", totalStudents);
            }
            result.add(map);
        }
        return result;
    }

    @Transactional
    public void markDone(Long assignmentId, Long studentId, String markedBy) {
        if (!completionRepo.existsByAssignmentIdAndStudentId(assignmentId, studentId)) {
            completionRepo.save(ClassworkCompletion.builder()
                    .assignmentId(assignmentId).studentId(studentId).markedBy(markedBy)
                    .markedAt(java.time.LocalDateTime.now()).build());
        }
    }

    @Transactional
    public void unmarkDone(Long assignmentId, Long studentId) {
        completionRepo.deleteByAssignmentIdAndStudentId(assignmentId, studentId);
    }

    // People: teachers + students via student_classrooms join table
    public Map<String, Object> getPeople(Long classroomId) {
        Map<String, Object> result = new LinkedHashMap<>();
        classroomRepo.findById(classroomId).ifPresent(classroom -> {
            List<Map<String, Object>> teachers = new ArrayList<>();
            accountRepo.findById(classroom.getTeacherAccountId()).ifPresent(t -> {
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("accountId", t.getAccountId());
                tm.put("name", t.getFullName());
                tm.put("image", t.getProfileImageUrl());
                tm.put("isOwner", true);
                tm.put("email", t.getEmail());
                tm.put("phoneNumber", t.getPhoneNumber());
                tm.put("description", t.getDescription());
                tm.put("qualification", t.getQualification());
                tm.put("experience", t.getExperience());
                tm.put("focusArea", t.getFocusArea());
                teachers.add(tm);
            });
            memberRepo.findByClassroomIdAndRole(classroomId, "teacher").forEach(m -> {
                if (!m.getAccountId().equals(classroom.getTeacherAccountId())) {
                    accountRepo.findById(m.getAccountId()).ifPresent(t -> {
                        Map<String, Object> tm = new LinkedHashMap<>();
                        tm.put("accountId", t.getAccountId());
                        tm.put("name", t.getFullName());
                        tm.put("image", t.getProfileImageUrl());
                        tm.put("isOwner", false);
                        tm.put("email", t.getEmail());
                        tm.put("phoneNumber", t.getPhoneNumber());
                        tm.put("description", t.getDescription());
                        tm.put("qualification", t.getQualification());
                        tm.put("experience", t.getExperience());
                        tm.put("focusArea", t.getFocusArea());
                                teachers.add(tm);
                    });
                }
            });
            result.put("teachers", teachers);

            List<Map<String, Object>> students = new ArrayList<>();
            studentClassroomRepo.findByClassroomId(classroomId).forEach(sc ->
                studentRepo.findById(sc.getStudentId())
                    .filter(s -> s.getDeletedAt() == null)
                    .ifPresent(s -> {
                        Map<String, Object> sm = new LinkedHashMap<>();
                        sm.put("studentId", s.getId());
                        sm.put("studentName", s.getFullName());
                        sm.put("studentCode", s.getStudentCode());
                        sm.put("gender", s.getGender());
                        if (s.getParentId() != null) {
                            accountRepo.findById(s.getParentId()).ifPresent(p -> {
                                sm.put("parentName", p.getFullName());
                                sm.put("image", p.getProfileImageUrl());
                            });
                        }
                        students.add(sm);
                    })
            );
            result.put("students", students);
        });
        return result;
    }

    // Create classroom
    public Classroom createClassroom(Map<String, String> body) {
        if (body.get("name") == null || body.get("name").isBlank()) throw new IllegalArgumentException("Name required");
        if (body.get("teacherAccountId") == null || body.get("teacherAccountId").isBlank()) throw new IllegalArgumentException("Teacher required");
        String code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Classroom c = Classroom.builder()
                .name(body.get("name").trim())
                .section(body.get("section"))
                .description(body.get("description"))
                .teacherAccountId(body.get("teacherAccountId"))
                .color(body.getOrDefault("color", "indigo"))
                .classCode(code)
                .build();
        return classroomRepo.save(c);
    }

    public void enrollStudent(Long classroomId, String accountId) {
        if (!memberRepo.existsByClassroomIdAndAccountId(classroomId, accountId)) {
            memberRepo.save(ClassMember.builder().classroomId(classroomId).accountId(accountId).role("student").build());
        }
    }

    // Enroll a student by student code — inserts into student_classrooms join table
    @Transactional
    public void enrollByStudentCode(Long classroomId, String studentCode) {
        if (studentCode == null || studentCode.isBlank())
            throw new IllegalArgumentException("Student code is required.");
        Student student = studentRepo.findByStudentCode(studentCode.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No student found with code \"" + studentCode + "\"."));
        if (student.getDeletedAt() != null)
            throw new IllegalArgumentException("This student account is deactivated.");
        classroomRepo.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found."));
        if (studentClassroomRepo.existsByStudentIdAndClassroomId(student.getId(), classroomId))
            throw new IllegalArgumentException("Student is already in this classroom.");
        if (!studentClassroomRepo.findByStudentId(student.getId()).isEmpty())
            throw new IllegalArgumentException("This student is already enrolled in another classroom. A student can only join one classroom at a time — remove them from their current class first.");
        studentClassroomRepo.save(StudentClassroom.builder()
                .studentId(student.getId())
                .classroomId(classroomId)
                .build());
    }

    // Post an announcement
    public Announcement postAnnouncement(Long classroomId, Map<String, String> body) {
        if (body.get("content") == null || body.get("content").isBlank()) throw new IllegalArgumentException("Content required");
        if (body.get("authorAccountId") == null) throw new IllegalArgumentException("Author required");
        String title = body.get("title") != null && !body.get("title").isBlank() ? body.get("title").trim() : null;
        Announcement saved = announcementRepo.save(Announcement.builder()
                .classroomId(classroomId).authorAccountId(body.get("authorAccountId"))
                .title(title)
                .content(body.get("content").trim())
                .topic(body.get("topic"))
                .imageUrl(body.get("imageUrl"))
                .build());
        try {
            String classroomName = classroomRepo.findById(classroomId).map(Classroom::getName).orElse("classroom");
            String preview = saved.getContent();
            if (preview.length() > 80) preview = preview.substring(0, 80) + "...";
            String notifTitle = title != null
                    ? "New announcement: " + title + " (" + classroomName + ")"
                    : "New announcement in " + classroomName;
            String notifBody = preview;
            notifyParentsInClassroom(classroomId, notifTitle, notifBody,
                    "&classroomId=" + classroomId + "&tab=stream&postId=" + saved.getId());
        } catch (Exception e) {
            log.error("[Notification] Failed for announcement {}: {}", saved.getId(), e.getMessage(), e);
        }
        return saved;
    }

    // Groups students in a classroom by parent, then sends one notification per parent
    // whose title lists every one of their children affected (a parent can have multiple
    // kids in the same classroom, or the announcement/assignment applies to all of them).
    private void notifyParentsInClassroom(Long classroomId, String baseTitle, String notifBody, String linkSuffix) {
        Map<String, List<Student>> byParent = new LinkedHashMap<>();
        studentClassroomRepo.findByClassroomId(classroomId).forEach(sc ->
            studentRepo.findById(sc.getStudentId()).ifPresent(s -> {
                if (s.getParentId() != null) byParent.computeIfAbsent(s.getParentId(), k -> new ArrayList<>()).add(s);
            })
        );
        byParent.forEach((parentId, kids) -> {
            String childNames = kids.stream().map(Student::getFullName).collect(Collectors.joining(", "));
            String link = "/parent/parentclassroom.html?childId=" + kids.get(0).getId() + linkSuffix;
            notifService.create(parentId, baseTitle + " — " + childNames, notifBody, link);
        });
    }

    // Create assignment
    public Assignment createAssignment(Long classroomId, Map<String, String> body) {
        if (body.get("title") == null || body.get("title").isBlank()) throw new IllegalArgumentException("Title required");
        if (body.get("points") != null && !body.get("points").isBlank()) {
            int points = Integer.parseInt(body.get("points"));
            if (points < 0 || points > 100) throw new IllegalArgumentException("Points must be between 0 and 100.");
        }
        Assignment saved = assignmentRepo.save(Assignment.builder()
                .classroomId(classroomId).title(body.get("title").trim())
                .description(body.get("description")).dueDate(body.get("dueDate"))
                .topic(body.get("topic"))
                .points(body.get("points") != null ? Integer.parseInt(body.get("points")) : null)
                .build());
        try {
            String classroomName = classroomRepo.findById(classroomId).map(Classroom::getName).orElse("classroom");
            String dueDate = body.get("dueDate");
            String notifBody = (dueDate != null && !dueDate.isBlank()) ? "Due: " + dueDate : null;
            notifyParentsInClassroom(classroomId, "New assignment: " + saved.getTitle() + " (" + classroomName + ")", notifBody,
                    "&classroomId=" + classroomId + "&tab=classwork&postId=" + saved.getId());
        } catch (Exception e) {
            log.error("[Notification] Failed for assignment {}: {}", saved.getId(), e.getMessage(), e);
        }
        return saved;
    }

    public List<Map<String, Object>> getAnnouncementViewers(Long announcementId) {
        List<AnnouncementView> views = announcementViewRepo.findByAnnouncementId(announcementId);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");
        List<Map<String, Object>> result = new ArrayList<>();
        for (AnnouncementView v : views) {
            Map<String, Object> map = new LinkedHashMap<>();
            accountRepo.findById(v.getViewerAccountId()).ifPresent(acc -> {
                map.put("name", acc.getFullName());
                map.put("image", acc.getProfileImageUrl());
            });
            map.put("viewedAt", v.getViewedAt() != null ? v.getViewedAt().format(fmt) : "");
            if (!map.isEmpty()) result.add(map);
        }
        return result;
    }

    public List<Map<String, Object>> getAssignmentCompletions(Long assignmentId) {
        List<ClassworkCompletion> completions = completionRepo.findByAssignmentId(assignmentId);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassworkCompletion c : completions) {
            Map<String, Object> map = new LinkedHashMap<>();
            studentRepo.findById(c.getStudentId()).ifPresent(s -> {
                map.put("studentName", s.getFullName());
                map.put("studentCode", s.getStudentCode());
                map.put("gender", s.getGender() != null ? s.getGender().toString() : null);
            });
            map.put("markedAt", c.getMarkedAt() != null ? c.getMarkedAt().format(fmt) : "");
            if (!map.isEmpty()) result.add(map);
        }
        return result;
    }

    @Transactional
    public void deleteAnnouncement(Long id) {
        announcementViewRepo.deleteByAnnouncementId(id);
        announcementRepo.deleteById(id);
    }
    @Transactional
    public void deleteAssignment(Long id) {
        completionRepo.deleteByAssignmentId(id);
        assignmentRepo.deleteById(id);
    }

    public Classroom updateClassroom(Long id, Map<String, String> body) {
        Classroom c = classroomRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found"));
        if (body.containsKey("name") && body.get("name") != null && !body.get("name").isBlank()) c.setName(body.get("name").trim());
        if (body.containsKey("section")) c.setSection(body.get("section"));
        if (body.containsKey("color") && body.get("color") != null) c.setColor(body.get("color"));
        if (body.containsKey("teacherAccountId") && body.get("teacherAccountId") != null && !body.get("teacherAccountId").isBlank())
            c.setTeacherAccountId(body.get("teacherAccountId"));
        return classroomRepo.save(c);
    }

    @Transactional
    public void deleteClassroom(Long id) {
        memberRepo.deleteByClassroomId(id);
        announcementRepo.deleteByClassroomId(id);
        assignmentRepo.deleteByClassroomId(id);
        studentClassroomRepo.deleteByClassroomId(id);
        memoryPostRepo.findByClassroomIdOrderByCreatedAtDesc(id)
                .forEach(post -> memoryImageRepo.deleteByMemoryPostId(post.getId()));
        memoryPostRepo.deleteByClassroomId(id);
        classroomRepo.deleteById(id);
    }

    // Remove a student from a specific classroom
    @Transactional
    public void removeStudentFromClassroom(Long classroomId, Long studentId) {
        studentClassroomRepo.deleteByStudentIdAndClassroomId(studentId, classroomId);
    }

    // Remove a co-teacher (kept for class_members table)
    @Transactional
    public void removeStudent(Long classroomId, String accountId) {
        memberRepo.deleteByClassroomIdAndAccountId(classroomId, accountId);
    }

    @Transactional
    public Map<String, Object> addTeacher(Long classroomId, String email) {
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email is required.");
        com.mytadika.model.Account account = accountRepo.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("No account found with that email."));
        if (account.getRoleType() != com.mytadika.model.Account.RoleType.TEACHER)
            throw new IllegalArgumentException("That account is not a teacher.");
        Classroom classroom = classroomRepo.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found."));
        if (account.getAccountId().equals(classroom.getTeacherAccountId()))
            throw new IllegalArgumentException("That teacher is already the classroom owner.");
        if (memberRepo.existsByClassroomIdAndAccountId(classroomId, account.getAccountId()))
            throw new IllegalArgumentException("That teacher is already in this classroom.");
        memberRepo.save(ClassMember.builder()
                .classroomId(classroomId)
                .accountId(account.getAccountId())
                .role("teacher")
                .build());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", account.getAccountId());
        result.put("name", account.getFullName());
        result.put("image", account.getProfileImageUrl());
        result.put("isOwner", false);
        return result;
    }

    @Transactional
    public void removeTeacher(Long classroomId, String accountId) {
        Classroom classroom = classroomRepo.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found."));
        if (accountId.equals(classroom.getTeacherAccountId()))
            throw new IllegalArgumentException("Cannot remove the classroom owner.");
        memberRepo.deleteByClassroomIdAndAccountId(classroomId, accountId);
    }
}

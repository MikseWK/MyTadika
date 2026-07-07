package com.mytadika.service;

import com.mytadika.model.*;
import com.mytadika.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MemoryService {

    private final MemoryPostRepository memoryPostRepository;
    private final MemoryImageRepository memoryImageRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final StudentRepository studentRepository;
    private final StudentClassroomRepository studentClassroomRepository;
    private final SupabaseStorageService storageService;
    private final NotificationService notificationService;

    public MemoryService(MemoryPostRepository memoryPostRepository, MemoryImageRepository memoryImageRepository,
                          ClassroomRepository classroomRepository, ClassMemberRepository memberRepository,
                          AccountRepository accountRepository, StudentRepository studentRepository,
                          StudentClassroomRepository studentClassroomRepository,
                          SupabaseStorageService storageService, NotificationService notificationService) {
        this.memoryPostRepository = memoryPostRepository;
        this.memoryImageRepository = memoryImageRepository;
        this.classroomRepository = classroomRepository;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.studentRepository = studentRepository;
        this.studentClassroomRepository = studentClassroomRepository;
        this.storageService = storageService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Map<String, Object> createPost(Long classroomId, String authorAccountId, String caption,
                                           List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("At least one photo is required.");
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found."));

        MemoryPost post = memoryPostRepository.save(MemoryPost.builder()
                .classroomId(classroomId)
                .authorAccountId(authorAccountId)
                .caption(caption != null && !caption.isBlank() ? caption.trim() : null)
                .build());

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String original = file.getOriginalFilename();
            String ext = (original != null && original.contains(".")) ? original.substring(original.lastIndexOf('.')) : ".jpg";
            String filename = "memory_" + UUID.randomUUID() + ext;
            String url = storageService.uploadImage(file.getBytes(), filename, file.getContentType());
            memoryImageRepository.save(MemoryImage.builder().memoryPostId(post.getId()).imageUrl(url).build());
        }

        try {
            notifyParentsInClassroom(classroomId, classroom.getName());
        } catch (Exception ignored) { }

        return toPostMap(post, classroom.getName());
    }

    private void notifyParentsInClassroom(Long classroomId, String classroomName) {
        Map<String, List<Student>> byParent = new LinkedHashMap<>();
        studentClassroomRepository.findByClassroomId(classroomId).forEach(sc ->
                studentRepository.findById(sc.getStudentId()).ifPresent(s -> {
                    if (s.getParentId() != null) byParent.computeIfAbsent(s.getParentId(), k -> new ArrayList<>()).add(s);
                })
        );
        byParent.forEach((parentId, kids) -> {
            String childNames = kids.stream().map(Student::getFullName).collect(Collectors.joining(", "));
            notificationService.create(parentId,
                    "New photos in " + classroomName + " — " + childNames,
                    "Tap to see the latest memories from class.",
                    "/parent/parentmemory.html");
        });
    }

    public List<Map<String, Object>> getForClassroom(Long classroomId) {
        Classroom classroom = classroomRepository.findById(classroomId).orElse(null);
        String name = classroom != null ? classroom.getName() : "classroom";
        return buildFeed(memoryPostRepository.findByClassroomIdOrderByCreatedAtDesc(classroomId),
                Collections.singletonMap(classroomId, name));
    }

    // All memory posts from every classroom the teacher teaches (owner or co-teacher).
    public List<Map<String, Object>> getForTeacher(String teacherAccountId) {
        Set<Long> classroomIds = new LinkedHashSet<>();
        classroomRepository.findByTeacherAccountId(teacherAccountId).forEach(c -> classroomIds.add(c.getId()));
        memberRepository.findByAccountId(teacherAccountId).forEach(m -> classroomIds.add(m.getClassroomId()));
        return getForClassroomIds(new ArrayList<>(classroomIds));
    }

    // All memory posts from every classroom any of this parent's children belong to.
    public List<Map<String, Object>> getForParent(String parentAccountId) {
        List<Student> kids = studentRepository.findByParentId(parentAccountId);
        List<Long> studentIds = kids.stream().map(Student::getId).collect(Collectors.toList());
        Set<Long> classroomIds = studentClassroomRepository.findByStudentIdIn(studentIds).stream()
                .map(StudentClassroom::getClassroomId).collect(Collectors.toCollection(LinkedHashSet::new));
        return getForClassroomIds(new ArrayList<>(classroomIds));
    }

    private List<Map<String, Object>> getForClassroomIds(List<Long> classroomIds) {
        if (classroomIds.isEmpty()) return Collections.emptyList();
        Map<Long, String> namesById = classroomRepository.findAllById(classroomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, Classroom::getName));
        List<MemoryPost> posts = memoryPostRepository.findByClassroomIdInOrderByCreatedAtDesc(classroomIds);
        return buildFeed(posts, namesById);
    }

    private List<Map<String, Object>> buildFeed(List<MemoryPost> posts, Map<Long, String> classroomNamesById) {
        if (posts.isEmpty()) return Collections.emptyList();

        List<Long> postIds = posts.stream().map(MemoryPost::getId).collect(Collectors.toList());
        Map<Long, List<String>> imagesByPost = new HashMap<>();
        memoryImageRepository.findByMemoryPostIdInOrderByIdAsc(postIds).forEach(img ->
                imagesByPost.computeIfAbsent(img.getMemoryPostId(), k -> new ArrayList<>()).add(img.getImageUrl()));

        List<String> authorIds = posts.stream().map(MemoryPost::getAuthorAccountId).distinct().collect(Collectors.toList());
        Map<String, Account> authorsById = accountRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (MemoryPost post : posts) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", post.getId());
            map.put("classroomId", post.getClassroomId());
            map.put("classroomName", classroomNamesById.getOrDefault(post.getClassroomId(), "Classroom"));
            map.put("caption", post.getCaption());
            map.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : null);
            map.put("images", imagesByPost.getOrDefault(post.getId(), Collections.emptyList()));
            Account author = authorsById.get(post.getAuthorAccountId());
            map.put("authorName", author != null ? author.getFullName() : "Teacher");
            map.put("authorImage", author != null ? author.getProfileImageUrl() : null);
            result.add(map);
        }
        return result;
    }

    private Map<String, Object> toPostMap(MemoryPost post, String classroomName) {
        return buildFeed(Collections.singletonList(post), Collections.singletonMap(post.getClassroomId(), classroomName)).get(0);
    }

    @Transactional
    public void deletePost(Long id) {
        memoryImageRepository.deleteByMemoryPostId(id);
        memoryPostRepository.deleteById(id);
    }
}

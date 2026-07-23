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
    private final MemoryReactionRepository memoryReactionRepository;
    private final MemoryCommentRepository memoryCommentRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final StudentRepository studentRepository;
    private final StudentClassroomRepository studentClassroomRepository;
    private final SupabaseStorageService storageService;
    private final NotificationService notificationService;

    public MemoryService(MemoryPostRepository memoryPostRepository, MemoryImageRepository memoryImageRepository,
                          MemoryReactionRepository memoryReactionRepository, MemoryCommentRepository memoryCommentRepository,
                          ClassroomRepository classroomRepository, ClassMemberRepository memberRepository,
                          AccountRepository accountRepository, StudentRepository studentRepository,
                          StudentClassroomRepository studentClassroomRepository,
                          SupabaseStorageService storageService, NotificationService notificationService) {
        this.memoryPostRepository = memoryPostRepository;
        this.memoryImageRepository = memoryImageRepository;
        this.memoryReactionRepository = memoryReactionRepository;
        this.memoryCommentRepository = memoryCommentRepository;
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
                                           List<MultipartFile> files, Integer coverIndex) throws IOException {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("At least one photo or video is required.");
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found."));

        MemoryPost post = memoryPostRepository.save(MemoryPost.builder()
                .classroomId(classroomId)
                .authorAccountId(authorAccountId)
                .caption(caption != null && !caption.isBlank() ? caption.trim() : null)
                .build());

        List<MemoryImage> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            saved.add(saveMedia(post.getId(), file));
        }
        if (coverIndex != null && coverIndex >= 0 && coverIndex < saved.size()) {
            post.setCoverMediaId(saved.get(coverIndex).getId());
            memoryPostRepository.save(post);
        }

        try {
            notifyParentsInClassroom(classroomId, classroom.getName(), post.getId());
        } catch (Exception ignored) { }

        return toPostMap(post, classroom.getName(), authorAccountId);
    }

    private MemoryImage saveMedia(Long postId, MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains(".")) ? original.substring(original.lastIndexOf('.')) : ".jpg";
        String filename = "memory_" + UUID.randomUUID() + ext;
        String url = storageService.uploadImage(file.getBytes(), filename, file.getContentType());
        String mediaType = detectMediaType(file.getContentType(), original);
        return memoryImageRepository.save(MemoryImage.builder().memoryPostId(postId).imageUrl(url).mediaType(mediaType).build());
    }

    private String detectMediaType(String contentType, String originalFilename) {
        if (contentType != null && contentType.startsWith("video/")) return "VIDEO";
        String lower = (originalFilename == null ? "" : originalFilename).toLowerCase();
        if (lower.matches(".*\\.(mp4|mov|webm|avi|mkv|m4v)$")) return "VIDEO";
        return "IMAGE";
    }

    // Updates the caption, removes any images/videos whose id is in removeImageIds, appends any
    // newFiles, and optionally changes which existing item is used as the feed thumbnail (cover).
    @Transactional
    public Map<String, Object> editPost(Long postId, String caption, List<Long> removeImageIds,
                                         List<MultipartFile> newFiles, Long coverMediaId) throws IOException {
        MemoryPost post = memoryPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Memory post not found."));
        post.setCaption(caption != null && !caption.isBlank() ? caption.trim() : null);

        if (removeImageIds != null && !removeImageIds.isEmpty()) {
            memoryImageRepository.deleteAllById(removeImageIds);
            // Don't leave the cover pointing at a photo/video that no longer exists.
            if (post.getCoverMediaId() != null && removeImageIds.contains(post.getCoverMediaId())) {
                post.setCoverMediaId(null);
            }
        }
        if (newFiles != null) {
            for (MultipartFile file : newFiles) {
                if (file.isEmpty()) continue;
                saveMedia(post.getId(), file);
            }
        }
        List<MemoryImage> remaining = memoryImageRepository.findByMemoryPostIdOrderByIdAsc(postId);
        if (remaining.isEmpty()) {
            throw new IllegalArgumentException("A memory post must have at least one photo or video.");
        }
        if (coverMediaId != null && remaining.stream().anyMatch(m -> m.getId().equals(coverMediaId))) {
            post.setCoverMediaId(coverMediaId);
        }
        memoryPostRepository.save(post);

        String classroomName = classroomRepository.findById(post.getClassroomId()).map(Classroom::getName).orElse("classroom");
        return toPostMap(post, classroomName, post.getAuthorAccountId());
    }

    private void notifyParentsInClassroom(Long classroomId, String classroomName, Long postId) {
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
                    "/parent/parentmemory.html?postId=" + postId);
        });
    }

    public List<Map<String, Object>> getForClassroom(Long classroomId, String viewerAccountId) {
        Classroom classroom = classroomRepository.findById(classroomId).orElse(null);
        String name = classroom != null ? classroom.getName() : "classroom";
        return buildFeed(memoryPostRepository.findByClassroomIdOrderByCreatedAtDesc(classroomId),
                Collections.singletonMap(classroomId, name), viewerAccountId);
    }

    // All memory posts from every classroom the teacher teaches (owner or co-teacher).
    public List<Map<String, Object>> getForTeacher(String teacherAccountId) {
        Set<Long> classroomIds = new LinkedHashSet<>();
        classroomRepository.findByTeacherAccountId(teacherAccountId).forEach(c -> classroomIds.add(c.getId()));
        memberRepository.findByAccountId(teacherAccountId).forEach(m -> classroomIds.add(m.getClassroomId()));
        return getForClassroomIds(new ArrayList<>(classroomIds), teacherAccountId);
    }

    // All memory posts from every classroom any of this parent's children belong to.
    public List<Map<String, Object>> getForParent(String parentAccountId) {
        List<Student> kids = studentRepository.findByParentId(parentAccountId);
        List<Long> studentIds = kids.stream().map(Student::getId).collect(Collectors.toList());
        Set<Long> classroomIds = studentClassroomRepository.findByStudentIdIn(studentIds).stream()
                .map(StudentClassroom::getClassroomId).collect(Collectors.toCollection(LinkedHashSet::new));
        return getForClassroomIds(new ArrayList<>(classroomIds), parentAccountId);
    }

    // School-wide feed for admin oversight/moderation — every memory post from every classroom.
    public List<Map<String, Object>> getAllForAdmin() {
        Map<Long, String> namesById = classroomRepository.findAll().stream()
                .collect(Collectors.toMap(Classroom::getId, Classroom::getName));
        List<MemoryPost> posts = memoryPostRepository.findAllByOrderByCreatedAtDesc();
        return buildFeed(posts, namesById, null);
    }

    private List<Map<String, Object>> getForClassroomIds(List<Long> classroomIds, String viewerAccountId) {
        if (classroomIds.isEmpty()) return Collections.emptyList();
        Map<Long, String> namesById = classroomRepository.findAllById(classroomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, Classroom::getName));
        List<MemoryPost> posts = memoryPostRepository.findByClassroomIdInOrderByCreatedAtDesc(classroomIds);
        return buildFeed(posts, namesById, viewerAccountId);
    }

    private List<Map<String, Object>> buildFeed(List<MemoryPost> posts, Map<Long, String> classroomNamesById, String viewerAccountId) {
        if (posts.isEmpty()) return Collections.emptyList();

        List<Long> postIds = posts.stream().map(MemoryPost::getId).collect(Collectors.toList());
        Map<Long, List<Map<String, Object>>> mediaByPost = new HashMap<>();
        memoryImageRepository.findByMemoryPostIdInOrderByIdAsc(postIds).forEach(img -> {
            Map<String, Object> mediaMap = new LinkedHashMap<>();
            mediaMap.put("id", img.getId());
            mediaMap.put("url", img.getImageUrl());
            mediaMap.put("type", img.getMediaType() != null ? img.getMediaType() : "IMAGE");
            mediaByPost.computeIfAbsent(img.getMemoryPostId(), k -> new ArrayList<>()).add(mediaMap);
        });

        List<String> authorIds = posts.stream().map(MemoryPost::getAuthorAccountId).distinct().collect(Collectors.toList());
        Map<String, Account> authorsById = accountRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));

        Map<Long, Long> reactionCounts = new HashMap<>();
        Set<Long> reactedByViewer = new HashSet<>();
        memoryReactionRepository.findByMemoryPostIdIn(postIds).forEach(r -> {
            reactionCounts.merge(r.getMemoryPostId(), 1L, Long::sum);
            if (viewerAccountId != null && viewerAccountId.equals(r.getAccountId())) reactedByViewer.add(r.getMemoryPostId());
        });

        Map<Long, Long> commentCounts = new HashMap<>();
        for (Long pid : postIds) commentCounts.put(pid, memoryCommentRepository.countByMemoryPostId(pid));

        List<Map<String, Object>> result = new ArrayList<>();
        for (MemoryPost post : posts) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", post.getId());
            map.put("classroomId", post.getClassroomId());
            map.put("classroomName", classroomNamesById.getOrDefault(post.getClassroomId(), "Classroom"));
            map.put("caption", post.getCaption());
            map.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : null);
            map.put("media", orderWithCoverFirst(mediaByPost.getOrDefault(post.getId(), Collections.emptyList()), post.getCoverMediaId()));
            Account author = authorsById.get(post.getAuthorAccountId());
            map.put("authorAccountId", post.getAuthorAccountId());
            map.put("authorName", author != null ? author.getFullName() : "Teacher");
            map.put("authorImage", author != null ? author.getProfileImageUrl() : null);
            map.put("reactionCount", reactionCounts.getOrDefault(post.getId(), 0L));
            map.put("reactedByMe", reactedByViewer.contains(post.getId()));
            map.put("commentCount", commentCounts.getOrDefault(post.getId(), 0L));
            result.add(map);
        }
        return result;
    }

    // Moves the chosen cover item to the front of the list (used as the feed thumbnail);
    // falls back to upload order (oldest first) when no cover has been explicitly picked.
    private List<Map<String, Object>> orderWithCoverFirst(List<Map<String, Object>> media, Long coverMediaId) {
        if (coverMediaId == null || media.size() < 2) return media;
        int idx = -1;
        for (int i = 0; i < media.size(); i++) {
            if (coverMediaId.equals(media.get(i).get("id"))) { idx = i; break; }
        }
        if (idx <= 0) return media;
        List<Map<String, Object>> reordered = new ArrayList<>(media);
        Map<String, Object> cover = reordered.remove(idx);
        reordered.add(0, cover);
        return reordered;
    }

    private Map<String, Object> toPostMap(MemoryPost post, String classroomName, String viewerAccountId) {
        return buildFeed(Collections.singletonList(post), Collections.singletonMap(post.getClassroomId(), classroomName), viewerAccountId).get(0);
    }

    @Transactional
    public void deletePost(Long id) {
        memoryImageRepository.deleteByMemoryPostId(id);
        memoryReactionRepository.deleteByMemoryPostId(id);
        memoryCommentRepository.deleteByMemoryPostId(id);
        memoryPostRepository.deleteById(id);
    }

    // ── Reactions ────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> toggleReaction(Long postId, String accountId) {
        Optional<MemoryReaction> existing = memoryReactionRepository.findByMemoryPostIdAndAccountId(postId, accountId);
        boolean reacted;
        if (existing.isPresent()) {
            memoryReactionRepository.delete(existing.get());
            reacted = false;
        } else {
            memoryReactionRepository.save(MemoryReaction.builder().memoryPostId(postId).accountId(accountId).build());
            reacted = true;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reactedByMe", reacted);
        result.put("reactionCount", memoryReactionRepository.countByMemoryPostId(postId));
        return result;
    }

    // ── Comments ─────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> addComment(Long postId, String accountId, String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Comment cannot be empty.");
        MemoryPost post = memoryPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Memory post not found."));
        MemoryComment saved = memoryCommentRepository.save(MemoryComment.builder()
                .memoryPostId(postId).authorAccountId(accountId).content(content.trim()).build());
        try {
            notifyOnComment(post, accountId);
        } catch (Exception ignored) { }
        return toCommentMap(saved);
    }

    // Notifies the post's author plus anyone else who has commented on this post — i.e. everyone
    // already part of the conversation, excluding whoever just wrote this comment.
    private void notifyOnComment(MemoryPost post, String commenterAccountId) {
        Set<String> recipients = new LinkedHashSet<>();
        recipients.add(post.getAuthorAccountId());
        memoryCommentRepository.findByMemoryPostIdOrderByCreatedAtAsc(post.getId())
                .forEach(c -> recipients.add(c.getAuthorAccountId()));
        recipients.remove(commenterAccountId);
        if (recipients.isEmpty()) return;

        String commenterName = accountRepository.findById(commenterAccountId).map(Account::getFullName).orElse("Someone");
        Map<String, Account> accountsById = accountRepository.findAllById(new ArrayList<>(recipients)).stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));

        for (String recipientId : recipients) {
            Account recipient = accountsById.get(recipientId);
            if (recipient == null) continue;
            String link = switch (recipient.getRoleType()) {
                case TEACHER -> "/teacher/teachermemory.html?postId=" + post.getId();
                case ADMIN -> "/admin/admingallery.html?postId=" + post.getId();
                default -> "/parent/parentmemory.html?postId=" + post.getId();
            };
            notificationService.create(recipientId,
                    commenterName + " commented on a memory post",
                    "Tap to view the conversation.",
                    link);
        }
    }

    public List<Map<String, Object>> getComments(Long postId) {
        List<MemoryComment> comments = memoryCommentRepository.findByMemoryPostIdOrderByCreatedAtAsc(postId);
        if (comments.isEmpty()) return Collections.emptyList();
        List<String> authorIds = comments.stream().map(MemoryComment::getAuthorAccountId).distinct().collect(Collectors.toList());
        Map<String, Account> authorsById = accountRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));
        return comments.stream().map(c -> toCommentMapWithAuthor(c, authorsById)).collect(Collectors.toList());
    }

    private Map<String, Object> toCommentMap(MemoryComment c) {
        Map<String, Account> authorsById = accountRepository.findById(c.getAuthorAccountId())
                .map(a -> Collections.singletonMap(a.getAccountId(), a)).orElse(Collections.emptyMap());
        return toCommentMapWithAuthor(c, authorsById);
    }

    private Map<String, Object> toCommentMapWithAuthor(MemoryComment c, Map<String, Account> authorsById) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("memoryPostId", c.getMemoryPostId());
        map.put("authorAccountId", c.getAuthorAccountId());
        Account author = authorsById.get(c.getAuthorAccountId());
        map.put("authorName", author != null ? author.getFullName() : "Someone");
        map.put("authorImage", author != null ? author.getProfileImageUrl() : null);
        map.put("content", c.getContent());
        map.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        return map;
    }

    public void deleteComment(Long commentId) {
        memoryCommentRepository.deleteById(commentId);
    }
}

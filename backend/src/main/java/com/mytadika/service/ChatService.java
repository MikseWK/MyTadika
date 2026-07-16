package com.mytadika.service;

import com.mytadika.dto.ChatMessageRequest;
import com.mytadika.model.Account;
import com.mytadika.model.ChatContact;
import com.mytadika.model.ChatMessage;
import com.mytadika.model.ClassMember;
import com.mytadika.model.Classroom;
import com.mytadika.model.Student;
import com.mytadika.model.StudentClassroom;
import com.mytadika.model.TeacherContact;
import com.mytadika.repository.AccountRepository;
import com.mytadika.repository.ChatContactRepository;
import com.mytadika.repository.ChatMessageRepository;
import com.mytadika.repository.ClassMemberRepository;
import com.mytadika.repository.ClassroomRepository;
import com.mytadika.repository.StudentClassroomRepository;
import com.mytadika.repository.StudentRepository;
import com.mytadika.repository.TeacherContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final AccountRepository accountRepository;
    private final ChatContactRepository chatContactRepository;
    private final TeacherContactRepository teacherContactRepository;
    private final StudentRepository studentRepository;
    private final StudentClassroomRepository studentClassroomRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassMemberRepository classMemberRepository;
    private final SupabaseStorageService storageService;
    private final NotificationService notificationService;

    public ChatService(ChatMessageRepository chatMessageRepository,
                       AccountRepository accountRepository,
                       ChatContactRepository chatContactRepository,
                       TeacherContactRepository teacherContactRepository,
                       StudentRepository studentRepository,
                       StudentClassroomRepository studentClassroomRepository,
                       ClassroomRepository classroomRepository,
                       ClassMemberRepository classMemberRepository,
                       SupabaseStorageService storageService,
                       NotificationService notificationService) {
        this.chatMessageRepository = chatMessageRepository;
        this.accountRepository = accountRepository;
        this.chatContactRepository = chatContactRepository;
        this.teacherContactRepository = teacherContactRepository;
        this.studentRepository = studentRepository;
        this.studentClassroomRepository = studentClassroomRepository;
        this.classroomRepository = classroomRepository;
        this.classMemberRepository = classMemberRepository;
        this.storageService = storageService;
        this.notificationService = notificationService;
    }

    // ── Contacts ────────────────────────────────────────────────────────────

    public void addContact(String parentId, String teacherId) {
        if (!chatContactRepository.existsByParentAccountIdAndTeacherAccountId(parentId, teacherId)) {
            ChatContact contact = new ChatContact();
            contact.setParentAccountId(parentId);
            contact.setTeacherAccountId(teacherId);
            chatContactRepository.save(contact);
        }
    }

    public void addTeacherContact(String teacherIdA, String teacherIdB) {
        boolean exists = teacherContactRepository.existsByTeacherAccountIdAAndTeacherAccountIdB(teacherIdA, teacherIdB)
                || teacherContactRepository.existsByTeacherAccountIdAAndTeacherAccountIdB(teacherIdB, teacherIdA);
        if (!exists) {
            TeacherContact contact = new TeacherContact();
            contact.setTeacherAccountIdA(teacherIdA);
            contact.setTeacherAccountIdB(teacherIdB);
            teacherContactRepository.save(contact);
        }
    }

    public List<Map<String, Object>> getContacts(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        if (account.getRoleType() == Account.RoleType.PARENT) {
            return getContactsForParent(accountId);
        } else if (account.getRoleType() == Account.RoleType.TEACHER) {
            List<Map<String, Object>> combined = new ArrayList<>();
            combined.addAll(getContactsForTeacher(accountId));
            combined.addAll(getTeacherPeerContacts(accountId));
            combined.sort((a, b) -> {
                String aAt = (String) a.get("lastMessageAt");
                String bAt = (String) b.get("lastMessageAt");
                if (aAt == null && bAt == null) return 0;
                if (aAt == null) return 1;
                if (bAt == null) return -1;
                return bAt.compareTo(aAt);
            });
            return combined;
        } else {
            return getContactsForTeacher(accountId);
        }
    }

    // Batched versions of getLastMessage/countUnread — one query for the whole contact
    // list instead of two queries per contact, which is what made contact lists slow
    // once an account had more than a couple of conversations.
    private Map<String, ChatMessage> getLastMessagesBatch(String me, List<String> others) {
        Map<String, ChatMessage> result = new java.util.HashMap<>();
        if (others.isEmpty()) return result;
        for (ChatMessage m : chatMessageRepository.findLatestPerContact(me, others)) {
            String other = m.getSenderId().equals(me) ? m.getReceiverId() : m.getSenderId();
            result.putIfAbsent(other, m); // query is ordered by sentAt DESC, so first hit per contact is the latest
        }
        return result;
    }

    private Map<String, Long> getUnreadCountsBatch(String me, List<String> others) {
        Map<String, Long> result = new java.util.HashMap<>();
        if (others.isEmpty()) return result;
        for (Object[] row : chatMessageRepository.countUnreadGrouped(me, others))
            result.put((String) row[0], (Long) row[1]);
        return result;
    }

    private List<Map<String, Object>> getContactsForParent(String parentId) {
        List<ChatContact> contacts = chatContactRepository.findByParentAccountId(parentId);
        if (contacts.isEmpty()) return new ArrayList<>();

        List<String> teacherIds = contacts.stream().map(ChatContact::getTeacherAccountId).collect(Collectors.toList());
        Map<String, Account> teachersById = accountRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));
        Map<String, ChatMessage> lastMessages = getLastMessagesBatch(parentId, teacherIds);
        Map<String, Long> unreadCounts = getUnreadCountsBatch(parentId, teacherIds);

        return contacts.stream()
                .map(c -> {
                    Account teacher = teachersById.get(c.getTeacherAccountId());
                    if (teacher == null) return null;
                    ChatMessage last = lastMessages.get(c.getTeacherAccountId());
                    long unread = unreadCounts.getOrDefault(c.getTeacherAccountId(), 0L);

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("accountId", teacher.getAccountId());
                    map.put("fullName", teacher.getFullName());
                    map.put("email", teacher.getEmail());
                    map.put("profileImageUrl", teacher.getProfileImageUrl());
                    map.put("description", teacher.getDescription());
                    map.put("qualification", teacher.getQualification());
                    map.put("experience", teacher.getExperience());
                    map.put("focusArea", teacher.getFocusArea());
                    map.put("phoneNumber", teacher.getPhoneNumber());
                    map.put("lastActiveAt", teacher.getLastActiveAt() != null ? teacher.getLastActiveAt().toString() : null);
                    map.put("lastMessage", last != null ? messagePreview(last) : null);
                    map.put("lastMessageAt", last != null ? last.getSentAt().toString() : null);
                    map.put("unreadCount", unread);
                    return map;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> {
                    String aAt = (String) a.get("lastMessageAt");
                    String bAt = (String) b.get("lastMessageAt");
                    if (aAt == null && bAt == null) return 0;
                    if (aAt == null) return 1;
                    if (bAt == null) return -1;
                    return bAt.compareTo(aAt);
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> getContactsForTeacher(String teacherId) {
        List<ChatContact> contacts = chatContactRepository.findByTeacherAccountId(teacherId);
        if (contacts.isEmpty()) return new ArrayList<>();

        List<String> parentIds = contacts.stream().map(ChatContact::getParentAccountId).collect(Collectors.toList());
        Map<String, Account> parentsById = accountRepository.findAllById(parentIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));
        Map<String, ChatMessage> lastMessages = getLastMessagesBatch(teacherId, parentIds);
        Map<String, Long> unreadCounts = getUnreadCountsBatch(teacherId, parentIds);

        return contacts.stream()
                .map(c -> {
                    Account parent = parentsById.get(c.getParentAccountId());
                    if (parent == null) return null;
                    ChatMessage last = lastMessages.get(c.getParentAccountId());
                    long unread = unreadCounts.getOrDefault(c.getParentAccountId(), 0L);

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("accountId", parent.getAccountId());
                    map.put("fullName", parent.getFullName());
                    map.put("email", parent.getEmail());
                    map.put("profileImageUrl", parent.getProfileImageUrl());
                    map.put("phoneNumber", parent.getPhoneNumber());
                    map.put("address", parent.getAddress());
                    map.put("roleType", "PARENT");
                    map.put("lastActiveAt", parent.getLastActiveAt() != null ? parent.getLastActiveAt().toString() : null);
                    map.put("lastMessage", last != null ? messagePreview(last) : null);
                    map.put("lastMessageAt", last != null ? last.getSentAt().toString() : null);
                    map.put("unreadCount", unread);
                    return map;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> {
                    String aAt = (String) a.get("lastMessageAt");
                    String bAt = (String) b.get("lastMessageAt");
                    if (aAt == null && bAt == null) return 0;
                    if (aAt == null) return 1;
                    if (bAt == null) return -1;
                    return bAt.compareTo(aAt);
                })
                .collect(Collectors.toList());
    }

    // Other teachers a teacher has started chatting with (staff-to-staff messaging).
    private List<Map<String, Object>> getTeacherPeerContacts(String teacherId) {
        List<TeacherContact> peers = teacherContactRepository.findByTeacherAccountIdAOrTeacherAccountIdB(teacherId, teacherId);
        if (peers.isEmpty()) return new ArrayList<>();

        List<String> otherIds = peers.stream()
                .map(tc -> tc.getTeacherAccountIdA().equals(teacherId) ? tc.getTeacherAccountIdB() : tc.getTeacherAccountIdA())
                .collect(Collectors.toList());
        Map<String, Account> othersById = accountRepository.findAllById(otherIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));
        Map<String, ChatMessage> lastMessages = getLastMessagesBatch(teacherId, otherIds);
        Map<String, Long> unreadCounts = getUnreadCountsBatch(teacherId, otherIds);

        return peers.stream()
                .map(tc -> {
                    String otherId = tc.getTeacherAccountIdA().equals(teacherId)
                            ? tc.getTeacherAccountIdB() : tc.getTeacherAccountIdA();
                    Account other = othersById.get(otherId);
                    if (other == null) return null;
                    ChatMessage last = lastMessages.get(otherId);
                    long unread = unreadCounts.getOrDefault(otherId, 0L);

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("accountId", other.getAccountId());
                    map.put("fullName", other.getFullName());
                    map.put("email", other.getEmail());
                    map.put("profileImageUrl", other.getProfileImageUrl());
                    map.put("description", other.getDescription());
                    map.put("qualification", other.getQualification());
                    map.put("experience", other.getExperience());
                    map.put("focusArea", other.getFocusArea());
                    map.put("phoneNumber", other.getPhoneNumber());
                    map.put("roleType", "TEACHER");
                    map.put("lastActiveAt", other.getLastActiveAt() != null ? other.getLastActiveAt().toString() : null);
                    map.put("lastMessage", last != null ? messagePreview(last) : null);
                    map.put("lastMessageAt", last != null ? last.getSentAt().toString() : null);
                    map.put("unreadCount", unread);
                    return map;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── Shared "School Admin" inbox ────────────────────────────────────────────
    // Every parent can message a single shared admin identity (the earliest-created
    // ADMIN account) without picking a specific admin. Any admin can view/reply to
    // that same thread — replies are sent with senderId = the inbox identity, not
    // the individual admin's own accountId, so the conversation reads as one
    // consistent "School Admin" contact regardless of which admin answers.

    public Map<String, Object> getAdminInboxIdentity() {
        Account inbox = accountRepository.findFirstByRoleTypeOrderByCreatedAtAsc(Account.RoleType.ADMIN)
                .orElseThrow(() -> new RuntimeException("No admin account exists yet"));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("accountId", inbox.getAccountId());
        map.put("fullName", "School Admin");
        map.put("profileImageUrl", inbox.getProfileImageUrl());
        map.put("email", inbox.getEmail());
        map.put("phoneNumber", inbox.getPhoneNumber());
        map.put("lastActiveAt", inbox.getLastActiveAt() != null ? inbox.getLastActiveAt().toString() : null);
        return map;
    }

    public List<Map<String, Object>> getAdminInboxContacts() {
        String inboxId = (String) getAdminInboxIdentity().get("accountId");
        List<String> parentIds = chatMessageRepository.findDistinctContactsOf(inboxId);
        if (parentIds.isEmpty()) return new ArrayList<>();

        Map<String, Account> parentsById = accountRepository.findAllById(parentIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));
        Map<String, ChatMessage> lastMessages = getLastMessagesBatch(inboxId, parentIds);
        Map<String, Long> unreadCounts = getUnreadCountsBatch(inboxId, parentIds);

        return parentIds.stream()
                .map(parentId -> {
                    Account parent = parentsById.get(parentId);
                    if (parent == null) return null;
                    ChatMessage last = lastMessages.get(parentId);
                    long unread = unreadCounts.getOrDefault(parentId, 0L);

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("accountId", parent.getAccountId());
                    map.put("fullName", parent.getFullName());
                    map.put("email", parent.getEmail());
                    map.put("profileImageUrl", parent.getProfileImageUrl());
                    map.put("lastMessage", last != null ? messagePreview(last) : null);
                    map.put("lastMessageAt", last != null ? last.getSentAt().toString() : null);
                    map.put("unreadCount", unread);
                    return map;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> {
                    String aAt = (String) a.get("lastMessageAt");
                    String bAt = (String) b.get("lastMessageAt");
                    if (aAt == null && bAt == null) return 0;
                    if (aAt == null) return 1;
                    if (bAt == null) return -1;
                    return bAt.compareTo(aAt);
                })
                .collect(Collectors.toList());
    }

    private String messagePreview(ChatMessage m) {
        if ("image".equals(m.getMessageType())) return "📷 Image";
        if ("file".equals(m.getMessageType())) return "📎 File";
        String c = m.getContent();
        return c.length() > 35 ? c.substring(0, 35) + "…" : c;
    }

    public void markAsRead(String viewerId, String senderId) {
        chatMessageRepository.markAsRead(viewerId, senderId);
    }

    // ── Teachers / Parents (full list for Add modal) ─────────────────────────

    // Each parent is enriched with their children's names + classroom(s), so the
    // "new message" recipient picker can group parents by classroom instead of showing
    // one long flat list — batched to avoid a query per parent.
    public List<Map<String, Object>> getParents() {
        List<Account> parents = accountRepository.findByRoleType(Account.RoleType.PARENT);
        List<String> parentIds = parents.stream().map(Account::getAccountId).collect(Collectors.toList());

        List<Student> allStudents = studentRepository.findByParentIdIn(parentIds);
        List<Long> studentIds = allStudents.stream().map(Student::getId).collect(Collectors.toList());
        List<StudentClassroom> links = studentClassroomRepository.findByStudentIdIn(studentIds);
        Map<Long, Classroom> classroomsById = classroomRepository.findAllById(
                links.stream().map(StudentClassroom::getClassroomId).distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Classroom::getId, c -> c));

        Map<Long, List<Classroom>> classroomsByStudent = new HashMap<>();
        for (StudentClassroom link : links) {
            Classroom c = classroomsById.get(link.getClassroomId());
            if (c != null) classroomsByStudent.computeIfAbsent(link.getStudentId(), k -> new ArrayList<>()).add(c);
        }
        Map<String, List<Student>> studentsByParent = new HashMap<>();
        for (Student s : allStudents)
            if (s.getParentId() != null) studentsByParent.computeIfAbsent(s.getParentId(), k -> new ArrayList<>()).add(s);

        return parents.stream()
                .map(p -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("accountId", p.getAccountId());
                    map.put("fullName", p.getFullName());
                    map.put("email", p.getEmail());
                    map.put("profileImageUrl", p.getProfileImageUrl());
                    map.put("phoneNumber", p.getPhoneNumber());
                    map.put("address", p.getAddress());
                    map.put("lastActiveAt", p.getLastActiveAt() != null ? p.getLastActiveAt().toString() : null);

                    List<Student> kids = studentsByParent.getOrDefault(p.getAccountId(), Collections.emptyList());
                    Set<String> classNames = new LinkedHashSet<>();
                    List<Map<String, Object>> children = new ArrayList<>();
                    for (Student kid : kids) {
                        List<Classroom> kidClassrooms = classroomsByStudent.getOrDefault(kid.getId(), Collections.emptyList());
                        List<String> kidClassNames = kidClassrooms.stream().map(Classroom::getName).collect(Collectors.toList());
                        classNames.addAll(kidClassNames);
                        Map<String, Object> childMap = new LinkedHashMap<>();
                        childMap.put("name", kid.getFullName());
                        childMap.put("classroomNames", kidClassNames);
                        children.add(childMap);
                    }
                    map.put("childNames", kids.stream().map(Student::getFullName).collect(Collectors.joining(", ")));
                    map.put("children", children);
                    map.put("classroomNames", new ArrayList<>(classNames));
                    return map;
                })
                .collect(Collectors.toList());
    }

    // Enriched with the classes each teacher teaches — as owner OR co-teacher — so the
    // "new message" recipient picker can filter teachers by classroom too. Batched
    // (one query for all classrooms/memberships) rather than one lookup per teacher.
    public List<Map<String, Object>> getTeachers() {
        List<Classroom> allClassrooms = classroomRepository.findAll();
        Map<String, List<String>> classroomNamesByTeacher = new HashMap<>();
        for (Classroom c : allClassrooms) {
            if (c.getTeacherAccountId() != null) {
                classroomNamesByTeacher.computeIfAbsent(c.getTeacherAccountId(), k -> new ArrayList<>()).add(c.getName());
            }
        }
        Map<Long, Classroom> classroomsById = allClassrooms.stream()
                .collect(Collectors.toMap(Classroom::getId, c -> c));
        for (ClassMember m : classMemberRepository.findAll()) {
            if (!"teacher".equals(m.getRole())) continue;
            Classroom c = classroomsById.get(m.getClassroomId());
            if (c == null) continue;
            List<String> names = classroomNamesByTeacher.computeIfAbsent(m.getAccountId(), k -> new ArrayList<>());
            if (!names.contains(c.getName())) names.add(c.getName());
        }

        return accountRepository.findByRoleType(Account.RoleType.TEACHER)
                .stream()
                .map(t -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("accountId", t.getAccountId());
                    map.put("fullName", t.getFullName());
                    map.put("email", t.getEmail());
                    map.put("profileImageUrl", t.getProfileImageUrl());
                    map.put("description", t.getDescription());
                    map.put("qualification", t.getQualification());
                    map.put("experience", t.getExperience());
                    map.put("focusArea", t.getFocusArea());
                    map.put("phoneNumber", t.getPhoneNumber());
                    map.put("lastActiveAt", t.getLastActiveAt() != null ? t.getLastActiveAt().toString() : null);
                    map.put("classroomNames", classroomNamesByTeacher.getOrDefault(t.getAccountId(), Collections.emptyList()));
                    return map;
                })
                .collect(Collectors.toList());
    }

    // ── Messages ─────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getMessages(String accountId, String contactId) {
        return chatMessageRepository.findConversation(accountId, contactId)
                .stream()
                .map(m -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", m.getId());
                    map.put("senderId", m.getSenderId());
                    map.put("receiverId", m.getReceiverId());
                    map.put("content", m.getContent());
                    map.put("sentAt", m.getSentAt().toString());
                    map.put("read", m.isRead());
                    map.put("edited", m.isEdited());
                    map.put("messageType", m.getMessageType());
                    map.put("replyToId", m.getReplyToId());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public ChatMessage sendMessage(ChatMessageRequest request) {
        // Auto-create contact record on first message
        Account sender = accountRepository.findById(request.getSenderId()).orElse(null);
        Account receiver = accountRepository.findById(request.getReceiverId()).orElse(null);
        if (sender != null && receiver != null) {
            if (sender.getRoleType() == Account.RoleType.TEACHER && receiver.getRoleType() == Account.RoleType.PARENT) {
                addContact(request.getReceiverId(), request.getSenderId());
            } else if (sender.getRoleType() == Account.RoleType.PARENT && receiver.getRoleType() == Account.RoleType.TEACHER) {
                addContact(request.getSenderId(), request.getReceiverId());
            } else if (sender.getRoleType() == Account.RoleType.TEACHER && receiver.getRoleType() == Account.RoleType.TEACHER) {
                addTeacherContact(request.getSenderId(), request.getReceiverId());
            }
        }

        String type = (request.getMessageType() != null) ? request.getMessageType() : "text";
        ChatMessage msg = ChatMessage.builder()
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .content(request.getContent())
                .sentAt(LocalDateTime.now())
                .read(false)
                .edited(false)
                .deleted(false)
                .messageType(type)
                .replyToId(request.getReplyToId())
                .build();
        ChatMessage saved = chatMessageRepository.save(msg);

        // Someone messaged the shared "School Admin" inbox — notify every real admin
        // account so whichever one is online sees it needs a reply.
        if (sender != null && receiver != null
                && receiver.getRoleType() == Account.RoleType.ADMIN
                && sender.getRoleType() != Account.RoleType.ADMIN) {
            notifyAdminsOfNewMessage(sender);
        }
        return saved;
    }

    private void notifyAdminsOfNewMessage(Account sender) {
        List<Account> admins = accountRepository.findByRoleType(Account.RoleType.ADMIN);
        String title = "New message from " + sender.getFullName();
        for (Account admin : admins)
            notificationService.create(admin.getAccountId(), title, "Tap to reply in the School Admin inbox.", "/admin/adminmessages.html");
    }

    public String uploadImage(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.'))
                : ".jpg";
        String filename = "chat_" + UUID.randomUUID() + ext;
        return storageService.uploadImage(file.getBytes(), filename, file.getContentType());
    }

    public void editMessage(Long messageId, String senderId, String newContent) {
        ChatMessage msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        if (!msg.getSenderId().equals(senderId)) {
            throw new RuntimeException("Not authorized to edit this message");
        }
        msg.setContent(newContent);
        msg.setEdited(true);
        chatMessageRepository.save(msg);
    }

    public void deleteMessage(Long messageId, String senderId) {
        ChatMessage msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        if (!msg.getSenderId().equals(senderId)) {
            throw new RuntimeException("Not authorized to delete this message");
        }
        msg.setDeleted(true);
        chatMessageRepository.save(msg);
    }
}

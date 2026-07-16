package com.mytadika.repository;

import com.mytadika.model.ChatContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatContactRepository extends JpaRepository<ChatContact, Long> {
    List<ChatContact> findByParentAccountId(String parentAccountId);
    List<ChatContact> findByTeacherAccountId(String teacherAccountId);
    boolean existsByParentAccountIdAndTeacherAccountId(String parentAccountId, String teacherAccountId);
}

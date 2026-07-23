package com.mytadika.repository;

import com.mytadika.model.TeacherContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherContactRepository extends JpaRepository<TeacherContact, Long> {
    List<TeacherContact> findByTeacherAccountIdAOrTeacherAccountIdB(String teacherAccountIdA, String teacherAccountIdB);
    boolean existsByTeacherAccountIdAAndTeacherAccountIdB(String teacherAccountIdA, String teacherAccountIdB);
}

package com.mytadika.repository;

import com.mytadika.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findByDeletedAtIsNull();

    Optional<Student> findByStudentCode(String studentCode);

    // Account's id property is named "accountId", not "id", so Spring Data can't
    // auto-derive these from the method name alone — hence the explicit JPQL.
    @Query("SELECT s FROM Student s WHERE s.parent.accountId = :parentId")
    List<Student> findByParentId(@Param("parentId") String parentId);

    @Query("SELECT s FROM Student s WHERE s.parent.accountId IN :parentIds")
    List<Student> findByParentIdIn(@Param("parentIds") List<String> parentIds);

    @Query("SELECT s FROM Student s WHERE s.parent.accountId = :parentId AND s.deletedAt IS NULL")
    List<Student> findByParentIdAndDeletedAtIsNull(@Param("parentId") String parentId);

    List<Student> findByClassroomIdAndDeletedAtIsNull(Long classroomId);
}

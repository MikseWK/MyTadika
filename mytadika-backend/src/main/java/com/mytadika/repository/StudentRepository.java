package com.mytadika.repository;

import com.mytadika.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findByDeletedAtIsNull();

    List<Student> findByParentAccountIdAndDeletedAtIsNull(String parentAccountId);

    List<Student> findByClassroomIdAndDeletedAtIsNull(Long classroomId);
}

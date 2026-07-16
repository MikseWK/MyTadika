package com.mytadika.repository;

import com.mytadika.model.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {
    List<Classroom> findByTeacherAccountId(String teacherAccountId);
    Optional<Classroom> findByClassCode(String classCode);
}

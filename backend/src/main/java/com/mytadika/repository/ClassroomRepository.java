package com.mytadika.repository;
import com.mytadika.model.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {
    List<Classroom> findByTeacherAccountId(String teacherAccountId);
    java.util.Optional<Classroom> findByClassCode(String classCode);
}

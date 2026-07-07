package com.mytadika.repository;
import com.mytadika.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByStudentCode(String studentCode);
    List<Student> findByParentId(String parentId);
    List<Student> findByParentIdIn(List<String> parentIds);
    List<Student> findByParentIdAndDeletedAtIsNull(String parentId);
    List<Student> findByClassroomIdAndDeletedAtIsNull(Long classroomId);
}

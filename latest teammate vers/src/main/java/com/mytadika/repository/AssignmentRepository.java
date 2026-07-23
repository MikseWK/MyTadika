package com.mytadika.repository;
import com.mytadika.model.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByClassroomIdOrderByCreatedAtAsc(Long classroomId);

    List<Assignment> findByClassroomIdIn(List<Long> classroomIds);

    List<Assignment> findByClassroomIdAndDueDateGreaterThanEqualOrderByDueDateAsc(Long classroomId, String today);

    List<Assignment> findByClassroomIdInAndDueDateGreaterThanEqualOrderByDueDateAsc(List<Long> classroomIds, String today);

    List<Assignment> findByDueDate(String dueDate);

    @Modifying
    @Query("DELETE FROM Assignment a WHERE a.classroomId = :classroomId")
    void deleteByClassroomId(@Param("classroomId") Long classroomId);
}

package com.mytadika.repository;
import com.mytadika.model.ClassworkCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Set;

public interface ClassworkCompletionRepository extends JpaRepository<ClassworkCompletion, Long> {
    boolean existsByAssignmentIdAndStudentId(Long assignmentId, Long studentId);
    void deleteByAssignmentIdAndStudentId(Long assignmentId, Long studentId);
    void deleteByAssignmentId(Long assignmentId);
    int countByAssignmentId(Long assignmentId);

    List<ClassworkCompletion> findByAssignmentId(Long assignmentId);

    List<ClassworkCompletion> findByStudentIdIn(List<Long> studentIds);

    @Query("SELECT c.assignmentId FROM ClassworkCompletion c WHERE c.studentId = :studentId")
    Set<Long> findCompletedAssignmentIdsByStudentId(@Param("studentId") Long studentId);
}

package com.mytadika.repository;
import com.mytadika.model.MemoryPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MemoryPostRepository extends JpaRepository<MemoryPost, Long> {
    List<MemoryPost> findByClassroomIdOrderByCreatedAtDesc(Long classroomId);
    List<MemoryPost> findByClassroomIdInOrderByCreatedAtDesc(List<Long> classroomIds);
    List<MemoryPost> findAllByOrderByCreatedAtDesc();

    @Modifying
    @Query("DELETE FROM MemoryPost m WHERE m.classroomId = :classroomId")
    void deleteByClassroomId(@Param("classroomId") Long classroomId);
}

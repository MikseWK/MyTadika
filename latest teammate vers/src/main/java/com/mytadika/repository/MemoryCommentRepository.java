package com.mytadika.repository;
import com.mytadika.model.MemoryComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MemoryCommentRepository extends JpaRepository<MemoryComment, Long> {
    List<MemoryComment> findByMemoryPostIdOrderByCreatedAtAsc(Long memoryPostId);
    List<MemoryComment> findByMemoryPostIdInOrderByCreatedAtAsc(List<Long> memoryPostIds);
    long countByMemoryPostId(Long memoryPostId);

    @Modifying
    @Query("DELETE FROM MemoryComment c WHERE c.memoryPostId = :memoryPostId")
    void deleteByMemoryPostId(@Param("memoryPostId") Long memoryPostId);
}

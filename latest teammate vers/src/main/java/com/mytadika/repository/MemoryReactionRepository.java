package com.mytadika.repository;
import com.mytadika.model.MemoryReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface MemoryReactionRepository extends JpaRepository<MemoryReaction, Long> {
    List<MemoryReaction> findByMemoryPostIdIn(List<Long> memoryPostIds);
    long countByMemoryPostId(Long memoryPostId);
    Optional<MemoryReaction> findByMemoryPostIdAndAccountId(Long memoryPostId, String accountId);

    @Modifying
    @Query("DELETE FROM MemoryReaction r WHERE r.memoryPostId = :memoryPostId")
    void deleteByMemoryPostId(@Param("memoryPostId") Long memoryPostId);
}

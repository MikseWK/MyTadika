package com.mytadika.repository;
import com.mytadika.model.MemoryImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MemoryImageRepository extends JpaRepository<MemoryImage, Long> {
    List<MemoryImage> findByMemoryPostIdOrderByIdAsc(Long memoryPostId);
    List<MemoryImage> findByMemoryPostIdInOrderByIdAsc(List<Long> memoryPostIds);

    @Modifying
    @Query("DELETE FROM MemoryImage i WHERE i.memoryPostId = :memoryPostId")
    void deleteByMemoryPostId(@Param("memoryPostId") Long memoryPostId);
}

package com.mytadika.repository;
import com.mytadika.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByClassroomIdOrderByCreatedAtDesc(Long classroomId);

    @Modifying
    @Query("DELETE FROM Announcement a WHERE a.classroomId = :classroomId")
    void deleteByClassroomId(@Param("classroomId") Long classroomId);
}

package com.mytadika.repository;
import com.mytadika.model.AcademicScoreItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AcademicScoreItemRepository extends JpaRepository<AcademicScoreItem, Long> {
    List<AcademicScoreItem> findByAcademicRecordId(Long academicRecordId);

    List<AcademicScoreItem> findByAcademicRecordIdIn(List<Long> academicRecordIds);

    @Modifying
    @Query("DELETE FROM AcademicScoreItem s WHERE s.academicRecordId = :academicRecordId")
    void deleteByAcademicRecordId(@Param("academicRecordId") Long academicRecordId);
}

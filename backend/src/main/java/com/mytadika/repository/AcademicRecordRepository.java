package com.mytadika.repository;
import com.mytadika.model.AcademicRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AcademicRecordRepository extends JpaRepository<AcademicRecord, Long> {
    List<AcademicRecord> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}

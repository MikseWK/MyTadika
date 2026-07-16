package com.mytadika.repository;

import com.mytadika.model.AcademicRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademicRecordRepository extends JpaRepository<AcademicRecord, Long> {

    List<AcademicRecord> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}

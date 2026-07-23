package com.mytadika.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mytadika.model.AcademicRecord;

public interface AcademicRecordRepository extends JpaRepository<AcademicRecord, Long> {
    List<AcademicRecord> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}

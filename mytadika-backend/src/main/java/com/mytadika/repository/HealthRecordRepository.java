package com.mytadika.repository;

import com.mytadika.model.HealthRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HealthRecordRepository extends JpaRepository<HealthRecord, Long> {

    /**
     * Retrieve all measurements for a child sorted by newest first.
     */
    List<HealthRecord> findByStudentIdOrderByRecordedAtDesc(Long studentId);

    /**
     * Retrieve the most recent measurement for a child.
     */
    Optional<HealthRecord> findFirstByStudentIdOrderByRecordedAtDesc(Long studentId);
}

package com.mytadika.repository;
import com.mytadika.model.Fee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeeRepository extends JpaRepository<Fee, Long> {
    List<Fee> findByStudentIdOrderByDueDateDesc(Long studentId);
    List<Fee> findAllByOrderByDueDateAsc();
    List<Fee> findByStudentIdIn(List<Long> studentIds);
}

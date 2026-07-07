package com.mytadika.repository;

import com.mytadika.model.SchoolEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolEventRepository extends JpaRepository<SchoolEvent, Long> {
    List<SchoolEvent> findAllByOrderByEventDateAsc();
}

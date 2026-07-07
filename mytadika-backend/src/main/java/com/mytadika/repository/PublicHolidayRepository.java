package com.mytadika.repository;

import com.mytadika.model.PublicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {
    List<PublicHoliday> findAllByOrderByHolidayDateAsc();
    List<PublicHoliday> findByYearOrderByHolidayDateAsc(Integer year);
}

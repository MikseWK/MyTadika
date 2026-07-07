package com.mytadika.service;

import com.mytadika.model.PublicHoliday;
import com.mytadika.model.SchoolEvent;
import com.mytadika.repository.PublicHolidayRepository;
import com.mytadika.repository.SchoolEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EventService {

    private final SchoolEventRepository schoolEventRepository;
    private final PublicHolidayRepository publicHolidayRepository;

    public EventService(SchoolEventRepository schoolEventRepository,
                        PublicHolidayRepository publicHolidayRepository) {
        this.schoolEventRepository = schoolEventRepository;
        this.publicHolidayRepository = publicHolidayRepository;
    }

    public List<SchoolEvent> getAllSchoolEvents() {
        return schoolEventRepository.findAllByOrderByEventDateAsc();
    }

    public SchoolEvent createSchoolEvent(Map<String, String> body) {
        if (body.get("title") == null || body.get("title").isBlank())
            throw new IllegalArgumentException("Title is required");
        if (body.get("date") == null || body.get("date").isBlank())
            throw new IllegalArgumentException("Date is required");

        SchoolEvent event = SchoolEvent.builder()
                .title(body.get("title").trim())
                .eventDate(body.get("date"))
                .eventType(body.getOrDefault("type", "general"))
                .description(body.get("description"))
                .createdByAccountId(body.get("createdByAccountId"))
                .build();
        return schoolEventRepository.save(event);
    }

    public SchoolEvent updateSchoolEvent(Long id, Map<String, String> body) {
        if (body.get("title") == null || body.get("title").isBlank())
            throw new IllegalArgumentException("Title is required");
        if (body.get("date") == null || body.get("date").isBlank())
            throw new IllegalArgumentException("Date is required");

        SchoolEvent event = schoolEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        event.setTitle(body.get("title").trim());
        event.setEventDate(body.get("date"));
        event.setEventType(body.getOrDefault("type", "general"));
        event.setDescription(body.get("description"));
        return schoolEventRepository.save(event);
    }

    public void deleteSchoolEvent(Long id) {
        schoolEventRepository.deleteById(id);
    }

    public List<PublicHoliday> getAllHolidays() {
        return publicHolidayRepository.findAllByOrderByHolidayDateAsc();
    }

    public List<PublicHoliday> getHolidaysByYear(Integer year) {
        return publicHolidayRepository.findByYearOrderByHolidayDateAsc(year);
    }

    // Returns a list because a multi-day holiday (e.g. Hari Raya) is stored as one
    // row per calendar day, all sharing the same name — the calendar renders holidays
    // keyed by exact date, so this keeps that lookup untouched for single-day holidays.
    @Transactional
    public List<PublicHoliday> createHoliday(Map<String, String> body) {
        if (body.get("date") == null || body.get("date").isBlank())
            throw new IllegalArgumentException("Date is required");
        if (body.get("name") == null || body.get("name").isBlank())
            throw new IllegalArgumentException("Name is required");

        String startDate = body.get("date");
        String endDateStr = body.get("endDate");
        String name = body.get("name").trim();
        String description = body.get("description");

        List<String> dates = new ArrayList<>();
        if (endDateStr != null && !endDateStr.isBlank() && !endDateStr.equals(startDate)) {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDateStr);
            if (end.isBefore(start))
                throw new IllegalArgumentException("End date must be on or after the start date");
            for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
                dates.add(cursor.toString());
            }
        } else {
            dates.add(startDate);
        }

        List<PublicHoliday> saved = new ArrayList<>();
        for (String date : dates) {
            PublicHoliday holiday = PublicHoliday.builder()
                    .holidayDate(date)
                    .name(name)
                    .description(description)
                    .year(Integer.parseInt(date.substring(0, 4)))
                    .build();
            saved.add(publicHolidayRepository.save(holiday));
        }
        return saved;
    }

    public PublicHoliday updateHoliday(Long id, Map<String, String> body) {
        if (body.get("date") == null || body.get("date").isBlank())
            throw new IllegalArgumentException("Date is required");
        if (body.get("name") == null || body.get("name").isBlank())
            throw new IllegalArgumentException("Name is required");

        PublicHoliday holiday = publicHolidayRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Holiday not found"));
        String date = body.get("date");
        holiday.setHolidayDate(date);
        holiday.setName(body.get("name").trim());
        holiday.setDescription(body.get("description"));
        holiday.setYear(Integer.parseInt(date.substring(0, 4)));
        return publicHolidayRepository.save(holiday);
    }

    public void deleteHoliday(Long id) {
        publicHolidayRepository.deleteById(id);
    }
}

package com.mytadika.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "public_holidays")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PublicHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, unique = true, length = 10)
    private String holidayDate;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer year;
}

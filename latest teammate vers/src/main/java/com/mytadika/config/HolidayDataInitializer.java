package com.mytadika.config;

import com.mytadika.model.PublicHoliday;
import com.mytadika.repository.PublicHolidayRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class HolidayDataInitializer implements ApplicationRunner {

    private final PublicHolidayRepository repo;

    public HolidayDataInitializer(PublicHolidayRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.count() > 0) return; // already seeded

        List<PublicHoliday> holidays = new ArrayList<>(List.of(
            // ── 2025 ──────────────────────────────────────────────────────────
            h("2025-01-01", "New Year's Day",
                "New Year celebration. Malaysia observes 1 January as a national public holiday.", 2025),
            h("2025-01-29", "Chinese New Year",
                "First day of the Year of the Snake. Celebrated with lion dances, fireworks, ang pau and family reunions.", 2025),
            h("2025-01-30", "Chinese New Year (Day 2)",
                "Second day of Lunar New Year celebrations across Malaysia.", 2025),
            h("2025-02-01", "Federal Territory Day",
                "Observed in Kuala Lumpur, Putrajaya and Labuan only — marking their proclamation as Federal Territories.", 2025),
            h("2025-02-11", "Thaipusam",
                "Hindu festival honouring Lord Murugan. Tamil devotees carry elaborate kavadi as a form of devotion and thanksgiving.", 2025),
            h("2025-03-31", "Hari Raya Aidilfitri",
                "Marks the end of Ramadan (fasting month). Celebrated with morning prayers, open houses and family gatherings. Date subject to moon sighting (±1 day).", 2025),
            h("2025-04-01", "Hari Raya Aidilfitri (Day 2)",
                "Second day of Eid celebrations. Date subject to moon sighting.", 2025),
            h("2025-05-01", "Workers' Day",
                "International Labour Day — a public holiday honouring workers and the labour movement worldwide.", 2025),
            h("2025-05-12", "Wesak Day",
                "Buddhist festival commemorating the birth, enlightenment and parinirvana of Gautama Buddha.", 2025),
            h("2025-06-02", "Yang di-Pertuan Agong's Birthday",
                "Official birthday celebration of Malaysia's King (Yang di-Pertuan Agong XVI, Sultan Ibrahim of Johor).", 2025),
            h("2025-06-07", "Hari Raya Aidiladha",
                "Festival of Sacrifice (Eid al-Adha) — commemorating Prophet Ibrahim's willingness to sacrifice his son. Date subject to moon sighting.", 2025),
            h("2025-06-27", "Awal Muharram",
                "Islamic New Year (1 Muharram 1447H) — marks the beginning of the Islamic lunar calendar. Date subject to moon sighting.", 2025),
            h("2025-08-31", "National Day (Hari Merdeka)",
                "Celebrates Malaysia's independence from British colonial rule declared on 31 August 1957.", 2025),
            h("2025-09-05", "Maulidur Rasul",
                "Birthday of the Prophet Muhammad ﷺ (12 Rabi' al-Awwal 1447H). Date subject to moon sighting.", 2025),
            h("2025-09-16", "Malaysia Day",
                "Commemorates the formation of Malaysia on 16 September 1963, uniting Malaya, Singapore, North Borneo and Sarawak.", 2025),
            h("2025-10-20", "Deepavali",
                "Festival of Lights celebrated by the Hindu community, symbolising the victory of light over darkness and good over evil.", 2025),
            h("2025-12-25", "Christmas Day",
                "Christian holiday celebrating the birth of Jesus Christ. Widely observed across Malaysia.", 2025),

            // ── 2026 ──────────────────────────────────────────────────────────
            h("2026-01-01", "New Year's Day",
                "New Year celebration.", 2026),
            h("2026-01-30", "Thaipusam",
                "Hindu festival — devotees carry kavadi to honour Lord Murugan.", 2026),
            h("2026-02-17", "Chinese New Year",
                "First day of the Year of the Horse. Celebrated with family, fireworks and traditional customs.", 2026),
            h("2026-02-18", "Chinese New Year (Day 2)",
                "Second day of Lunar New Year celebrations.", 2026),
            h("2026-03-20", "Hari Raya Aidilfitri",
                "Eid ul-Fitr — end of Ramadan. Date subject to moon sighting.", 2026),
            h("2026-03-21", "Hari Raya Aidilfitri (Day 2)",
                "Second day of Eid celebrations. Date subject to moon sighting.", 2026),
            h("2026-05-01", "Workers' Day",
                "International Labour Day.", 2026),
            h("2026-05-28", "Hari Raya Aidiladha",
                "Festival of Sacrifice. Date subject to moon sighting.", 2026),
            h("2026-05-30", "Wesak Day",
                "Buddhist festival of the Buddha.", 2026),
            h("2026-06-01", "Yang di-Pertuan Agong's Birthday",
                "Official birthday of Malaysia's King.", 2026),
            h("2026-06-17", "Awal Muharram",
                "Islamic New Year 1448H. Date subject to moon sighting.", 2026),
            h("2026-08-31", "National Day (Hari Merdeka)",
                "Celebrates Malaysia's independence.", 2026),
            h("2026-09-16", "Malaysia Day",
                "Commemorates the formation of Malaysia.", 2026),
            h("2026-11-07", "Deepavali",
                "Festival of Lights.", 2026),
            h("2026-12-25", "Christmas Day",
                "Christian holiday celebrating the birth of Jesus Christ.", 2026)
        ));

        repo.saveAll(holidays);
        System.out.println("[HolidayDataInitializer] Seeded " + holidays.size() + " Malaysia public holidays.");
    }

    private PublicHoliday h(String date, String name, String description, int year) {
        return PublicHoliday.builder()
                .holidayDate(date)
                .name(name)
                .description(description)
                .year(year)
                .build();
    }
}

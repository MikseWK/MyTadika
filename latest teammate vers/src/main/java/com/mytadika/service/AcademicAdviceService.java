package com.mytadika.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Static, templated advice for the parent-facing Interest/Strength Area card —
 * mirrors HealthAdviceService's card-based pattern (title/body suggestions),
 * but simpler: a fixed set of domain-specific tips selected by *why* a domain
 * landed where it did, not a model prediction. Every domain gets advice
 * regardless of level, phrased as encouragement (Strength), gentle exposure
 * (Developing), or a concrete next step (Emerging) — never as a deficit.
 */
@Service
public class AcademicAdviceService {

    // Matches AcademicPredictionClient's COMPLETION_LOW threshold — kept as its
    // own constant here since advice-selection and label-assignment are
    // different concerns that happen to agree on the same cutoff today.
    private static final double COMPLETION_LOW = 0.5;

    public record AdviceCard(String title, String body) {}

    public List<AdviceCard> getAdvice(String domain, String level, String trend, double completionRate) {
        if ("Strength".equals(level)) {
            return STRENGTH_TIPS.getOrDefault(domain, List.of());
        }
        if ("Emerging".equals(level)) {
            if (completionRate < COMPLETION_LOW) {
                return LOW_COMPLETION_TIPS.getOrDefault(domain, List.of());
            }
            if ("declining".equals(trend)) {
                return DECLINING_TIPS.getOrDefault(domain, List.of());
            }
            return EXPOSURE_TIPS.getOrDefault(domain, List.of());
        }
        return DEVELOPING_TIPS.getOrDefault(domain, List.of());
    }

    private static final Map<String, List<AdviceCard>> STRENGTH_TIPS = Map.of(
            "language_literacy", List.of(
                    new AdviceCard("Keep the reading habit alive", "Keep reading together every day — let them pick the book sometimes to keep it fun."),
                    new AdviceCard("Encourage storytelling", "Ask them to retell a story in their own words, or make up their own — it builds on a real strength.")),
            "stem_logic", List.of(
                    new AdviceCard("Feed the curiosity", "Keep exploring hands-on math and science — counting games, building blocks, simple experiments."),
                    new AdviceCard("Let them explain their thinking", "Ask 'how did you figure that out?' — explaining strengthens the skill further.")),
            "creative_arts", List.of(
                    new AdviceCard("Keep the creative outlets open", "Keep art, music, or craft materials easily accessible — this is clearly something they enjoy and are good at."),
                    new AdviceCard("Consider an enrichment activity", "A dance, music, or art class could give this strength more room to grow.")),
            "physical_movement", List.of(
                    new AdviceCard("Keep them active", "Keep encouraging active play, sports, or outdoor games — they're doing well here."),
                    new AdviceCard("Consider a structured activity", "Swimming, dance, or a sports class could build on this strength.")));

    private static final Map<String, List<AdviceCard>> DEVELOPING_TIPS = Map.of(
            "language_literacy", List.of(
                    new AdviceCard("Keep up the daily reading", "A short reading session each day helps steady, well-rounded progress continue."),
                    new AdviceCard("Introduce new words naturally", "Point out new words during everyday conversations, not just during homework.")),
            "stem_logic", List.of(
                    new AdviceCard("Practice through everyday moments", "Count and measure things together while cooking or shopping — low-pressure practice adds up."),
                    new AdviceCard("Try simple puzzles together", "Shape-sorting, building blocks, or simple puzzles keep logic skills developing steadily.")),
            "creative_arts", List.of(
                    new AdviceCard("Offer variety", "Rotate between drawing, music, and simple crafts to keep creative interest fresh."),
                    new AdviceCard("Celebrate the process", "Praise the effort and ideas, not just the finished result — it keeps confidence growing.")),
            "physical_movement", List.of(
                    new AdviceCard("Keep regular active play", "Daily outdoor play or simple ball games help maintain steady progress."),
                    new AdviceCard("Try something new occasionally", "A new game or activity each week keeps movement fun rather than routine.")));

    private static final Map<String, List<AdviceCard>> LOW_COMPLETION_TIPS = Map.of(
            "language_literacy", List.of(
                    new AdviceCard("Set a short daily reading routine", "Even 10 minutes at the same time each day makes reading homework feel manageable."),
                    new AdviceCard("Break it into smaller pieces", "Split reading tasks into shorter chunks rather than one long session.")),
            "stem_logic", List.of(
                    new AdviceCard("Set a regular homework spot and time", "A quiet, consistent time and place makes it easier to get math practice done."),
                    new AdviceCard("Keep sessions short and frequent", "A few short practice sessions often work better than one long one.")),
            "creative_arts", List.of(
                    new AdviceCard("Schedule dedicated creative time", "Set aside a regular slot for art or craft projects so they don't get missed."),
                    new AdviceCard("Keep materials within easy reach", "Having supplies ready to go lowers the barrier to starting.")),
            "physical_movement", List.of(
                    new AdviceCard("Build in daily active time", "Even 20-30 minutes of active play at a consistent time each day helps build the habit."),
                    new AdviceCard("Make it part of the routine", "Tying movement to an existing routine (after school, before dinner) makes it easier to stick to.")));

    private static final Map<String, List<AdviceCard>> DECLINING_TIPS = Map.of(
            "language_literacy", List.of(
                    new AdviceCard("Revisit recent material together", "Go back over recent stories or spelling lists together to rebuild footing."),
                    new AdviceCard("Check in with the teacher", "Ask which specific reading or writing skills could use reinforcing at home.")),
            "stem_logic", List.of(
                    new AdviceCard("Review recent topics with simple examples", "Revisiting a recent concept with everyday examples can rebuild confidence."),
                    new AdviceCard("Check in with the teacher", "Ask which specific topic the recent classwork covered, so home practice lines up.")),
            "creative_arts", List.of(
                    new AdviceCard("Revisit a favourite activity", "Going back to an activity they've previously enjoyed can help rebuild enthusiasm."),
                    new AdviceCard("Ask what excites them most", "Let them choose the next creative activity — renewed interest often follows renewed choice.")),
            "physical_movement", List.of(
                    new AdviceCard("Try a low-pressure activity together", "A fun, no-stakes physical activity together can help rebuild enjoyment."),
                    new AdviceCard("Check for a specific barrier", "Ask if something specific (comfort, space, a friend not around) is getting in the way.")));

    private static final Map<String, List<AdviceCard>> EXPOSURE_TIPS = Map.of(
            "language_literacy", List.of(
                    new AdviceCard("Read together every night", "Regular shared reading is one of the simplest ways to build this area at home."),
                    new AdviceCard("Play word games", "Simple rhyming games or 'I Spy' build vocabulary without feeling like homework.")),
            "stem_logic", List.of(
                    new AdviceCard("Weave in everyday counting", "Counting steps, measuring ingredients, or sorting objects builds number sense naturally."),
                    new AdviceCard("Try building or puzzle toys", "Blocks, shape-sorters, and simple puzzles build logic skills through play.")),
            "creative_arts", List.of(
                    new AdviceCard("Offer simple art supplies for free play", "Crayons, paper, or clay for unstructured play often sparks more engagement than structured lessons."),
                    new AdviceCard("Encourage imaginative play", "Storytelling through drawing or pretend play builds creative confidence.")),
            "physical_movement", List.of(
                    new AdviceCard("Encourage outdoor play", "Running, climbing, or simple ball games build comfort with movement."),
                    new AdviceCard("Practice balance and coordination", "Simple games like hopscotch or catch build coordination in a low-pressure way.")));
}

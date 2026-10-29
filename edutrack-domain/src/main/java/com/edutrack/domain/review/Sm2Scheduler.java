package com.edutrack.domain.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The SM-2 algorithm (Wozniak, 1987), the scheduler behind most flashcard apps.
 *
 * <ul>
 *   <li>Ease changes after every review by {@code 0.1 − (5 − q) × (0.08 + (5 − q) × 0.02)}, so
 *       Easy adds 0.10, Good keeps it, Hard removes 0.14 and Again removes 0.54. It never drops
 *       below 1.3.</li>
 *   <li>A pass (quality ≥ 3) schedules the card 1 day out, then 6 days, then the previous interval
 *       times the new ease, always at least one day longer than before.</li>
 *   <li>A lapse (Again) resets the repetitions and keeps the card due today, so the review session
 *       shows it again before it ends — SM-2's "repeat until recalled" rule. Forgetting a card that
 *       was already learned counts as a lapse.</li>
 * </ul>
 */
public final class Sm2Scheduler {

    public static final BigDecimal INITIAL_EASE = new BigDecimal("2.50");
    public static final BigDecimal MIN_EASE = new BigDecimal("1.30");

    private static final int FIRST_INTERVAL = 1;
    private static final int SECOND_INTERVAL = 6;

    private Sm2Scheduler() {
    }

    public static CardSchedule next(CardSchedule current, Grade grade, LocalDate today) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(today, "today");
        BigDecimal ease = nextEase(current.ease(), grade.quality());

        if (grade.isLapse()) {
            int lapses = current.repetitions() > 0 ? current.lapses() + 1 : current.lapses();
            return new CardSchedule(ease, 0, 0, lapses, today, today);
        }

        int repetitions = current.repetitions() + 1;
        int interval = switch (repetitions) {
            case 1 -> FIRST_INTERVAL;
            case 2 -> SECOND_INTERVAL;
            default -> Math.max(current.intervalDays() + 1, BigDecimal.valueOf(current.intervalDays())
                    .multiply(ease).setScale(0, RoundingMode.HALF_UP).intValueExact());
        };
        return new CardSchedule(ease, interval, repetitions, current.lapses(), today.plusDays(interval), today);
    }

    static BigDecimal nextEase(BigDecimal ease, int quality) {
        int miss = 5 - quality;
        BigDecimal change = new BigDecimal("0.1")
                .subtract(BigDecimal.valueOf(miss).multiply(new BigDecimal("0.08")
                        .add(BigDecimal.valueOf(miss).multiply(new BigDecimal("0.02")))));
        BigDecimal next = ease.add(change);
        return next.compareTo(MIN_EASE) < 0 ? MIN_EASE : next.setScale(2, RoundingMode.HALF_UP);
    }
}

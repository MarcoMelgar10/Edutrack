package com.edutrack.domain.review;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Sm2SchedulerTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 10, 27);

    private static CardSchedule next(CardSchedule schedule, Grade grade, LocalDate today) {
        return Sm2Scheduler.next(schedule, grade, today);
    }

    private static BigDecimal ease(String value) {
        return new BigDecimal(value);
    }

    @Test
    void aNewCardIsNewWithTheDefaultEaseAndDueToday() {
        CardSchedule fresh = CardSchedule.newCard(DAY_1);

        assertThat(fresh.isNew()).isTrue();
        assertThat(fresh.ease()).isEqualTo(ease("2.50"));
        assertThat(fresh.repetitions()).isZero();
        assertThat(fresh.lapses()).isZero();
        assertThat(fresh.dueOn()).isEqualTo(DAY_1);
        assertThat(fresh.isDueOn(DAY_1)).isTrue();
        assertThat(fresh.isDueOn(DAY_1.minusDays(1))).isFalse();
    }

    @Test
    void firstGoodReviewSchedulesTomorrow() {
        CardSchedule schedule = next(CardSchedule.newCard(DAY_1), Grade.GOOD, DAY_1);

        assertThat(schedule.ease()).isEqualTo(ease("2.50"));
        assertThat(schedule.repetitions()).isEqualTo(1);
        assertThat(schedule.intervalDays()).isEqualTo(1);
        assertThat(schedule.dueOn()).isEqualTo(DAY_1.plusDays(1));
        assertThat(schedule.lastReviewedOn()).isEqualTo(DAY_1);
        assertThat(schedule.isNew()).isFalse();
    }

    @Test
    void goodRepeatedGivesOneThenSixThenFifteenDays() {
        CardSchedule first = next(CardSchedule.newCard(DAY_1), Grade.GOOD, DAY_1);
        CardSchedule second = next(first, Grade.GOOD, DAY_1.plusDays(1));
        CardSchedule third = next(second, Grade.GOOD, DAY_1.plusDays(7));

        assertThat(second.intervalDays()).isEqualTo(6);
        assertThat(second.dueOn()).isEqualTo(DAY_1.plusDays(7));
        assertThat(third.intervalDays()).isEqualTo(15);
        assertThat(third.repetitions()).isEqualTo(3);
        assertThat(third.dueOn()).isEqualTo(DAY_1.plusDays(22));
        assertThat(third.ease()).isEqualTo(ease("2.50"));
    }

    @Test
    void easyOnANewCardRaisesTheEaseByOneTenth() {
        CardSchedule schedule = next(CardSchedule.newCard(DAY_1), Grade.EASY, DAY_1);

        assertThat(schedule.ease()).isEqualTo(ease("2.60"));
        assertThat(schedule.intervalDays()).isEqualTo(1);
    }

    @Test
    void hardLowersTheEaseByFourteenHundredths() {
        CardSchedule schedule = next(CardSchedule.newCard(DAY_1), Grade.HARD, DAY_1);

        assertThat(schedule.ease()).isEqualTo(ease("2.36"));
        assertThat(schedule.repetitions()).isEqualTo(1);
        assertThat(schedule.intervalDays()).isEqualTo(1);
    }

    @Test
    void againLowersTheEaseByFiftyFourHundredthsAndKeepsTheCardDueToday() {
        CardSchedule schedule = next(CardSchedule.newCard(DAY_1), Grade.AGAIN, DAY_1);

        assertThat(schedule.ease()).isEqualTo(ease("1.96"));
        assertThat(schedule.repetitions()).isZero();
        assertThat(schedule.intervalDays()).isZero();
        assertThat(schedule.dueOn()).isEqualTo(DAY_1);
        assertThat(schedule.lastReviewedOn()).isEqualTo(DAY_1);
    }

    @Test
    void forgettingANewCardIsNotALapse() {
        CardSchedule schedule = next(CardSchedule.newCard(DAY_1), Grade.AGAIN, DAY_1);

        assertThat(schedule.lapses()).isZero();
        assertThat(schedule.isNew()).isFalse();
    }

    @Test
    void forgettingALearnedCardIsALapseAndResetsTheRepetitions() {
        CardSchedule learned = next(next(CardSchedule.newCard(DAY_1), Grade.GOOD, DAY_1), Grade.GOOD, DAY_1.plusDays(1));

        CardSchedule lapsed = next(learned, Grade.AGAIN, DAY_1.plusDays(7));

        assertThat(lapsed.lapses()).isEqualTo(1);
        assertThat(lapsed.repetitions()).isZero();
        assertThat(lapsed.intervalDays()).isZero();
        assertThat(lapsed.dueOn()).isEqualTo(DAY_1.plusDays(7));
    }

    @Test
    void afterALapseTheCardStartsOverWithOneThenSixDays() {
        CardSchedule learned = next(next(CardSchedule.newCard(DAY_1), Grade.GOOD, DAY_1), Grade.GOOD, DAY_1.plusDays(1));
        CardSchedule lapsed = next(learned, Grade.AGAIN, DAY_1.plusDays(7));

        CardSchedule relearn1 = next(lapsed, Grade.GOOD, DAY_1.plusDays(7));
        CardSchedule relearn2 = next(relearn1, Grade.GOOD, DAY_1.plusDays(8));

        assertThat(relearn1.intervalDays()).isEqualTo(1);
        assertThat(relearn2.intervalDays()).isEqualTo(6);
        assertThat(relearn2.lapses()).isEqualTo(1);
    }

    @Test
    void easeNeverDropsBelowOnePointThree() {
        CardSchedule schedule = CardSchedule.newCard(DAY_1);
        for (int i = 0; i < 10; i++) {
            schedule = next(schedule, Grade.AGAIN, DAY_1);
        }

        assertThat(schedule.ease()).isEqualTo(ease("1.30"));
    }

    @Test
    void intervalAlwaysGrowsByAtLeastADayEvenAtMinimumEase() {
        CardSchedule low = new CardSchedule(ease("1.30"), 1, 2, 0, DAY_1, DAY_1.minusDays(1));

        CardSchedule schedule = next(low, Grade.HARD, DAY_1);

        // 1 day × 1.30 rounds to 1, which would not grow: the minimum step is one more day.
        assertThat(schedule.repetitions()).isEqualTo(3);
        assertThat(schedule.intervalDays()).isEqualTo(2);
        assertThat(schedule.ease()).isEqualTo(ease("1.30"));
    }

    @Test
    void laterIntervalsMultiplyByTheNewEase() {
        CardSchedule weekOld = new CardSchedule(ease("2.50"), 10, 4, 0, DAY_1, DAY_1.minusDays(10));

        assertThat(next(weekOld, Grade.EASY, DAY_1).intervalDays()).isEqualTo(26);   // 10 × 2.60
        assertThat(next(weekOld, Grade.HARD, DAY_1).intervalDays()).isEqualTo(24);   // 10 × 2.36
        assertThat(next(weekOld, Grade.GOOD, DAY_1).intervalDays()).isEqualTo(25);
    }

    @Test
    void gradesMapToSm2Qualities() {
        assertThat(Grade.AGAIN.quality()).isEqualTo(1);
        assertThat(Grade.HARD.quality()).isEqualTo(3);
        assertThat(Grade.GOOD.quality()).isEqualTo(4);
        assertThat(Grade.EASY.quality()).isEqualTo(5);
        assertThat(Grade.AGAIN.isLapse()).isTrue();
        assertThat(Grade.HARD.isLapse()).isFalse();
    }

    @Test
    void scheduleRejectsEaseBelowTheMinimumAndNegativeCounts() {
        assertThatThrownBy(() -> new CardSchedule(ease("1.29"), 0, 0, 0, DAY_1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CardSchedule(ease("2.50"), -1, 0, 0, DAY_1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.edutrack.domain.measure;

import com.edutrack.domain.measure.MeasureSeries.Entry;
import com.edutrack.domain.measure.MeasureSeries.Point;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MeasureSeriesTest {

    private static final LocalDate MARCH_1 = LocalDate.of(2026, 3, 1);
    private static final LocalDate MARCH_8 = LocalDate.of(2026, 3, 8);

    private static Entry entry(LocalDate day, String score, int order) {
        return new Entry(day, new BigDecimal(score), order);
    }

    private static BigDecimal value(String text) {
        return new BigDecimal(text);
    }

    @Test
    void averagesThePartsOfAnAttemptOnTheSameDay() {
        List<Entry> ielts = List.of(entry(MARCH_1, "6.5", 0), entry(MARCH_1, "7.0", 1),
                entry(MARCH_1, "6.0", 2), entry(MARCH_1, "6.5", 3));

        List<Point> points = MeasureSeries.points(ielts, CombineRule.AVERAGE);

        assertThat(points).containsExactly(new Point(MARCH_1, value("6.50"), 4));
    }

    @Test
    void averageIsRoundedToTwoDecimals() {
        List<Entry> parts = List.of(entry(MARCH_1, "1", 0), entry(MARCH_1, "1", 1), entry(MARCH_1, "2", 2));

        assertThat(MeasureSeries.points(parts, CombineRule.AVERAGE)).extracting(Point::value)
                .containsExactly(value("1.33"));
    }

    @Test
    void sumsThePartsOfAnAttempt() {
        List<Entry> parts = List.of(entry(MARCH_1, "30.5", 0), entry(MARCH_1, "40", 1), entry(MARCH_1, "25.25", 2));

        assertThat(MeasureSeries.points(parts, CombineRule.SUM))
                .containsExactly(new Point(MARCH_1, value("95.75"), 3));
    }

    @Test
    void noneKeepsTheLastEnteredResultOfTheDay() {
        List<Entry> parts = List.of(entry(MARCH_1, "10", 2), entry(MARCH_1, "99", 5), entry(MARCH_1, "50", 3));

        assertThat(MeasureSeries.points(parts, CombineRule.NONE))
                .containsExactly(new Point(MARCH_1, value("99.00"), 3));
    }

    @Test
    void pointsAreSortedByDayWhateverTheInputOrder() {
        List<Entry> entries = List.of(entry(MARCH_8, "7", 0), entry(MARCH_1, "6", 1),
                entry(LocalDate.of(2026, 3, 4), "6.5", 2));

        assertThat(MeasureSeries.points(entries, CombineRule.NONE)).extracting(Point::on)
                .containsExactly(MARCH_1, LocalDate.of(2026, 3, 4), MARCH_8);
    }

    @Test
    void separateDaysAreSeparateAttempts() {
        List<Entry> entries = List.of(entry(MARCH_1, "6", 0), entry(MARCH_1, "7", 1), entry(MARCH_8, "8", 2));

        assertThat(MeasureSeries.points(entries, CombineRule.AVERAGE)).extracting(Point::value)
                .containsExactly(value("6.50"), value("8.00"));
    }

    @Test
    void latestIsTheNewestDay() {
        List<Entry> entries = List.of(entry(MARCH_8, "7", 0), entry(MARCH_1, "9", 1));

        assertThat(MeasureSeries.latest(entries, CombineRule.NONE)).contains(new Point(MARCH_8, value("7.00"), 1));
    }

    @Test
    void bestIsTheHighestWhenHigherIsBetter() {
        List<Entry> entries = List.of(entry(MARCH_1, "5", 0), entry(MARCH_8, "7", 1), entry(LocalDate.of(2026, 3, 15), "6", 2));

        assertThat(MeasureSeries.best(entries, CombineRule.NONE, Direction.HIGHER_IS_BETTER))
                .hasValueSatisfying(point -> assertThat(point.on()).isEqualTo(MARCH_8));
    }

    @Test
    void bestIsTheLowestWhenLowerIsBetter() {
        List<Entry> times = List.of(entry(MARCH_1, "90", 0), entry(MARCH_8, "75", 1), entry(LocalDate.of(2026, 3, 15), "80", 2));

        assertThat(MeasureSeries.best(times, CombineRule.NONE, Direction.LOWER_IS_BETTER))
                .hasValueSatisfying(point -> assertThat(point.value()).isEqualTo(value("75.00")));
    }

    @Test
    void bestIsComputedOnCombinedAttemptsNotOnSingleParts() {
        // March 1 has a 9 part but averages 5; March 8 averages 6.
        List<Entry> entries = List.of(entry(MARCH_1, "9", 0), entry(MARCH_1, "1", 1), entry(MARCH_8, "6", 2));

        assertThat(MeasureSeries.best(entries, CombineRule.AVERAGE, Direction.HIGHER_IS_BETTER))
                .hasValueSatisfying(point -> assertThat(point.on()).isEqualTo(MARCH_8));
    }

    @Test
    void meetsTargetWhenHigherIsBetter() {
        assertThat(MeasureSeries.meets(value("7.0"), value("7"), Direction.HIGHER_IS_BETTER)).isTrue();
        assertThat(MeasureSeries.meets(value("7.5"), value("7"), Direction.HIGHER_IS_BETTER)).isTrue();
        assertThat(MeasureSeries.meets(value("6.5"), value("7"), Direction.HIGHER_IS_BETTER)).isFalse();
    }

    @Test
    void meetsTargetWhenLowerIsBetter() {
        assertThat(MeasureSeries.meets(value("60"), value("60"), Direction.LOWER_IS_BETTER)).isTrue();
        assertThat(MeasureSeries.meets(value("55"), value("60"), Direction.LOWER_IS_BETTER)).isTrue();
        assertThat(MeasureSeries.meets(value("61"), value("60"), Direction.LOWER_IS_BETTER)).isFalse();
    }

    @Test
    void noEntriesMeansNoPoints() {
        assertThat(MeasureSeries.points(List.of(), CombineRule.AVERAGE)).isEmpty();
        assertThat(MeasureSeries.latest(List.of(), CombineRule.NONE)).isEmpty();
        assertThat(MeasureSeries.best(List.of(), CombineRule.NONE, Direction.LOWER_IS_BETTER)).isEmpty();
    }
}

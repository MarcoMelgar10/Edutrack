package com.edutrack.domain.measure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Turns the results recorded against a measure into one value per attempt, for charts and the
 * "latest vs target" line. Results taken on the same day are parts of one attempt (the four
 * sections of a mock exam): {@link CombineRule#AVERAGE} and {@link CombineRule#SUM} merge them;
 * with {@link CombineRule#NONE} the last one entered that day stands for the day.
 */
public final class MeasureSeries {

    /** One recorded result: its day, its score and its entry order (later entries win under NONE). */
    public record Entry(LocalDate takenOn, BigDecimal score, int order) {

        public Entry {
            Objects.requireNonNull(takenOn, "takenOn");
            Objects.requireNonNull(score, "score");
        }
    }

    public record Point(LocalDate on, BigDecimal value, int parts) {
    }

    private MeasureSeries() {
    }

    /** One point per day, oldest first, values with two decimals. */
    public static List<Point> points(Collection<Entry> entries, CombineRule rule) {
        Objects.requireNonNull(rule, "rule");
        Map<LocalDate, List<Entry>> byDay = new TreeMap<>();
        for (Entry entry : entries) {
            byDay.computeIfAbsent(entry.takenOn(), day -> new ArrayList<>()).add(entry);
        }
        List<Point> points = new ArrayList<>();
        byDay.forEach((day, parts) -> points.add(new Point(day, combine(parts, rule), parts.size())));
        return points;
    }

    public static Optional<Point> latest(Collection<Entry> entries, CombineRule rule) {
        List<Point> points = points(entries, rule);
        return points.isEmpty() ? Optional.empty() : Optional.of(points.getLast());
    }

    /** The best attempt so far: highest, or lowest when lower is better (a time, an error count). */
    public static Optional<Point> best(Collection<Entry> entries, CombineRule rule, Direction direction) {
        Comparator<Point> byValue = Comparator.comparing(Point::value);
        return points(entries, rule).stream()
                .max(direction == Direction.LOWER_IS_BETTER ? byValue.reversed() : byValue);
    }

    /** Whether {@code value} reaches {@code target} in the measure's direction. */
    public static boolean meets(BigDecimal value, BigDecimal target, Direction direction) {
        int comparison = value.compareTo(target);
        return direction == Direction.LOWER_IS_BETTER ? comparison <= 0 : comparison >= 0;
    }

    private static BigDecimal combine(List<Entry> parts, CombineRule rule) {
        return switch (rule) {
            case NONE -> parts.stream().max(Comparator.comparingInt(Entry::order)).orElseThrow().score()
                    .setScale(2, RoundingMode.HALF_UP);
            case SUM -> sum(parts).setScale(2, RoundingMode.HALF_UP);
            case AVERAGE -> sum(parts).divide(BigDecimal.valueOf(parts.size()), 2, RoundingMode.HALF_UP);
        };
    }

    private static BigDecimal sum(List<Entry> parts) {
        return parts.stream().map(Entry::score).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

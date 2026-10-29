package com.edutrack.application.measure;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.measure.Measure;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.measure.MeasureSeries;
import com.edutrack.domain.measure.MeasureSeries.Point;
import com.edutrack.domain.record.MeasuredResult;
import com.edutrack.domain.skill.CompetencyId;

import java.math.BigDecimal;
import java.util.List;

/**
 * A measure with its series, as the measures panel and its chart show it. {@code points} has one
 * value per attempt (day), oldest first; {@code latest} and {@code best} are {@code null} while
 * nothing is recorded. {@code meetsTarget} is whether the latest attempt reaches the target (false
 * without a target or without results). {@code resultCount} counts every recorded result, parts included.
 */
public record MeasureView(MeasureId id, GoalId goalId, String name, String unit, BigDecimal maxValue,
                          BigDecimal target, Direction direction, CombineRule combineRule, CompetencyId competencyId,
                          int position, List<Point> points, Point latest, Point best, boolean meetsTarget,
                          int resultCount) {

    static MeasureView of(Measure measure, List<MeasuredResult> results) {
        List<MeasureSeries.Entry> entries = results.stream()
                .map(result -> new MeasureSeries.Entry(result.takenOn(), result.score(), result.position()))
                .toList();
        List<Point> points = MeasureSeries.points(entries, measure.combineRule());
        Point latest = points.isEmpty() ? null : points.getLast();
        Point best = MeasureSeries.best(entries, measure.combineRule(), measure.direction()).orElse(null);
        boolean meetsTarget = latest != null && measure.target() != null
                && MeasureSeries.meets(latest.value(), measure.target(), measure.direction());
        return new MeasureView(measure.id(), measure.goalId(), measure.name(), measure.unit(), measure.maxValue(),
                measure.target(), measure.direction(), measure.combineRule(), measure.competencyId(),
                measure.position(), points, latest, best, meetsTarget, results.size());
    }
}

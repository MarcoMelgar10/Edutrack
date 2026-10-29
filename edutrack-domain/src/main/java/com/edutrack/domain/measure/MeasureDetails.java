package com.edutrack.domain.measure;

import com.edutrack.domain.skill.CompetencyId;

import java.math.BigDecimal;

/**
 * What a goal measures, as typed in the measure dialog: "IELTS band", out of 9, target 7, the
 * parts averaged. {@code direction} and {@code combineRule} fall back to their defaults when
 * {@code null}. Validated by {@link Measure}.
 */
public record MeasureDetails(String name, String unit, BigDecimal maxValue, BigDecimal target, Direction direction,
                             CombineRule combineRule, CompetencyId competencyId) {
}

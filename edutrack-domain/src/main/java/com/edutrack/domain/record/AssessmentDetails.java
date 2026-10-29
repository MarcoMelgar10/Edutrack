package com.edutrack.domain.record;

import com.edutrack.domain.measure.MeasureId;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A formal result as typed in the Result dialog, e.g. "IELTS mock test", 6.5 of 9, taken on 12 Oct 2026.
 * {@code measureId} and {@code part} are optional: they file the result under a goal's measure, as one
 * part of an attempt ("Listening"). Validated by {@link Assessment}.
 */
public record AssessmentDetails(String title, BigDecimal score, BigDecimal maxScore, LocalDate takenOn, String note,
                                MeasureId measureId, String part) {

    /** A result that is not part of any measure. */
    public AssessmentDetails(String title, BigDecimal score, BigDecimal maxScore, LocalDate takenOn, String note) {
        this(title, score, maxScore, takenOn, note, null, null);
    }
}

package com.edutrack.domain.record;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One result recorded against a measure, read across all learning records: where it lives
 * ({@code owner}), what it says and its {@code position} within that record (entry order).
 */
public record MeasuredResult(RecordOwner owner, AssessmentId id, String title, String part, BigDecimal score,
                             LocalDate takenOn, int position) {
}

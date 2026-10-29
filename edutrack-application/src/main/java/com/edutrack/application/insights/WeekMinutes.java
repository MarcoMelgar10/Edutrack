package com.edutrack.application.insights;

import com.edutrack.domain.area.AreaId;

import java.time.LocalDate;
import java.util.Map;

/**
 * Study time in one week, split by area (areas without time are absent, the rest in area order).
 * {@code weekStart} is the first day of the week.
 */
public record WeekMinutes(LocalDate weekStart, Map<AreaId, Integer> minutesByArea, int total) {
}

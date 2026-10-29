package com.edutrack.application.practice;

import com.edutrack.domain.area.AreaId;

/**
 * An active routine on Today. {@code sessionsThisWeek} counts the days of the week with a session
 * for the routine (two sessions on one day count once); {@code metThisWeek} means that count
 * reached the routine's times per week.
 */
public record RoutineStatus(RoutineView routine, String goalTitle, AreaId areaId, int minutesToday,
                            int sessionsThisWeek, boolean doneToday, boolean metThisWeek) {
}

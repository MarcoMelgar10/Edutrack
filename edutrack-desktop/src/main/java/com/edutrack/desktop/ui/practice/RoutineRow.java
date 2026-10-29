package com.edutrack.desktop.ui.practice;

import com.edutrack.application.practice.RoutineView;

/**
 * A practice routine as the goal page lists it, with its cadence and this week's progress already worded.
 * A paused routine has no week progress.
 */
public record RoutineRow(RoutineView routine, String cadenceText, String weekText, boolean doneToday,
                         boolean metThisWeek) {
}

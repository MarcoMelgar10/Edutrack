package com.edutrack.application.session;

import com.edutrack.domain.goal.StepId;

import java.util.Map;

/**
 * Time studied on a goal: in total, per step (only steps with sessions appear) and over the last
 * 7 days, today included. Sessions without a step count for the goal only.
 */
public record SessionTotals(int goalMinutes, Map<StepId, Integer> stepMinutes, int last7DaysMinutes) {
}

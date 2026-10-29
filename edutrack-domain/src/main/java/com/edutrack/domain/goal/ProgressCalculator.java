package com.edutrack.domain.goal;

import com.edutrack.domain.progress.Progress;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * The single place where EduTrack's progress rules live.
 *
 * <ul>
 *   <li><b>Goal</b>: hours of Done steps ÷ hours of all steps. A step without an estimate (or
 *       estimated at 0 h) weighs 1 h, so it still counts. A goal without steps is at 0%.</li>
 *   <li><b>Area</b>: the done hours of all its goals ÷ their total hours, with the same step weights.
 *       A big goal weighs more than a small one, and a goal without steps does not count at all.
 *       An area without hours is at 0%.</li>
 * </ul>
 */
public final class ProgressCalculator {

    static final BigDecimal DEFAULT_STEP_WEIGHT = BigDecimal.ONE;

    private ProgressCalculator() {
    }

    public static Effort effortOf(Collection<RoadmapStep> steps) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal done = BigDecimal.ZERO;
        BigDecimal estimated = BigDecimal.ZERO;
        BigDecimal estimatedDone = BigDecimal.ZERO;
        int unestimated = 0;
        for (RoadmapStep step : steps) {
            BigDecimal weight = weightOf(step);
            boolean isDone = step.status() == StepStatus.DONE;
            total = total.add(weight);
            done = isDone ? done.add(weight) : done;
            if (isEstimated(step)) {
                estimated = estimated.add(step.estimatedHours());
                estimatedDone = isDone ? estimatedDone.add(step.estimatedHours()) : estimatedDone;
            } else {
                unestimated++;
            }
        }
        return new Effort(done, total, estimatedDone, estimated, unestimated);
    }

    public static Progress ofSteps(Collection<RoadmapStep> steps) {
        return effortOf(steps).progress();
    }

    /** An area's progress: the goals' weighted hours added up, so goals without steps add nothing. */
    public static Progress ofGoals(Collection<Effort> goals) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal done = BigDecimal.ZERO;
        for (Effort goal : goals) {
            total = total.add(goal.totalHours());
            done = done.add(goal.doneHours());
        }
        return Progress.ofRatio(done, total);
    }

    static BigDecimal weightOf(RoadmapStep step) {
        return isEstimated(step) ? step.estimatedHours() : DEFAULT_STEP_WEIGHT;
    }

    private static boolean isEstimated(RoadmapStep step) {
        return step.estimatedHours() != null && step.estimatedHours().signum() != 0;
    }

    /**
     * The hours behind a goal's percentage.
     *
     * <p>{@code doneHours}/{@code totalHours} are the weights the percentage is computed from (a step
     * without an estimate counts as 1 h). {@code estimatedDoneHours}/{@code estimatedHours} add up only
     * the hours the learner actually entered, and {@code unestimatedSteps} counts the rest; those are
     * the numbers to show, so the screen never displays hours nobody typed in.
     */
    public record Effort(BigDecimal doneHours, BigDecimal totalHours, BigDecimal estimatedDoneHours,
                         BigDecimal estimatedHours, int unestimatedSteps) {

        public Progress progress() {
            return Progress.ofRatio(doneHours, totalHours);
        }
    }
}

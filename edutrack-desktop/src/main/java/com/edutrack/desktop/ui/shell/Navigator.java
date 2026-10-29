package com.edutrack.desktop.ui.shell;

import com.edutrack.application.goal.GoalService;
import com.edutrack.application.review.ReviewScope;
import com.edutrack.desktop.ui.journal.JournalController;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * How any page sends the learner somewhere else in the window: a page of the rail, an area, a goal
 * or one step of a goal. Inject it where a link or a row should navigate. Calls are ignored until a
 * shell has registered as the {@link Target} (and after a language switch the new shell takes over).
 */
@Component
public class Navigator {

    /** What the shell offers; implemented by {@link ShellController}. */
    public interface Target {
        void showPage(ShellPage page);

        void showArea(AreaId areaId);

        void showGoal(AreaId areaId, GoalId goalId);

        void showStep(AreaId areaId, GoalId goalId, StepId stepId);

        /** The controller of a fixed page that is (or has just been) shown; {@code null} if it was never opened. */
        Object pageController(ShellPage page);
    }

    private final GoalService goalService;
    private Target target;

    public Navigator(GoalService goalService) {
        this.goalService = goalService;
    }

    /** Called by the shell when it is built. */
    public void attach(Target target) {
        this.target = target;
    }

    public void open(ShellPage page) {
        if (target != null) {
            target.showPage(page);
        }
    }

    public void openArea(AreaId areaId) {
        if (target != null) {
            target.showArea(areaId);
        }
    }

    /** Opens the goal's area and selects the goal in it. */
    public void openGoal(GoalId goalId) {
        if (target != null) {
            target.showGoal(goalService.getGoal(goalId).areaId(), goalId);
        }
    }

    // ---- pages that open with a request: the page takes the request when it is shown ----

    private ReviewScope reviewScope;

    /** Opens the Review page on the cards of one area or goal (or all of them). */
    public void openReview(ReviewScope scope) {
        reviewScope = scope;
        open(ShellPage.REVIEW);
    }

    /** The scope given to {@link #openReview(ReviewScope)}, once. */
    public Optional<ReviewScope> takeReviewScope() {
        Optional<ReviewScope> scope = Optional.ofNullable(reviewScope);
        reviewScope = null;
        return scope;
    }

    /** Opens the goal and, on top of it, the page of one of its roadmap steps. */
    public void openStep(GoalId goalId, StepId stepId) {
        if (target != null) {
            target.showStep(goalService.getGoal(goalId).areaId(), goalId, stepId);
        }
    }

    /** Opens the Journal with the weekly check-in editor ready to write. */
    public void openJournalCheckIn() {
        if (target != null) {
            target.showPage(ShellPage.JOURNAL);
            if (target.pageController(ShellPage.JOURNAL) instanceof JournalController journal) {
                journal.startCheckIn();
            }
        }
    }

    /** Opens the Journal with a new note about the goal ready to write. */
    public void openJournalNote(GoalId goalId) {
        if (target != null) {
            target.showPage(ShellPage.JOURNAL);
            if (target.pageController(ShellPage.JOURNAL) instanceof JournalController journal) {
                journal.startNote(goalId);
            }
        }
    }
}

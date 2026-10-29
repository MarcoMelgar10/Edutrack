package com.edutrack.desktop.ui.review;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.application.review.ReviewItem;
import com.edutrack.application.review.ReviewScope;
import com.edutrack.application.review.ReviewService;
import com.edutrack.application.session.SessionCommands;
import com.edutrack.application.session.SessionService;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.review.CardId;
import com.edutrack.domain.review.CardSide;
import com.edutrack.domain.review.Grade;
import com.edutrack.domain.session.SessionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewSessionViewModelTest {

    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-28T10:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final ReviewService reviews = mock(ReviewService.class);
    private final SessionService sessions = mock(SessionService.class);
    private final AreaService areas = mock(AreaService.class);
    private final GoalService goals = mock(GoalService.class);
    private final DataChanges dataChanges = new DataChanges();
    private final TestClock clock = new TestClock();
    private final AreaId areaId = AreaId.newId();
    private final GoalId goalId = GoalId.newId();
    private Preferences node;
    private ReviewSessionViewModel viewModel;

    @BeforeEach
    void setUp() {
        node = Preferences.userRoot().node("edutrack-review-test-" + UUID.randomUUID());
        when(areas.listAreas()).thenReturn(List.of(new AreaSummary(areaId, "English", null, AreaIcon.GLOBE, 1, Progress.ZERO)));
        when(goals.listGoals(areaId)).thenReturn(List.of(new GoalSummary(goalId, areaId, "IELTS", null, Progress.ZERO, 1, 0, false)));
        when(reviews.nextDue(any())).thenReturn(Optional.empty());
        when(reviews.previewIntervals(any(), any())).thenReturn(Map.of(Grade.AGAIN, 0, Grade.HARD, 3, Grade.GOOD, 6, Grade.EASY, 15));
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        node.removeNode();
    }

    private ReviewItem item(String prompt, boolean isNew) {
        return new ReviewItem(CardId.newId(), CardSide.FORWARD, prompt, "answer of " + prompt, "IELTS", "English", isNew,
                goalId);
    }

    private void queueOf(ReviewItem... items) {
        when(reviews.queue(any(), anyInt(), anyInt())).thenReturn(List.of(items));
        viewModel = new ReviewSessionViewModel(reviews, sessions, areas, goals, new UiPreferences(node), dataChanges,
                clock, new Messages());
    }

    @Test
    void theStartPageCountsDueAndNewCards() {
        queueOf(item("a", false), item("b", false), item("c", true));

        assertThat(viewModel.countsTextProperty().get()).isEqualTo("2 due · 1 new");
        assertThat(viewModel.nothingToReviewProperty().get()).isFalse();
        assertThat(viewModel.scopes()).extracting(ReviewSessionViewModel.ScopeOption::label)
                .containsExactly("All subjects", "English", "English · IELTS");
    }

    @Test
    void withNothingWaitingItSaysWhenTheNextCardsAreDue() {
        when(reviews.nextDue(any())).thenReturn(Optional.of(LocalDate.of(2026, 11, 3)));
        queueOf();

        assertThat(viewModel.nothingToReviewProperty().get()).isTrue();
        assertThat(viewModel.nextDueTextProperty().get()).isEqualTo("Next cards are due on 03 Nov 2026.");
        viewModel.start();
        assertThat(viewModel.phaseProperty().get()).isEqualTo(ReviewSessionViewModel.Phase.START);
    }

    @Test
    void aSessionShowsAPromptThenTheAnswerAndTheIntervalsEachGradeGives() {
        queueOf(item("hello", true));

        viewModel.start();
        assertThat(viewModel.phaseProperty().get()).isEqualTo(ReviewSessionViewModel.Phase.QUESTION);
        assertThat(viewModel.promptProperty().get()).isEqualTo("hello");
        assertThat(viewModel.answerProperty().get()).isEmpty();
        assertThat(viewModel.newCardProperty().get()).isTrue();
        assertThat(viewModel.progressTextProperty().get()).isEqualTo("1 / 1");

        viewModel.reveal();
        assertThat(viewModel.phaseProperty().get()).isEqualTo(ReviewSessionViewModel.Phase.ANSWER);
        assertThat(viewModel.answerProperty().get()).isEqualTo("answer of hello");
        assertThat(viewModel.intervalTextProperty(Grade.AGAIN).get()).isEqualTo("today");
        assertThat(viewModel.intervalTextProperty(Grade.GOOD).get()).isEqualTo("6 d");
        assertThat(viewModel.intervalTextProperty(Grade.EASY).get()).isEqualTo("15 d");
    }

    @Test
    void gradingBeforeTheAnswerIsShownDoesNothing() {
        queueOf(item("hello", false));
        viewModel.start();

        viewModel.grade(Grade.GOOD);

        verify(reviews, never()).grade(any(), any(), any(), anyInt());
    }

    @Test
    void againPutsTheCardAtTheEndOfTheSessionAndTimeIsMeasured() {
        ReviewItem first = item("first", false);
        ReviewItem second = item("second", false);
        queueOf(first, second);
        viewModel.start();
        viewModel.reveal();
        clock.advance(Duration.ofSeconds(12));

        viewModel.grade(Grade.AGAIN);

        verify(reviews).grade(first.cardId(), CardSide.FORWARD, Grade.AGAIN, 12);
        assertThat(viewModel.promptProperty().get()).isEqualTo("second");
        assertThat(viewModel.progressTextProperty().get()).isEqualTo("2 / 3"); // the forgotten card comes back

        viewModel.reveal();
        viewModel.grade(Grade.GOOD);
        assertThat(viewModel.promptProperty().get()).isEqualTo("first");
        viewModel.reveal();
        viewModel.grade(Grade.EASY);

        assertThat(viewModel.phaseProperty().get()).isEqualTo(ReviewSessionViewModel.Phase.SUMMARY);
        assertThat(viewModel.cardsTextProperty().get()).isEqualTo("2");
        assertThat(viewModel.againTextProperty().get()).isEqualTo("1");
        assertThat(viewModel.accuracyTextProperty().get()).isEqualTo("67%");
    }

    @Test
    void aCardIsNeverTimedForMoreThanFiveMinutes() {
        ReviewItem only = item("slow", false);
        queueOf(only);
        viewModel.start();
        viewModel.reveal();
        clock.advance(Duration.ofHours(2));

        viewModel.grade(Grade.GOOD);

        verify(reviews).grade(only.cardId(), CardSide.FORWARD, Grade.GOOD, ReviewSessionViewModel.MAX_CARD_SECONDS);
    }

    @Test
    void aCardDeletedMeanwhileIsSkipped() {
        ReviewItem gone = item("gone", false);
        ReviewItem kept = item("kept", false);
        queueOf(gone, kept);
        doThrow(new NotFoundException("Card", gone.cardId())).when(reviews).grade(any(), any(), any(), anyInt());
        viewModel.start();
        viewModel.reveal();

        viewModel.grade(Grade.GOOD);

        assertThat(viewModel.promptProperty().get()).isEqualTo("kept");
    }

    @Test
    void endingBeforeAnsweringReturnsToTheStartPage() {
        queueOf(item("a", false));
        viewModel.start();

        viewModel.endSession();

        assertThat(viewModel.phaseProperty().get()).isEqualTo(ReviewSessionViewModel.Phase.START);
        assertThat(viewModel.inSession()).isFalse();
    }

    @Test
    void theSummaryLogsTheMinutesSpentOnEachGoalOnce() {
        ReviewItem first = item("a", false);
        queueOf(first);
        viewModel.start();
        viewModel.reveal();
        clock.advance(Duration.ofSeconds(200)); // 3 whole minutes
        viewModel.grade(Grade.GOOD);
        assertThat(viewModel.canLogTimeProperty().get()).isTrue();

        viewModel.logAsStudyTime();
        viewModel.logAsStudyTime();

        ArgumentCaptor<SessionCommands.LogSession> logged = ArgumentCaptor.forClass(SessionCommands.LogSession.class);
        verify(sessions).log(logged.capture());
        assertThat(logged.getValue().minutes()).isEqualTo(3);
        assertThat(logged.getValue().goalId()).isEqualTo(goalId);
        assertThat(logged.getValue().source()).isEqualTo(SessionSource.MANUAL);
        assertThat(logged.getValue().startedAt()).isEqualTo(Instant.parse("2026-10-28T10:00:00Z"));
        assertThat(viewModel.logTextProperty().get()).isEqualTo("Logged 3 min of study time.");
        assertThat(viewModel.canLogTimeProperty().get()).isFalse();
    }

    @Test
    void aShortReviewHasNothingToLog() {
        queueOf(item("a", false));
        viewModel.start();
        viewModel.reveal();
        clock.advance(Duration.ofSeconds(20));
        viewModel.grade(Grade.GOOD);

        assertThat(viewModel.canLogTimeProperty().get()).isFalse();
        assertThat(viewModel.logTextProperty().get()).isEqualTo("Less than a minute: nothing to log.");
    }

    @Test
    void showingAGoalScopeChoosesItOnTheStartPage() {
        queueOf(item("a", false));

        viewModel.show(ReviewScope.ofGoal(goalId));

        assertThat(viewModel.scopeProperty().get().scope()).isEqualTo(ReviewScope.ofGoal(goalId));
        assertThat(viewModel.phaseProperty().get()).isEqualTo(ReviewSessionViewModel.Phase.START);
    }
}

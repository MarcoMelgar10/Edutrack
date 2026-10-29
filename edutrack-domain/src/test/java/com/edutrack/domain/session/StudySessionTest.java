package com.edutrack.domain.session;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudySessionTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);

    private final GoalId goalId = GoalId.newId();

    private SessionDetails details(Instant startedAt, Integer minutes, String note) {
        return new SessionDetails(goalId, null, null, startedAt, minutes, note);
    }

    private StudySession session(Integer minutes) {
        return StudySession.create(details(EARLIER, minutes, null), SessionSource.MANUAL, NOW);
    }

    @Test
    void createKeepsTheLinksSourceAndCreationTime() {
        StepId stepId = StepId.newId();
        PracticeRoutineId routineId = PracticeRoutineId.newId();

        StudySession session = StudySession.create(
                new SessionDetails(goalId, stepId, routineId, EARLIER, 45, "  Chapter 3  "), SessionSource.TIMER, NOW);

        assertThat(session.goalId()).isEqualTo(goalId);
        assertThat(session.stepId()).isEqualTo(stepId);
        assertThat(session.routineId()).isEqualTo(routineId);
        assertThat(session.startedAt()).isEqualTo(EARLIER);
        assertThat(session.minutes()).isEqualTo(45);
        assertThat(session.note()).isEqualTo("Chapter 3");
        assertThat(session.source()).isEqualTo(SessionSource.TIMER);
        assertThat(session.createdAt()).isEqualTo(NOW);
    }

    @Test
    void stepRoutineAndNoteAreOptional() {
        StudySession session = session(30);

        assertThat(session.stepId()).isNull();
        assertThat(session.routineId()).isNull();
        assertThat(session.note()).isNull();
    }

    @Test
    void goalIsRequired() {
        assertThatThrownBy(() -> StudySession.create(
                new SessionDetails(null, null, null, EARLIER, 30, null), SessionSource.MANUAL, NOW))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Goal is required.");
    }

    @Test
    void startIsRequired() {
        assertThatThrownBy(() -> StudySession.create(details(null, 30, null), SessionSource.MANUAL, NOW))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Start is required.");
    }

    @Test
    void minutesAreRequired() {
        assertThatThrownBy(() -> session(null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Minutes is required.");
    }

    @Test
    void minutesBetweenOneAndADayAreAccepted() {
        assertThat(session(1).minutes()).isEqualTo(1);
        assertThat(session(1440).minutes()).isEqualTo(1440);
    }

    @Test
    void minutesOutsideTheRangeAreRejectedWithTheirBounds() {
        for (int minutes : new int[]{0, -5, 1441}) {
            assertThatThrownBy(() -> session(minutes))
                    .isInstanceOfSatisfying(DomainValidationException.class, error -> {
                        assertThat(error.code()).isEqualTo("out-of-range");
                        assertThat(error.args()).containsExactly("Minutes", 1, 1440);
                    });
        }
    }

    @Test
    void sessionsCannotStartInTheFuture() {
        assertThatThrownBy(() -> StudySession.create(details(NOW.plusSeconds(1), 30, null), SessionSource.MANUAL, NOW))
                .isInstanceOfSatisfying(DomainValidationException.class, error -> {
                    assertThat(error.code()).isEqualTo("future-date");
                    assertThat(error.args()).containsExactly("Start");
                });
    }

    @Test
    void aSessionMayStartExactlyNow() {
        assertThat(StudySession.create(details(NOW, 30, null), SessionSource.MANUAL, NOW).startedAt()).isEqualTo(NOW);
    }

    @Test
    void noteOf501CharactersIsRejected() {
        assertThatThrownBy(() -> StudySession.create(details(EARLIER, 30, "a".repeat(501)), SessionSource.MANUAL, NOW))
                .isInstanceOfSatisfying(DomainValidationException.class, error -> assertThat(error.code()).isEqualTo("too-long"));
    }

    @Test
    void sourceIsRequired() {
        assertThatThrownBy(() -> StudySession.create(details(EARLIER, 30, null), null, NOW))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void updateChangesTheContentButNotTheSourceOrCreationTime() {
        StudySession session = StudySession.create(details(EARLIER, 30, "x"), SessionSource.TIMER, NOW);
        StepId stepId = StepId.newId();
        Instant later = NOW.plusSeconds(600);

        session.update(new SessionDetails(goalId, stepId, null, NOW.minusSeconds(60), 50, null), later);

        assertThat(session.stepId()).isEqualTo(stepId);
        assertThat(session.minutes()).isEqualTo(50);
        assertThat(session.note()).isNull();
        assertThat(session.source()).isEqualTo(SessionSource.TIMER);
        assertThat(session.createdAt()).isEqualTo(NOW);
    }

    @Test
    void aRejectedUpdateChangesNothing() {
        StudySession session = session(30);

        assertThatThrownBy(() -> session.update(details(EARLIER, 2000, "changed"), NOW))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> session.update(details(NOW.plusSeconds(5), 40, "changed"), NOW))
                .isInstanceOf(DomainValidationException.class);

        assertThat(session.minutes()).isEqualTo(30);
        assertThat(session.note()).isNull();
        assertThat(session.startedAt()).isEqualTo(EARLIER);
    }

    @Test
    void restoreDoesNotCheckTheStartAgainstTheClock() {
        StudySession restored = StudySession.restore(StudySessionId.newId(), goalId, null, null,
                NOW.plusSeconds(86_400), 20, null, SessionSource.MANUAL, NOW);

        assertThat(restored.startedAt()).isEqualTo(NOW.plusSeconds(86_400));
    }

    @Test
    void sessionsAreEqualById() {
        StudySession session = session(30);
        StudySession copy = StudySession.restore(session.id(), goalId, null, null, EARLIER, 99, null,
                SessionSource.MANUAL, NOW);

        assertThat(copy).isEqualTo(session).hasSameHashCodeAs(session);
        assertThat(session(30)).isNotEqualTo(session);
    }
}

package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.review.Card;
import com.edutrack.domain.review.CardDetails;
import com.edutrack.domain.review.CardId;
import com.edutrack.domain.review.CardRepository;
import com.edutrack.domain.review.CardSide;
import com.edutrack.domain.review.Grade;
import com.edutrack.domain.review.ReviewLog;
import com.edutrack.domain.review.ReviewLogId;
import com.edutrack.domain.review.ReviewLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import static com.edutrack.infrastructure.persistence.GoalFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class, JpaCardRepositoryAdapter.class,
        JpaReviewLogRepositoryAdapter.class})
class JpaReviewLogRepositoryAdapterIT {

    // A zone whose midnight is far from UTC's, so "local day" and "UTC day" differ visibly.
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private CardRepository cards;

    @Autowired
    private ReviewLogRepository logs;

    @Autowired
    private TestEntityManager entityManager;

    private CardId card() {
        Goal goal = GoalFixtures.saveGoalWithSteps(areas, goals, 1);
        Card card = Card.create(goal.id(), new CardDetails(null, "a", "b", Set.of(), true), LocalDate.of(2026, 3, 1), NOW);
        return cards.save(card).id();
    }

    private void log(CardId card, CardSide side, String at, Grade grade, boolean wasNew) {
        logs.append(new ReviewLog(ReviewLogId.newId(), card, side, Instant.parse(at), grade, 7, wasNew));
    }

    @Test
    void appendedEntriesRoundTripThroughFindBetween() {
        CardId card = card();
        Instant at = Instant.parse("2026-03-05T10:15:30.123456Z");
        ReviewLog entry = new ReviewLog(ReviewLogId.newId(), card, CardSide.REVERSE, at, Grade.HARD, 42, true);

        logs.append(entry);
        flushAndClear();

        assertThat(logs.findBetween(at.minusSeconds(1), at.plusSeconds(1))).containsExactly(entry);
    }

    @Test
    void findBetweenIncludesTheStartExcludesTheEndAndIsOldestFirst() {
        CardId card = card();
        log(card, CardSide.FORWARD, "2026-03-05T12:00:00Z", Grade.GOOD, false);
        log(card, CardSide.FORWARD, "2026-03-05T10:00:00Z", Grade.EASY, true);
        log(card, CardSide.FORWARD, "2026-03-05T11:00:00Z", Grade.AGAIN, false);
        flushAndClear();

        assertThat(logs.findBetween(Instant.parse("2026-03-05T10:00:00Z"), Instant.parse("2026-03-05T12:00:00Z")))
                .extracting(ReviewLog::grade).containsExactly(Grade.EASY, Grade.AGAIN);
        assertThat(logs.findBetween(Instant.parse("2026-03-06T00:00:00Z"), Instant.parse("2026-03-07T00:00:00Z"))).isEmpty();
    }

    @Test
    void countsReviewsByLocalDay() {
        CardId card = card();
        // Madrid is UTC+1 in early March: 23:30 UTC on the 4th is already the 5th there.
        log(card, CardSide.FORWARD, "2026-03-04T22:59:59Z", Grade.GOOD, false);   // 4 Mar 23:59 Madrid
        log(card, CardSide.FORWARD, "2026-03-04T23:00:00Z", Grade.GOOD, true);    // 5 Mar 00:00 Madrid
        log(card, CardSide.FORWARD, "2026-03-05T12:00:00Z", Grade.GOOD, false);   // 5 Mar
        log(card, CardSide.REVERSE, "2026-03-05T22:59:59Z", Grade.GOOD, true);    // 5 Mar 23:59 Madrid
        log(card, CardSide.FORWARD, "2026-03-05T23:00:00Z", Grade.GOOD, false);   // 6 Mar 00:00 Madrid
        flushAndClear();

        LocalDate fifth = LocalDate.of(2026, 3, 5);
        assertThat(logs.countOn(fifth, MADRID, false)).isEqualTo(3);
        assertThat(logs.countOn(fifth, MADRID, true)).isEqualTo(2);
        assertThat(logs.countOn(fifth, ZoneOffset.UTC, false)).isEqualTo(3);
        assertThat(logs.countOn(LocalDate.of(2026, 3, 4), MADRID, false)).isEqualTo(1);
        assertThat(logs.countOn(LocalDate.of(2026, 3, 6), MADRID, false)).isEqualTo(1);
        assertThat(logs.countOn(LocalDate.of(2026, 3, 7), MADRID, false)).isZero();
        assertThat(logs.countOn(LocalDate.of(2026, 3, 7), MADRID, true)).isZero();
    }

    @Test
    void theSameInstantsFallOnDifferentDaysInAnotherZone() {
        CardId card = card();
        log(card, CardSide.FORWARD, "2026-03-05T03:00:00Z", Grade.GOOD, true);    // 4 Mar 22:00 in Lima (UTC-5)
        log(card, CardSide.FORWARD, "2026-03-05T20:00:00Z", Grade.GOOD, false);   // 5 Mar 15:00 in Lima
        flushAndClear();

        assertThat(logs.countOn(LocalDate.of(2026, 3, 4), LIMA, false)).isEqualTo(1);
        assertThat(logs.countOn(LocalDate.of(2026, 3, 5), LIMA, false)).isEqualTo(1);
        assertThat(logs.countOn(LocalDate.of(2026, 3, 5), ZoneOffset.UTC, false)).isEqualTo(2);
    }

    @Test
    void reviewDaysListsTheLocalDaysWithAtLeastOneReview() {
        CardId card = card();
        log(card, CardSide.FORWARD, "2026-03-04T23:30:00Z", Grade.GOOD, true);    // 5 Mar in Madrid
        log(card, CardSide.FORWARD, "2026-03-05T09:00:00Z", Grade.GOOD, false);   // 5 Mar
        log(card, CardSide.FORWARD, "2026-03-08T09:00:00Z", Grade.GOOD, false);   // 8 Mar
        log(card, CardSide.FORWARD, "2026-03-20T09:00:00Z", Grade.GOOD, false);   // outside the range
        flushAndClear();

        assertThat(logs.reviewDays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10), MADRID))
                .containsExactlyInAnyOrder(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 8));
        assertThat(logs.reviewDays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10), ZoneOffset.UTC))
                .containsExactlyInAnyOrder(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 8));
        assertThat(logs.reviewDays(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 8), MADRID))
                .containsExactly(LocalDate.of(2026, 3, 8));
        assertThat(logs.reviewDays(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), MADRID)).isEmpty();
    }

    @Test
    void countsStayCorrectWhenTheClocksChange() {
        CardId card = card();
        // Madrid switched to summer time on 29 Mar 2026 (the day has 23 hours): 00:00 local = 28 Mar 23:00 UTC.
        log(card, CardSide.FORWARD, "2026-03-28T23:00:00Z", Grade.GOOD, true);    // 29 Mar 00:00 Madrid
        log(card, CardSide.FORWARD, "2026-03-29T21:59:59Z", Grade.GOOD, false);   // 29 Mar 23:59 Madrid (UTC+2)
        log(card, CardSide.FORWARD, "2026-03-29T22:00:00Z", Grade.GOOD, false);   // 30 Mar 00:00 Madrid
        flushAndClear();

        assertThat(logs.countOn(LocalDate.of(2026, 3, 29), MADRID, false)).isEqualTo(2);
        assertThat(logs.countOn(LocalDate.of(2026, 3, 30), MADRID, false)).isEqualTo(1);
    }

    @Test
    void anEntryNeedsAnExistingCard() {
        ReviewLog orphan = new ReviewLog(ReviewLogId.newId(), CardId.newId(), CardSide.FORWARD, NOW, Grade.GOOD, 1, true);

        assertThatThrownBy(() -> logs.append(orphan)).hasStackTraceContaining("foreign key");
    }

    @Test
    void theDatabaseRejectsNegativeSeconds() {
        CardId card = card();

        assertThatThrownBy(() -> entityManager.getEntityManager().createNativeQuery(
                "insert into review_log (id, card_id, side, reviewed_at, grade, seconds, was_new) "
                        + "values (gen_random_uuid(), :card, 'FORWARD', now(), 'GOOD', -1, false)")
                .setParameter("card", card.value()).executeUpdate())
                .hasStackTraceContaining("violates check constraint");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}

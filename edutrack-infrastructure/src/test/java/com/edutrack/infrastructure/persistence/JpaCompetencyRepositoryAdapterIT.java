package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.measure.Measure;
import com.edutrack.domain.measure.MeasureDetails;
import com.edutrack.domain.measure.MeasureRepository;
import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyDetails;
import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.skill.CompetencyRepository;
import com.edutrack.domain.skill.LevelChange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.edutrack.infrastructure.persistence.GoalFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class, JpaCompetencyRepositoryAdapter.class,
        JpaMeasureRepositoryAdapter.class})
class JpaCompetencyRepositoryAdapterIT {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 3, 1);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 3, 8);
    private static final LocalDate DAY_3 = LocalDate.of(2026, 3, 15);

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private CompetencyRepository competencies;

    @Autowired
    private MeasureRepository measures;

    @Autowired
    private TestEntityManager entityManager;

    private Goal goalWithSteps(int steps) {
        Goal goal = GoalFixtures.saveGoalWithSteps(areas, goals, steps);
        entityManager.flush();
        return goal;
    }

    private Competency listening(Goal goal, int position) {
        return Competency.create(goal.id(), new CompetencyDetails("Listening", "Native speed", 5), position, NOW);
    }

    @Test
    void roundTripsACompetencyWithHistoryAndSteps() {
        Goal goal = goalWithSteps(3);
        Competency competency = listening(goal, 2);
        competency.changeLevel(1, DAY_1);
        competency.changeLevel(3, DAY_2);
        competency.linkSteps(Set.of(goal.steps().get(0).id(), goal.steps().get(2).id()));

        Competency saved = competencies.save(competency);
        flushAndClear();
        Competency loaded = competencies.findById(competency.id()).orElseThrow();

        assertSame(saved, competency);
        assertSame(loaded, competency);
        assertThat(loaded.name()).isEqualTo("Listening");
        assertThat(loaded.description()).isEqualTo("Native speed");
        assertThat(loaded.level()).isEqualTo(3);
        assertThat(loaded.targetLevel()).isEqualTo(5);
        assertThat(loaded.position()).isEqualTo(2);
        assertThat(loaded.createdAt()).isEqualTo(NOW);
        assertThat(loaded.steps()).containsExactlyInAnyOrder(goal.steps().get(0).id(), goal.steps().get(2).id());
    }

    @Test
    void aFreshCompetencyHasNoHistoryAndNoSteps() {
        Goal goal = goalWithSteps(1);
        Competency competency = Competency.create(goal.id(), new CompetencyDetails("SQL", null, 3), 0, NOW);

        competencies.save(competency);
        flushAndClear();
        Competency loaded = competencies.findById(competency.id()).orElseThrow();

        assertThat(loaded.level()).isZero();
        assertThat(loaded.description()).isNull();
        assertThat(loaded.history()).isEmpty();
        assertThat(loaded.steps()).isEmpty();
    }

    @Test
    void historyComesBackInDateOrderWhateverTheInsertOrder() {
        Goal goal = goalWithSteps(1);
        Competency competency = Competency.restore(CompetencyId.newId(), goal.id(),
                new CompetencyDetails("Speaking", null, 4), 4, 0, NOW,
                List.of(new LevelChange(DAY_3, 4), new LevelChange(DAY_1, 1), new LevelChange(DAY_2, 2)), Set.of());

        competencies.save(competency);
        flushAndClear();

        assertThat(competencies.findById(competency.id()).orElseThrow().history())
                .containsExactly(new LevelChange(DAY_1, 1), new LevelChange(DAY_2, 2), new LevelChange(DAY_3, 4));
    }

    @Test
    void savesAnEditedCompetencyExactly() {
        Goal goal = goalWithSteps(3);
        Competency competency = listening(goal, 0);
        competency.changeLevel(1, DAY_1);
        competency.changeLevel(2, DAY_2);
        competency.linkSteps(Set.of(goal.steps().get(0).id(), goal.steps().get(1).id()));
        competencies.save(competency);
        flushAndClear();

        Competency edited = competencies.findById(competency.id()).orElseThrow();
        edited.update(new CompetencyDetails("Listening (news)", null, 4));
        edited.changeLevel(5, DAY_2);          // replaces the day-2 entry
        edited.changeLevel(4, DAY_3);
        edited.unlinkStep(goal.steps().get(0).id());
        edited.linkSteps(Set.of(goal.steps().get(2).id()));
        edited.moveTo(3);
        competencies.save(edited);
        flushAndClear();
        Competency reloaded = competencies.findById(competency.id()).orElseThrow();

        assertSame(reloaded, edited);
        assertThat(reloaded.history()).containsExactly(new LevelChange(DAY_1, 1), new LevelChange(DAY_2, 5), new LevelChange(DAY_3, 4));
        assertThat(reloaded.steps()).containsExactlyInAnyOrder(goal.steps().get(1).id(), goal.steps().get(2).id());
        assertThat(reloaded.name()).isEqualTo("Listening (news)");
        assertThat(reloaded.description()).isNull();
        assertThat(count("competency_level_change", "competency_id", competency.id().value())).isEqualTo(3);
        assertThat(count("step_competency", "competency_id", competency.id().value())).isEqualTo(2);
    }

    @Test
    void findByGoalIsOrderedByPositionAndLimitedToTheGoal() {
        Goal goal = goalWithSteps(1);
        Goal other = goalWithSteps(1);
        competencies.save(Competency.create(goal.id(), new CompetencyDetails("Third", null, 1), 2, NOW));
        competencies.save(Competency.create(goal.id(), new CompetencyDetails("First", null, 1), 0, NOW));
        competencies.save(Competency.create(goal.id(), new CompetencyDetails("Second", null, 1), 1, NOW));
        competencies.save(Competency.create(other.id(), new CompetencyDetails("Elsewhere", null, 1), 0, NOW));
        flushAndClear();

        assertThat(competencies.findByGoal(goal.id())).extracting(Competency::name).containsExactly("First", "Second", "Third");
        assertThat(competencies.findByGoal(other.id())).extracting(Competency::name).containsExactly("Elsewhere");
    }

    @Test
    void findByStepReturnsEveryCompetencyLinkedToIt() {
        Goal goal = goalWithSteps(2);
        StepId shared = goal.steps().get(0).id();
        StepId lonely = goal.steps().get(1).id();
        Competency first = listening(goal, 0);
        first.linkSteps(Set.of(shared));
        first.changeLevel(2, DAY_1);
        Competency second = Competency.create(goal.id(), new CompetencyDetails("Writing", null, 4), 1, NOW);
        second.linkSteps(Set.of(shared, lonely));
        competencies.save(second);
        competencies.save(first);
        flushAndClear();

        assertThat(competencies.findByStep(shared)).extracting(Competency::name).containsExactly("Listening", "Writing");
        assertThat(competencies.findByStep(lonely)).extracting(Competency::name).containsExactly("Writing");
        assertThat(competencies.findByStep(shared).getFirst().history()).containsExactly(new LevelChange(DAY_1, 2));
        assertThat(competencies.findByStep(StepId.newId())).isEmpty();
    }

    @Test
    void findByIdOfAMissingCompetencyIsEmpty() {
        assertThat(competencies.findById(CompetencyId.newId())).isEmpty();
    }

    @Test
    void deleteRemovesTheCompetencyItsHistoryAndItsLinks() {
        Goal goal = goalWithSteps(1);
        Competency competency = listening(goal, 0);
        competency.changeLevel(2, DAY_1);
        competency.linkSteps(Set.of(goal.steps().getFirst().id()));
        competencies.save(competency);
        flushAndClear();

        competencies.delete(competency.id());
        competencies.delete(competency.id());
        flushAndClear();

        assertThat(competencies.findById(competency.id())).isEmpty();
        assertThat(count("competency_level_change", "competency_id", competency.id().value())).isZero();
        assertThat(count("step_competency", "competency_id", competency.id().value())).isZero();
    }

    @Test
    void deletingTheGoalDeletesItsCompetencies() {
        Goal goal = goalWithSteps(1);
        Goal kept = goalWithSteps(1);
        Competency competency = listening(goal, 0);
        competency.changeLevel(1, DAY_1);
        competencies.save(competency);
        Competency keptCompetency = competencies.save(listening(kept, 0));
        flushAndClear();

        goals.delete(goal.id());
        flushAndClear();

        assertThat(competencies.findById(competency.id())).isEmpty();
        assertThat(count("competency_level_change", "competency_id", competency.id().value())).isZero();
        assertThat(competencies.findById(keptCompetency.id())).isPresent();
    }

    @Test
    void removingAStepUnlinksItFromTheCompetency() {
        Goal goal = goalWithSteps(2);
        StepId removed = goal.steps().get(0).id();
        StepId kept = goal.steps().get(1).id();
        Competency competency = listening(goal, 0);
        competency.linkSteps(Set.of(removed, kept));
        competencies.save(competency);
        flushAndClear();

        Goal reloaded = goals.findById(goal.id()).orElseThrow();
        reloaded.removeStep(removed);
        goals.save(reloaded);
        flushAndClear();

        assertThat(competencies.findById(competency.id()).orElseThrow().steps()).containsExactly(kept);
    }

    @Test
    void deletingTheCompetencyKeepsItsMeasures() {
        Goal goal = goalWithSteps(1);
        Competency competency = competencies.save(listening(goal, 0));
        Measure measure = measures.save(Measure.create(goal.id(),
                new MeasureDetails("Listening band", "band", null, null, null, null, competency.id()), 0, NOW));
        flushAndClear();

        competencies.delete(competency.id());
        flushAndClear();

        Measure reloaded = measures.findById(measure.id()).orElseThrow();
        assertThat(reloaded.competencyId()).isNull();
    }

    @Test
    void theDatabaseRejectsOutOfRangeLevels() {
        Goal goal = goalWithSteps(1);
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> {
            entityManager.getEntityManager().createNativeQuery(
                    "insert into competency (id, goal_id, name, level, target_level, position, created_at) "
                            + "values (:id, :goal, 'X', 6, 3, 0, now())")
                    .setParameter("id", id).setParameter("goal", goal.id().value()).executeUpdate();
        }).hasStackTraceContaining("violates check constraint");
    }

    @Test
    void aSecondEntryForTheSameDayIsRejectedByTheDatabase() {
        Goal goal = goalWithSteps(1);
        Competency competency = competencies.save(listening(goal, 0));
        flushAndClear();
        entityManager.getEntityManager().createNativeQuery(
                "insert into competency_level_change (competency_id, changed_on, level) values (:id, date '2026-03-01', 1)")
                .setParameter("id", competency.id().value()).executeUpdate();

        assertThatThrownBy(() -> entityManager.getEntityManager().createNativeQuery(
                "insert into competency_level_change (competency_id, changed_on, level) values (:id, date '2026-03-01', 2)")
                .setParameter("id", competency.id().value()).executeUpdate())
                .hasStackTraceContaining("duplicate key");
    }

    // ---------- helpers ----------

    private static void assertSame(Competency actual, Competency expected) {
        assertThat(actual).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
        assertThat(actual.history()).containsExactlyElementsOf(expected.history());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private long count(String table, String column, UUID id) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("select count(*) from " + table + " where " + column + " = :id")
                .setParameter("id", id)
                .getSingleResult()).longValue();
    }
}

package com.edutrack.application.skill;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.support.InMemoryCompetencyRepository;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.MutableClock;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.skill.CompetencyDetails;
import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.skill.LevelChange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetencyServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryCompetencyRepository competencies = new InMemoryCompetencyRepository();
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final CompetencyService service = new CompetencyService(competencies, goals, clock);
    private final Goal goal = goalWithSteps("Grammar", "Listening practice", "Mock exam");

    private Goal goalWithSteps(String... titles) {
        Goal created = Goal.create(AreaId.newId(), "IELTS 7", null, null, 0, NOW);
        for (String title : titles) {
            created.addStep(new StepDetails(title, null, null), StepStatus.NOT_STARTED, NOW);
        }
        return goals.save(created);
    }

    private static CompetencyDetails details(String name, int target) {
        return new CompetencyDetails(name, null, target);
    }

    private StepId step(int index) {
        return goal.steps().get(index).id();
    }

    @Test
    void createdCompetenciesStartAtLevelZeroAndAreAppended() {
        CompetencyView first = service.create(goal.id(), details("Listening", 5));
        CompetencyView second = service.create(goal.id(), details("Writing", 4));

        assertThat(first.level()).isZero();
        assertThat(first.meetsTarget()).isFalse();
        assertThat(first.history()).isEmpty();
        assertThat(first.steps()).isEmpty();
        assertThat(first.position()).isZero();
        assertThat(second.position()).isEqualTo(1);
        assertThat(service.forGoal(goal.id())).extracting(CompetencyView::name).containsExactly("Listening", "Writing");
    }

    @Test
    void creatingForAMissingGoalFails() {
        assertThatThrownBy(() -> service.create(com.edutrack.domain.goal.GoalId.newId(), details("Listening", 5)))
                .isInstanceOf(NotFoundException.class);
        assertThat(competencies.findByGoal(goal.id())).isEmpty();
    }

    @Test
    void invalidDetailsAreRejectedAndNothingIsSaved() {
        assertThatThrownBy(() -> service.create(goal.id(), details("Listening", 7)))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.code()).isEqualTo("out-of-range"));
        assertThat(competencies.findByGoal(goal.id())).isEmpty();
    }

    @Test
    void updateChangesNameDescriptionAndTarget() {
        CompetencyId id = service.create(goal.id(), details("Listening", 5)).id();

        CompetencyView updated = service.update(id, new CompetencyDetails("Listening (news)", "BBC", 3));

        assertThat(updated.name()).isEqualTo("Listening (news)");
        assertThat(updated.description()).isEqualTo("BBC");
        assertThat(updated.targetLevel()).isEqualTo(3);
    }

    @Test
    void changeLevelRecordsTodaysDateAndMeetsTarget() {
        CompetencyId id = service.create(goal.id(), details("Listening", 3)).id();

        CompetencyView two = service.changeLevel(id, 2);
        clock.advanceDays(2);
        CompetencyView three = service.changeLevel(id, 3);

        assertThat(two.meetsTarget()).isFalse();
        assertThat(three.level()).isEqualTo(3);
        assertThat(three.meetsTarget()).isTrue();
        assertThat(three.history()).containsExactly(new LevelChange(LocalDate.of(2026, 10, 27), 2),
                new LevelChange(LocalDate.of(2026, 10, 29), 3));
    }

    @Test
    void changingTheLevelTwiceInOneDayKeepsOneHistoryEntry() {
        CompetencyId id = service.create(goal.id(), details("Listening", 5)).id();

        service.changeLevel(id, 2);
        CompetencyView view = service.changeLevel(id, 4);

        assertThat(view.history()).containsExactly(new LevelChange(LocalDate.of(2026, 10, 27), 4));
    }

    @Test
    void anOutOfRangeLevelIsRejected() {
        CompetencyId id = service.create(goal.id(), details("Listening", 5)).id();

        assertThatThrownBy(() -> service.changeLevel(id, 6)).isInstanceOf(DomainValidationException.class);
        assertThat(competencies.findById(id).orElseThrow().level()).isZero();
    }

    @Test
    void unknownCompetenciesAreReported() {
        assertThatThrownBy(() -> service.changeLevel(CompetencyId.newId(), 1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(CompetencyId.newId(), details("A", 1))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.move(CompetencyId.newId(), MoveDirection.UP)).isInstanceOf(NotFoundException.class);
    }

    // ---------- steps ----------

    @Test
    void linkedStepsAppearWithTheirTitlesInRoadmapOrder() {
        CompetencyId id = service.create(goal.id(), details("Listening", 5)).id();

        CompetencyView view = service.linkSteps(id, Set.of(step(2), step(1)));

        assertThat(view.steps()).extracting(CompetencyView.LinkedStep::title).containsExactly("Listening practice", "Mock exam");
        assertThat(view.steps()).extracting(CompetencyView.LinkedStep::id).containsExactly(step(1), step(2));
    }

    @Test
    void linkingAddsToTheExistingLinksAndSettingReplacesThem() {
        CompetencyId id = service.create(goal.id(), details("Listening", 5)).id();
        service.linkSteps(id, Set.of(step(0)));

        CompetencyView added = service.linkSteps(id, Set.of(step(1)));
        CompetencyView replaced = service.setSteps(id, Set.of(step(2)));

        assertThat(added.steps()).extracting(CompetencyView.LinkedStep::id).containsExactly(step(0), step(1));
        assertThat(replaced.steps()).extracting(CompetencyView.LinkedStep::id).containsExactly(step(2));
    }

    @Test
    void unlinkingRemovesOneStep() {
        CompetencyId id = service.create(goal.id(), details("Listening", 5)).id();
        service.linkSteps(id, Set.of(step(0), step(1)));

        CompetencyView view = service.unlinkStep(id, step(0));

        assertThat(view.steps()).extracting(CompetencyView.LinkedStep::id).containsExactly(step(1));
    }

    @Test
    void stepsOfAnotherGoalCannotBeLinked() {
        Goal other = goalWithSteps("Elsewhere");
        CompetencyId id = service.create(goal.id(), details("Listening", 5)).id();
        service.linkSteps(id, Set.of(step(0)));

        assertThatThrownBy(() -> service.linkSteps(id, Set.of(step(1), other.steps().getFirst().id())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.setSteps(id, Set.of(other.steps().getFirst().id())))
                .isInstanceOf(NotFoundException.class);

        assertThat(competencies.findById(id).orElseThrow().steps()).containsExactly(step(0));
    }

    @Test
    void forStepListsTheCompetenciesTheStepContributesTo() {
        CompetencyId listening = service.create(goal.id(), details("Listening", 5)).id();
        CompetencyId writing = service.create(goal.id(), details("Writing", 4)).id();
        service.linkSteps(listening, Set.of(step(2)));
        service.linkSteps(writing, Set.of(step(2), step(0)));

        assertThat(service.forStep(step(2))).extracting(CompetencyView::name).containsExactly("Listening", "Writing");
        assertThat(service.forStep(step(0))).extracting(CompetencyView::name).containsExactly("Writing");
        assertThat(service.forStep(step(1))).isEmpty();
        assertThat(service.forStep(StepId.newId())).isEmpty();
    }

    // ---------- move and delete ----------

    @Test
    void moveSwapsWithTheNeighbourAndRenumbers() {
        CompetencyId a = service.create(goal.id(), details("A", 1)).id();
        service.create(goal.id(), details("B", 1));
        CompetencyId c = service.create(goal.id(), details("C", 1)).id();

        List<CompetencyView> afterUp = service.move(c, MoveDirection.UP);
        List<CompetencyView> afterDown = service.move(a, MoveDirection.DOWN);

        assertThat(afterUp).extracting(CompetencyView::name).containsExactly("A", "C", "B");
        assertThat(afterUp).extracting(CompetencyView::position).containsExactly(0, 1, 2);
        assertThat(afterDown).extracting(CompetencyView::name).containsExactly("C", "A", "B");
    }

    @Test
    void movingPastEitherEndDoesNothing() {
        CompetencyId a = service.create(goal.id(), details("A", 1)).id();
        service.create(goal.id(), details("B", 1));

        assertThat(service.move(a, MoveDirection.UP)).extracting(CompetencyView::name).containsExactly("A", "B");
    }

    @Test
    void deleteRemovesTheCompetency() {
        CompetencyId id = service.create(goal.id(), details("A", 1)).id();

        service.delete(id);

        assertThat(service.forGoal(goal.id())).isEmpty();
        service.delete(id);
    }
}

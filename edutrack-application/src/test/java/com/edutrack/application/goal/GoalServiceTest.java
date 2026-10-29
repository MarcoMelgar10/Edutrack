package com.edutrack.application.goal;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.support.InMemoryAreaRepository;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryLearningRecordRepository;
import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.record.LearningRecord;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryAreaRepository areas = new InMemoryAreaRepository();
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final InMemoryLearningRecordRepository records = new InMemoryLearningRecordRepository();
    private final GoalService service = new GoalService(goals, areas, records, clock);
    private final AreaId areaId = areas.save(Area.create("English", null, AreaIcon.GLOBE, 0, NOW)).id();

    private GoalDetails createGoal(AreaId area, String title) {
        return service.createGoal(new GoalCommands.CreateGoal(area, title, null, null));
    }

    private GoalDetails addStep(GoalId goalId, String title, String hours, StepStatus status) {
        BigDecimal estimate = hours == null ? null : new BigDecimal(hours);
        return service.addStep(new GoalCommands.AddStep(goalId, title, null, estimate, status));
    }

    private GoalDetails addStep(GoalId goalId, String title) {
        return addStep(goalId, title, null, StepStatus.NOT_STARTED);
    }

    private static List<String> titles(GoalDetails details) {
        return details.steps().stream().map(StepView::title).toList();
    }

    private static List<Integer> numbers(GoalDetails details) {
        return details.steps().stream().map(StepView::number).toList();
    }

    private static StepId lastStep(GoalDetails details) {
        return details.steps().getLast().id();
    }

    @Test
    void createGoalForAnUnknownAreaIsNotFound() {
        assertThatThrownBy(() -> createGoal(AreaId.newId(), "Goal"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Area not found");
    }

    @Test
    void createGoalReturnsAnEmptyRoadmapAtZeroProgress() {
        GoalDetails created = service.createGoal(
                new GoalCommands.CreateGoal(areaId, "  Pass B2  ", "Cambridge", LocalDate.of(2027, 6, 1)));

        assertThat(created.title()).isEqualTo("Pass B2");
        assertThat(created.description()).isEqualTo("Cambridge");
        assertThat(created.deadline()).isEqualTo(LocalDate.of(2027, 6, 1));
        assertThat(created.areaId()).isEqualTo(areaId);
        assertThat(created.steps()).isEmpty();
        assertThat(created.progress()).isEqualTo(Progress.ZERO);
        assertThat(created.completed()).isFalse();
    }

    @Test
    void goalPositionsAreMaxPlusOneWithinTheArea() {
        createGoal(areaId, "First");
        GoalDetails second = createGoal(areaId, "Second");
        createGoal(areaId, "Third");
        service.deleteGoal(second.id());
        createGoal(areaId, "Fourth");

        assertThat(goals.findByArea(areaId)).extracting(Goal::position).containsExactly(0, 2, 3);
    }

    @Test
    void goalPositionsAreIndependentPerArea() {
        AreaId other = areas.save(Area.create("Technologies", null, AreaIcon.CODE, 1, NOW)).id();
        createGoal(areaId, "English goal");
        createGoal(areaId, "Another English goal");

        GoalDetails inOther = createGoal(other, "Java goal");

        assertThat(goals.findById(inOther.id())).get().extracting(Goal::position).isEqualTo(0);
    }

    @Test
    void thePlanScenarioEndToEnd() {
        GoalId goalId = createGoal(areaId, "Pass B2").id();
        StepId first = lastStep(addStep(goalId, "Grammar", "10", StepStatus.NOT_STARTED));
        StepId second = lastStep(addStep(goalId, "Listening", "20", StepStatus.NOT_STARTED));
        addStep(goalId, "Mock exam", "8", StepStatus.NOT_STARTED);

        service.cycleStepStatus(goalId, first);
        service.cycleStepStatus(goalId, first);
        GoalDetails details = service.updateStep(new GoalCommands.UpdateStep(
                goalId, second, "Listening", null, new BigDecimal("20"), StepStatus.IN_PROGRESS));

        assertThat(details.progress().rounded()).isEqualTo(26);
        assertThat(details.doneHours()).isEqualByComparingTo("10.0");
        assertThat(details.totalHours()).isEqualByComparingTo("38.0");
        assertThat(numbers(details)).containsExactly(1, 2, 3);
        assertThat(details.steps().get(0).status()).isEqualTo(StepStatus.DONE);
        assertThat(details.steps().get(0).completedAt()).isEqualTo(NOW);
        assertThat(details.steps().get(1).status()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(details.completed()).isFalse();
    }

    @Test
    void updateStepChangesTitleHoursAndStatus() {
        GoalId goalId = createGoal(areaId, "Goal").id();
        StepId stepId = lastStep(addStep(goalId, "Old title", "1", StepStatus.NOT_STARTED));

        StepView step = service.updateStep(new GoalCommands.UpdateStep(
                goalId, stepId, "New title", "Notes", new BigDecimal("3.5"), StepStatus.DONE))
                .steps().getFirst();

        assertThat(step.title()).isEqualTo("New title");
        assertThat(step.description()).isEqualTo("Notes");
        assertThat(step.estimatedHours()).isEqualByComparingTo("3.5");
        assertThat(step.status()).isEqualTo(StepStatus.DONE);
        assertThat(step.completedAt()).isEqualTo(NOW);
    }

    @Test
    void updateGoalChangesTitleDescriptionAndDeadline() {
        GoalId goalId = createGoal(areaId, "Old").id();

        GoalDetails updated = service.updateGoal(
                new GoalCommands.UpdateGoal(goalId, "New", "Details", LocalDate.of(2027, 1, 31)));

        assertThat(updated.title()).isEqualTo("New");
        assertThat(updated.description()).isEqualTo("Details");
        assertThat(updated.deadline()).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    void addStepWithoutStatusStartsNotStarted() {
        GoalId goalId = createGoal(areaId, "Goal").id();

        GoalDetails details = service.addStep(new GoalCommands.AddStep(goalId, "Step", null, null, null));

        assertThat(details.steps().getFirst().status()).isEqualTo(StepStatus.NOT_STARTED);
    }

    @Test
    void moveStepReturnsTheStepsInTheNewOrder() {
        GoalId goalId = createGoal(areaId, "Goal").id();
        addStep(goalId, "A");
        addStep(goalId, "B");
        StepId c = lastStep(addStep(goalId, "C"));

        GoalDetails moved = service.moveStep(goalId, c, MoveDirection.UP);

        assertThat(titles(moved)).containsExactly("A", "C", "B");
        assertThat(numbers(moved)).containsExactly(1, 2, 3);
    }

    @Test
    void removeStepDropsItAndRenumbers() {
        GoalId goalId = createGoal(areaId, "Goal").id();
        addStep(goalId, "A");
        StepId b = lastStep(addStep(goalId, "B"));
        addStep(goalId, "C");

        GoalDetails removed = service.removeStep(goalId, b);

        assertThat(titles(removed)).containsExactly("A", "C");
        assertThat(numbers(removed)).containsExactly(1, 2);
    }

    @Test
    void goalIsCompletedOnlyWhenAllStepsAreDone() {
        GoalId goalId = createGoal(areaId, "Goal").id();
        addStep(goalId, "A", null, StepStatus.DONE);
        GoalDetails oneLeft = addStep(goalId, "B", null, StepStatus.IN_PROGRESS);

        GoalDetails allDone = service.cycleStepStatus(goalId, lastStep(oneLeft));

        assertThat(oneLeft.completed()).isFalse();
        assertThat(allDone.completed()).isTrue();
        assertThat(allDone.progress()).isEqualTo(Progress.COMPLETE);
    }

    @Test
    void listGoalsCountsStepsAndDoneStepsInPositionOrder() {
        GoalId first = createGoal(areaId, "First").id();
        addStep(first, "A", null, StepStatus.DONE);
        addStep(first, "B", null, StepStatus.DONE);
        addStep(first, "C", null, StepStatus.NOT_STARTED);
        GoalId second = createGoal(areaId, "Second").id();
        addStep(second, "D", null, StepStatus.DONE);
        createGoal(areaId, "Third");

        List<GoalSummary> summaries = service.listGoals(areaId);

        assertThat(summaries).extracting(GoalSummary::title).containsExactly("First", "Second", "Third");
        assertThat(summaries).extracting(GoalSummary::stepCount).containsExactly(3, 1, 0);
        assertThat(summaries).extracting(GoalSummary::doneCount).containsExactly(2, 1, 0);
        assertThat(summaries).extracting(GoalSummary::completed).containsExactly(false, true, false);
    }

    @Test
    void listGoalsOnlyReturnsTheGoalsOfThatArea() {
        AreaId other = areas.save(Area.create("Technologies", null, AreaIcon.CODE, 1, NOW)).id();
        createGoal(areaId, "English goal");
        createGoal(other, "Java goal");

        assertThat(service.listGoals(areaId)).extracting(GoalSummary::title).containsExactly("English goal");
    }

    @Test
    void getGoalReturnsTheDetails() {
        GoalId goalId = createGoal(areaId, "Goal").id();
        addStep(goalId, "A");

        GoalDetails details = service.getGoal(goalId);

        assertThat(details.id()).isEqualTo(goalId);
        assertThat(titles(details)).containsExactly("A");
    }

    @Test
    void getGoalWithAnUnknownIdIsNotFound() {
        assertThatThrownBy(() -> service.getGoal(GoalId.newId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Goal not found");
    }

    @Test
    void addStepToAnUnknownGoalIsNotFound() {
        assertThatThrownBy(() -> addStep(GoalId.newId(), "Step")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void stepViewsCarryTheSummaryOfTheirLearningRecord() {
        GoalId goalId = createGoal(areaId, "Goal").id();
        addStep(goalId, "With record");
        GoalDetails details = addStep(goalId, "Without record");
        StepId withRecord = details.steps().get(0).id();
        LearningRecord record = LearningRecord.create(RecordOwner.ofStep(withRecord), NOW);
        record.updateNotes("Notes", NOW);
        record.addExercise("Essay", NOW);
        records.save(record);

        GoalDetails loaded = service.getGoal(goalId);

        assertThat(loaded.steps().get(0).record()).isEqualTo(new RecordSummary(true, 0, 0, 1, 0));
        assertThat(loaded.steps().get(1).record()).isEqualTo(RecordSummary.EMPTY);
        assertThat(loaded.step(withRecord).title()).isEqualTo("With record");
        assertThatThrownBy(() -> loaded.step(StepId.newId())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletedGoalIsNoLongerFound() {
        GoalId goalId = createGoal(areaId, "Goal").id();

        service.deleteGoal(goalId);

        assertThatThrownBy(() -> service.getGoal(goalId)).isInstanceOf(NotFoundException.class);
    }

    // ---------- moving goals ----------

    private List<String> goalTitles() {
        return service.listGoals(areaId).stream().map(GoalSummary::title).toList();
    }

    @Test
    void moveGoalDownSwapsItWithItsNeighbour() {
        GoalId first = createGoal(areaId, "First").id();
        createGoal(areaId, "Second");
        createGoal(areaId, "Third");

        List<GoalSummary> moved = service.moveGoal(first, MoveDirection.DOWN);

        assertThat(moved).extracting(GoalSummary::title).containsExactly("Second", "First", "Third");
        assertThat(goalTitles()).containsExactly("Second", "First", "Third");
    }

    @Test
    void moveGoalUpSwapsItWithItsNeighbour() {
        createGoal(areaId, "First");
        createGoal(areaId, "Second");
        GoalId third = createGoal(areaId, "Third").id();

        List<GoalSummary> moved = service.moveGoal(third, MoveDirection.UP);

        assertThat(moved).extracting(GoalSummary::title).containsExactly("First", "Third", "Second");
    }

    @Test
    void movingTheFirstGoalUpOrTheLastGoalDownDoesNothing() {
        GoalId first = createGoal(areaId, "First").id();
        createGoal(areaId, "Second");
        GoalId third = createGoal(areaId, "Third").id();

        assertThat(service.moveGoal(first, MoveDirection.UP)).extracting(GoalSummary::title)
                .containsExactly("First", "Second", "Third");
        assertThat(service.moveGoal(third, MoveDirection.DOWN)).extracting(GoalSummary::title)
                .containsExactly("First", "Second", "Third");
        assertThat(goals.findByArea(areaId)).extracting(Goal::position).containsExactly(0, 1, 2);
    }

    @Test
    void moveGoalRenumbersPositionsWithGapsLeftByDeletedGoals() {
        GoalId first = createGoal(areaId, "First").id();
        GoalId second = createGoal(areaId, "Second").id();
        createGoal(areaId, "Third");
        service.deleteGoal(second);                      // positions are now 0 and 2

        service.moveGoal(first, MoveDirection.DOWN);

        assertThat(goalTitles()).containsExactly("Third", "First");
        assertThat(goals.findByArea(areaId)).extracting(Goal::position).containsExactly(0, 1);
    }

    @Test
    void moveGoalOnlyReordersWithinItsOwnArea() {
        AreaId other = areas.save(Area.create("Technologies", null, AreaIcon.CODE, 1, NOW)).id();
        GoalId first = createGoal(areaId, "First").id();
        createGoal(areaId, "Second");
        GoalId elsewhere = createGoal(other, "Elsewhere").id();

        service.moveGoal(first, MoveDirection.DOWN);

        assertThat(goalTitles()).containsExactly("Second", "First");
        assertThat(goals.findById(elsewhere)).get().extracting(Goal::position).isEqualTo(0);
    }

    @Test
    void moveGoalWithAnUnknownIdIsNotFound() {
        assertThatThrownBy(() -> service.moveGoal(GoalId.newId(), MoveDirection.UP))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- weekly target and creation date ----------

    @Test
    void createGoalCarriesTheWeeklyTargetAndCreationTime() {
        GoalDetails created = service.createGoal(
                new GoalCommands.CreateGoal(areaId, "Guitar", null, null, 150));

        assertThat(created.weeklyTargetMinutes()).isEqualTo(150);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(createGoal(areaId, "No target").weeklyTargetMinutes()).isNull();
    }

    @Test
    void updateGoalChangesAndRemovesTheWeeklyTarget() {
        GoalId goalId = service.createGoal(new GoalCommands.CreateGoal(areaId, "Guitar", null, null, 150)).id();

        GoalDetails changed = service.updateGoal(new GoalCommands.UpdateGoal(goalId, "Guitar", null, null, 200));
        assertThat(changed.weeklyTargetMinutes()).isEqualTo(200);

        GoalDetails removed = service.updateGoal(new GoalCommands.UpdateGoal(goalId, "Guitar", null, null, null));
        assertThat(removed.weeklyTargetMinutes()).isNull();
    }

    @Test
    void anInvalidWeeklyTargetIsRejectedAndNothingIsSaved() {
        GoalId goalId = service.createGoal(new GoalCommands.CreateGoal(areaId, "Guitar", null, null, 150)).id();

        assertThatThrownBy(() -> service.updateGoal(new GoalCommands.UpdateGoal(goalId, "Renamed", null, null, 20_000)))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.createGoal(new GoalCommands.CreateGoal(areaId, "Other", null, null, 0)))
                .isInstanceOf(DomainValidationException.class);

        assertThat(service.getGoal(goalId).title()).isEqualTo("Guitar");
        assertThat(service.getGoal(goalId).weeklyTargetMinutes()).isEqualTo(150);
        assertThat(goalTitles()).containsExactly("Guitar");
    }

    // ---------- planned days and the agenda ----------

    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 27);

    private StepId addPlannedStep(GoalId goalId, String title, LocalDate plannedFor, StepStatus status) {
        return lastStep(service.addStep(new GoalCommands.AddStep(goalId, title, null, null, status, plannedFor)));
    }

    @Test
    void stepsCarryTheirPlannedDay() {
        GoalId goalId = createGoal(areaId, "Goal").id();
        StepId stepId = addPlannedStep(goalId, "Planned", TUESDAY.plusDays(2), StepStatus.NOT_STARTED);

        assertThat(service.getGoal(goalId).step(stepId).plannedFor()).isEqualTo(TUESDAY.plusDays(2));

        GoalDetails replanned = service.updateStep(new GoalCommands.UpdateStep(
                goalId, stepId, "Planned", null, null, StepStatus.NOT_STARTED, TUESDAY.minusDays(9)));
        assertThat(replanned.step(stepId).plannedFor()).isEqualTo(TUESDAY.minusDays(9));

        GoalDetails cleared = service.updateStep(new GoalCommands.UpdateStep(
                goalId, stepId, "Planned", null, null, StepStatus.NOT_STARTED));
        assertThat(cleared.step(stepId).plannedFor()).isNull();
    }

    @Test
    void agendaListsUnfinishedStepsPlannedThisWeekAndOverdueOnesSortedByDay() {
        GoalId english = createGoal(areaId, "English").id();
        AreaId otherArea = areas.save(Area.create("Technologies", null, AreaIcon.CODE, 1, NOW)).id();
        GoalId java = createGoal(otherArea, "Java").id();
        addPlannedStep(english, "Friday", LocalDate.of(2026, 10, 30), StepStatus.NOT_STARTED);
        StepId overdue = addPlannedStep(english, "Last week", LocalDate.of(2026, 10, 20), StepStatus.IN_PROGRESS);
        addPlannedStep(english, "Today", TUESDAY, StepStatus.NOT_STARTED);
        addPlannedStep(english, "Done already", LocalDate.of(2026, 10, 28), StepStatus.DONE);
        addPlannedStep(english, "Next week", LocalDate.of(2026, 11, 2), StepStatus.NOT_STARTED);
        addPlannedStep(english, "Not planned", null, StepStatus.NOT_STARTED);
        addPlannedStep(java, "Monday", LocalDate.of(2026, 10, 26), StepStatus.NOT_STARTED);

        List<AgendaItem> agenda = service.agenda(DayOfWeek.MONDAY);

        assertThat(agenda).extracting(AgendaItem::stepTitle)
                .containsExactly("Last week", "Monday", "Today", "Friday");
        assertThat(agenda).extracting(AgendaItem::overdue).containsExactly(true, true, false, false);
        AgendaItem first = agenda.getFirst();
        assertThat(first.stepId()).isEqualTo(overdue);
        assertThat(first.goalId()).isEqualTo(english);
        assertThat(first.goalTitle()).isEqualTo("English");
        assertThat(first.areaId()).isEqualTo(areaId);
        assertThat(first.plannedFor()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(first.status()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(agenda.get(1).areaId()).isEqualTo(otherArea);
    }

    @Test
    void theAgendaWeekFollowsTheChosenFirstDay() {
        GoalId goalId = createGoal(areaId, "Goal").id();
        addPlannedStep(goalId, "Sunday", LocalDate.of(2026, 11, 1), StepStatus.NOT_STARTED);

        assertThat(service.agenda(DayOfWeek.MONDAY)).extracting(AgendaItem::stepTitle).containsExactly("Sunday");
        assertThat(service.agenda(DayOfWeek.SUNDAY)).isEmpty();
    }

    @Test
    void theAgendaIsEmptyWithoutPlannedSteps() {
        addStep(createGoal(areaId, "Goal").id(), "Step");

        assertThat(service.agenda(DayOfWeek.MONDAY)).isEmpty();
    }
}

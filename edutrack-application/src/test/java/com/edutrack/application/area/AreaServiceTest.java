package com.edutrack.application.area;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.goal.GoalCommands;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.support.InMemoryAreaRepository;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryLearningRecordRepository;
import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AreaServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryAreaRepository areas = new InMemoryAreaRepository();
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final AreaService service = new AreaService(areas, goals, clock);
    private final GoalService goalService = new GoalService(goals, areas, new InMemoryLearningRecordRepository(), clock);

    private AreaSummary create(String name) {
        return service.createArea(new AreaCommands.CreateArea(name, null, AreaIcon.BOOK));
    }

    private void addGoalWithSteps(AreaId areaId, StepStatus... statuses) {
        GoalId goalId = goalService.createGoal(new GoalCommands.CreateGoal(areaId, "Goal", null, null)).id();
        for (StepStatus status : statuses) {
            goalService.addStep(new GoalCommands.AddStep(goalId, "Step", null, BigDecimal.ONE, status));
        }
    }

    private List<Integer> positions() {
        return areas.findAll().stream().map(Area::position).toList();
    }

    private List<String> names(List<AreaSummary> summaries) {
        return summaries.stream().map(AreaSummary::name).toList();
    }

    @Test
    void createAreaAssignsConsecutivePositions() {
        create("English");
        create("Technologies");
        create("Consulting");

        assertThat(positions()).containsExactly(0, 1, 2);
    }

    @Test
    void createAreaReturnsTheSummaryOfAnEmptyArea() {
        AreaSummary created = service.createArea(new AreaCommands.CreateArea("  English ", "Grammar", AreaIcon.GLOBE));

        assertThat(created.name()).isEqualTo("English");
        assertThat(created.description()).isEqualTo("Grammar");
        assertThat(created.icon()).isEqualTo(AreaIcon.GLOBE);
        assertThat(created.goalCount()).isZero();
        assertThat(created.progress()).isEqualTo(Progress.ZERO);
    }

    @Test
    void afterDeletingTheFirstAreaTheNextOneGetsMaxPlusOne() {
        AreaSummary first = create("English");
        create("Technologies");
        create("Consulting");

        service.deleteArea(first.id());
        create("Design");

        assertThat(positions()).containsExactly(1, 2, 3);
    }

    @Test
    void duplicateNameIsRejectedIgnoringCase() {
        create("English");

        assertThatThrownBy(() -> create("ENGLISH"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void duplicateNameIsRejectedIgnoringSurroundingSpaces() {
        create("English");

        assertThatThrownBy(() -> create("  english "))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectedDuplicateIsNotSaved() {
        create("English");

        assertThatThrownBy(() -> create("english")).isInstanceOf(DomainValidationException.class);

        assertThat(areas.findAll()).hasSize(1);
    }

    @Test
    void blankNameIsReportedAsRequiredNotAsDuplicate() {
        assertThatThrownBy(() -> create("   "))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Name is required.");
    }

    @Test
    void updateAreaChangesNameDescriptionAndIcon() {
        AreaSummary english = create("English");

        AreaSummary updated = service.updateArea(
                new AreaCommands.UpdateArea(english.id(), "Languages", "All of them", AreaIcon.GLOBE));

        assertThat(updated.id()).isEqualTo(english.id());
        assertThat(updated.name()).isEqualTo("Languages");
        assertThat(updated.description()).isEqualTo("All of them");
        assertThat(updated.icon()).isEqualTo(AreaIcon.GLOBE);
    }

    @Test
    void updateAreaMayKeepItsOwnName() {
        AreaSummary english = create("English");

        AreaSummary updated = service.updateArea(
                new AreaCommands.UpdateArea(english.id(), " english ", "New description", AreaIcon.GLOBE));

        assertThat(updated.name()).isEqualTo("english");
        assertThat(updated.description()).isEqualTo("New description");
    }

    @Test
    void renamingAnAreaToAnotherAreasNameIsRejected() {
        create("English");
        AreaSummary technologies = create("Technologies");

        assertThatThrownBy(() -> service.updateArea(
                new AreaCommands.UpdateArea(technologies.id(), "english", null, AreaIcon.CODE)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void listAreasReturnsThemInPositionOrder() {
        areas.save(Area.create("Third", null, AreaIcon.BOOK, 2, NOW));
        areas.save(Area.create("First", null, AreaIcon.BOOK, 0, NOW));
        areas.save(Area.create("Second", null, AreaIcon.BOOK, 1, NOW));

        assertThat(names(service.listAreas())).containsExactly("First", "Second", "Third");
    }

    @Test
    void listAreasShowsGoalCountAndProgressWeightedByHoursIgnoringEmptyGoals() {
        AreaSummary english = create("English");
        AreaSummary technologies = create("Technologies");
        addGoalWithSteps(english.id(), StepStatus.DONE, StepStatus.NOT_STARTED);
        addGoalWithSteps(english.id(), StepStatus.DONE);
        addGoalWithSteps(english.id());

        List<AreaSummary> listed = service.listAreas();

        // 2 of 3 hours done; the goal without steps does not count.
        assertThat(listed.get(0).goalCount()).isEqualTo(3);
        assertThat(listed.get(0).progress().percent()).isEqualByComparingTo("66.67");
        assertThat(listed.get(1).id()).isEqualTo(technologies.id());
        assertThat(listed.get(1).goalCount()).isZero();
        assertThat(listed.get(1).progress()).isEqualTo(Progress.ZERO);
    }

    @Test
    void getAreaReturnsTheSummaryWithItsProgress() {
        AreaSummary english = create("English");
        addGoalWithSteps(english.id(), StepStatus.DONE, StepStatus.IN_PROGRESS, StepStatus.NOT_STARTED, StepStatus.NOT_STARTED);

        AreaSummary found = service.getArea(english.id());

        assertThat(found.goalCount()).isEqualTo(1);
        assertThat(found.progress().percent()).isEqualByComparingTo("25");
    }

    @Test
    void deleteAreaRemovesIt() {
        AreaSummary english = create("English");

        service.deleteArea(english.id());

        assertThat(service.listAreas()).isEmpty();
    }

    @Test
    void addSuggestedAreasCreatesEnglishTechnologiesAndConsulting() {
        List<AreaSummary> created = service.addSuggestedAreas();

        assertThat(names(created)).containsExactly("English", "Technologies", "Consulting");
        assertThat(created).extracting(AreaSummary::icon)
                .containsExactly(AreaIcon.GLOBE, AreaIcon.CODE, AreaIcon.BRIEFCASE);
    }

    @Test
    void addSuggestedAreasTwiceDoesNotDuplicate() {
        service.addSuggestedAreas();

        List<AreaSummary> again = service.addSuggestedAreas();

        assertThat(names(again)).containsExactly("English", "Technologies", "Consulting");
    }

    @Test
    void addSuggestedAreasSkipsANameThatAlreadyExists() {
        create("english");

        List<AreaSummary> result = service.addSuggestedAreas();

        assertThat(names(result)).containsExactly("english", "Technologies", "Consulting");
    }

    @Test
    void getAreaWithAnUnknownIdIsNotFound() {
        assertThatThrownBy(() -> service.getArea(AreaId.newId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Area not found");
    }

    @Test
    void updateAreaWithAnUnknownIdIsNotFound() {
        assertThatThrownBy(() -> service.updateArea(
                new AreaCommands.UpdateArea(AreaId.newId(), "English", null, AreaIcon.GLOBE)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void moveAreaDownSwapsItWithItsNeighbour() {
        AreaId english = create("English").id();
        create("Technologies");
        create("Consulting");

        List<AreaSummary> moved = service.moveArea(english, MoveDirection.DOWN);

        assertThat(names(moved)).containsExactly("Technologies", "English", "Consulting");
        assertThat(positions()).containsExactly(0, 1, 2);
    }

    @Test
    void moveAreaUpSwapsItWithItsNeighbour() {
        create("English");
        create("Technologies");
        AreaId consulting = create("Consulting").id();

        List<AreaSummary> moved = service.moveArea(consulting, MoveDirection.UP);

        assertThat(names(moved)).containsExactly("English", "Consulting", "Technologies");
        assertThat(names(service.listAreas())).containsExactly("English", "Consulting", "Technologies");
    }

    @Test
    void movingTheFirstAreaUpOrTheLastAreaDownDoesNothing() {
        AreaId english = create("English").id();
        create("Technologies");
        AreaId consulting = create("Consulting").id();

        assertThat(names(service.moveArea(english, MoveDirection.UP))).containsExactly("English", "Technologies", "Consulting");
        assertThat(names(service.moveArea(consulting, MoveDirection.DOWN))).containsExactly("English", "Technologies", "Consulting");
        assertThat(positions()).containsExactly(0, 1, 2);
    }

    @Test
    void moveAreaRenumbersPositionsWithGapsLeftByDeletedAreas() {
        AreaId english = create("English").id();
        AreaId technologies = create("Technologies").id();
        create("Consulting");
        service.deleteArea(technologies);                 // positions are now 0 and 2

        List<AreaSummary> moved = service.moveArea(english, MoveDirection.DOWN);

        assertThat(names(moved)).containsExactly("Consulting", "English");
        assertThat(positions()).containsExactly(0, 1);
    }

    @Test
    void moveAreaWithAnUnknownIdIsNotFound() {
        assertThatThrownBy(() -> service.moveArea(AreaId.newId(), MoveDirection.UP))
                .isInstanceOf(NotFoundException.class);
    }
}

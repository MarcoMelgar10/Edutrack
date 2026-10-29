package com.edutrack.application.export;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.goal.GoalCommands;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.measure.MeasureService;
import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.review.ReviewService;
import com.edutrack.application.support.InMemoryAreaRepository;
import com.edutrack.application.support.InMemoryCardRepository;
import com.edutrack.application.support.InMemoryCompetencyRepository;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryLearningRecordRepository;
import com.edutrack.application.support.InMemoryMeasureRepository;
import com.edutrack.application.support.InMemoryReviewLogRepository;
import com.edutrack.application.support.MutableClock;
import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.measure.MeasureDetails;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.record.AssessmentDetails;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.ResourceDetails;
import com.edutrack.domain.record.ResourceType;
import com.edutrack.domain.review.CardDetails;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryGoalRepository goalRepository = new InMemoryGoalRepository();
    private final InMemoryAreaRepository areas = new InMemoryAreaRepository();
    private final InMemoryLearningRecordRepository recordRepository = new InMemoryLearningRecordRepository();
    private final InMemoryMeasureRepository measureRepository = new InMemoryMeasureRepository();
    private final InMemoryCardRepository cardRepository = new InMemoryCardRepository();

    private final GoalService goals = new GoalService(goalRepository, areas, recordRepository, clock);
    private final LearningRecordService records = new LearningRecordService(recordRepository, goalRepository,
            measureRepository, clock);
    private final MeasureService measures = new MeasureService(measureRepository, recordRepository,
            new InMemoryCompetencyRepository(), goalRepository, clock);
    private final ReviewService reviews = new ReviewService(cardRepository, new InMemoryReviewLogRepository(),
            goalRepository, areas, clock);
    private final ExportService export = new ExportService(goals, records, reviews, measures);

    private final Area area = areas.save(Area.create("English", null, AreaIcon.GLOBE, 0, NOW));

    private GoalId goal(String title, String description) {
        return goals.createGoal(new GoalCommands.CreateGoal(area.id(), title, description, null)).id();
    }

    private StepId step(GoalId goalId, String title, String description, String hours, StepStatus status) {
        return goals.addStep(new GoalCommands.AddStep(goalId, title, description,
                hours == null ? null : new BigDecimal(hours), status)).steps().getLast().id();
    }

    // ---------- markdown ----------

    @Test
    void notesMarkdownListsEachStepWithItsRecordAndThenTheGoalResults() {
        GoalId goalId = goal("Reach B2", "Pass the exam in June.");
        StepId grammar = step(goalId, "Grammar", "Tenses and conditionals", "4.0", StepStatus.IN_PROGRESS);
        step(goalId, "Listening", null, null, StepStatus.NOT_STARTED);
        RecordOwner owner = RecordOwner.ofStep(grammar);
        records.saveNotes(owner, "\n\nPast simple vs present perfect.\n\n- *since* a point in time\n- *for* a period\n\n");
        records.addResource(owner, new ResourceDetails("BBC Learning English", "https://www.bbc.co.uk/learningenglish",
                ResourceType.ARTICLE));
        records.addResource(owner, new ResourceDetails("A paper dictionary", null, ResourceType.BOOK));
        var drill = records.addExercise(owner, "Past simple drill").exercises().getFirst().id();
        records.addExercise(owner, "Conditionals drill");
        records.markExerciseDone(owner, drill, true);
        records.addResult(owner, new AssessmentDetails("Grammar quiz", new BigDecimal("18"), new BigDecimal("20"),
                LocalDate.of(2026, 10, 20), null));
        records.addResult(RecordOwner.ofGoal(goalId), new AssessmentDetails("IELTS mock", new BigDecimal("7.50"),
                new BigDecimal("9"), LocalDate.of(2026, 10, 27), "Felt rushed", null, "Listening"));

        assertThat(export.goalNotesMarkdown(goalId)).isEqualTo("""
                # Reach B2

                Pass the exam in June.

                ## 1. Grammar

                In progress · 4 h

                Tenses and conditionals

                Past simple vs present perfect.

                - *since* a point in time
                - *for* a period

                ### Resources

                - [BBC Learning English](https://www.bbc.co.uk/learningenglish)
                - A paper dictionary

                ### Exercises

                - [x] Past simple drill
                - [ ] Conditionals drill

                ### Results

                - 2026-10-20 · Grammar quiz: 18 / 20

                ## 2. Listening

                Not started

                ## Goal results

                - 2026-10-27 · IELTS mock (Listening): 7.5 / 9 — Felt rushed
                """);
    }

    @Test
    void notesAreKeptVerbatimIncludingIndentedCodeAndHeadings() {
        GoalId goalId = goal("Java", null);
        StepId step = step(goalId, "Streams", null, "2", StepStatus.DONE);
        records.saveNotes(RecordOwner.ofStep(step), "    int x = 1;\n\n# My heading\n");

        assertThat(export.goalNotesMarkdown(goalId)).isEqualTo("""
                # Java

                ## 1. Streams

                Done · 2 h

                    int x = 1;

                # My heading
                """);
    }

    @Test
    void aGoalWithoutStepsIsJustItsTitle() {
        GoalId goalId = goal("Empty", null);

        assertThat(export.goalNotesMarkdown(goalId)).isEqualTo("# Empty\n");
    }

    // ---------- cards ----------

    @Test
    void cardsCsvQuotesFieldsAccordingToRfc4180() {
        GoalId goalId = goal("Spanish", null);
        addCard(goalId, new CardDetails(null, "to be, or not", "ser o no ser", Set.of("verbs", "basic"), true));
        addCard(goalId, new CardDetails(null, "say \"hi\"", "line one\nline two", Set.of(), false));
        addCard(goalId, new CardDetails(null, "plain", "simple", Set.of("x"), false));

        assertThat(export.cardsCsv(goalId)).isEqualTo(
                "front,back,tags,reversible\r\n"
                        + "\"to be, or not\",ser o no ser,basic;verbs,true\r\n"
                        + "\"say \"\"hi\"\"\",\"line one\nline two\",,false\r\n"
                        + "plain,simple,x,false\r\n");
    }

    @Test
    void cardsCsvOfAGoalWithoutCardsIsJustTheHeader() {
        assertThat(export.cardsCsv(goal("Empty", null))).isEqualTo("front,back,tags,reversible\r\n");
    }

    private void addCard(GoalId goalId, CardDetails details) {
        clock.advance(Duration.ofMillis(1));
        reviews.createCard(goalId, details);
    }

    // ---------- results ----------

    @Test
    void resultsCsvListsStepAndGoalResultsOldestFirstWithMeasureAndStepNames() {
        GoalId goalId = goal("Reach B2", null);
        StepId grammar = step(goalId, "Grammar, tenses", null, null, StepStatus.NOT_STARTED);
        MeasureId band = measures.create(goalId, new MeasureDetails("IELTS band", "band", new BigDecimal("9"), null,
                Direction.HIGHER_IS_BETTER, CombineRule.AVERAGE, null)).id();
        records.addResult(RecordOwner.ofGoal(goalId), new AssessmentDetails("IELTS mock", new BigDecimal("7.5"), null,
                LocalDate.of(2026, 10, 27), null, band, "Listening"));
        records.addResult(RecordOwner.ofStep(grammar), new AssessmentDetails("Quiz", new BigDecimal("18.00"),
                new BigDecimal("20"), LocalDate.of(2026, 10, 20), "Good, but \"slow\""));
        records.addResult(RecordOwner.ofStep(grammar), new AssessmentDetails("Quiz", new BigDecimal("0"), null,
                LocalDate.of(2026, 10, 1), null));

        assertThat(export.resultsCsv(goalId)).isEqualTo(
                "date,title,part,measure,score,max,step,note\r\n"
                        + "2026-10-01,Quiz,,,0,,\"Grammar, tenses\",\r\n"
                        + "2026-10-20,Quiz,,,18,20,\"Grammar, tenses\",\"Good, but \"\"slow\"\"\"\r\n"
                        + "2026-10-27,IELTS mock,Listening,IELTS band,7.5,,,\r\n");
    }

    @Test
    void exportingAMissingGoalFails() {
        GoalId missing = GoalId.newId();

        assertThatThrownBy(() -> export.goalNotesMarkdown(missing)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> export.resultsCsv(missing)).isInstanceOf(NotFoundException.class);
    }
}

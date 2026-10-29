package com.edutrack.application.template;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.goal.GoalCommands;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.StepView;
import com.edutrack.application.measure.MeasureService;
import com.edutrack.application.measure.MeasureView;
import com.edutrack.application.practice.PracticeCommands;
import com.edutrack.application.practice.PracticeService;
import com.edutrack.application.practice.RoutineView;
import com.edutrack.application.review.CardView;
import com.edutrack.application.review.ReviewService;
import com.edutrack.application.skill.CompetencyService;
import com.edutrack.application.skill.CompetencyView;
import com.edutrack.application.support.InMemoryAreaRepository;
import com.edutrack.application.support.InMemoryCardRepository;
import com.edutrack.application.support.InMemoryCompetencyRepository;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryLearningRecordRepository;
import com.edutrack.application.support.InMemoryMeasureRepository;
import com.edutrack.application.support.InMemoryPracticeRoutineRepository;
import com.edutrack.application.support.InMemoryReviewLogRepository;
import com.edutrack.application.support.InMemoryStudySessionRepository;
import com.edutrack.application.support.InMemoryTemplateCatalog;
import com.edutrack.application.support.MutableClock;
import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.measure.MeasureDetails;
import com.edutrack.domain.practice.Cadence;
import com.edutrack.domain.review.Card;
import com.edutrack.domain.review.CardDetails;
import com.edutrack.domain.skill.CompetencyDetails;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TemplateServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryAreaRepository areas = new InMemoryAreaRepository();
    private final InMemoryGoalRepository goalRepository = new InMemoryGoalRepository();
    private final InMemoryLearningRecordRepository recordRepository = new InMemoryLearningRecordRepository();
    private final InMemoryCompetencyRepository competencyRepository = new InMemoryCompetencyRepository();
    private final InMemoryMeasureRepository measureRepository = new InMemoryMeasureRepository();
    private final InMemoryPracticeRoutineRepository routineRepository = new InMemoryPracticeRoutineRepository();
    private final InMemoryStudySessionRepository sessionRepository = new InMemoryStudySessionRepository();
    private final InMemoryCardRepository cardRepository = new InMemoryCardRepository();
    private final InMemoryTemplateCatalog catalog = new InMemoryTemplateCatalog();

    private final GoalService goals = new GoalService(goalRepository, areas, recordRepository, clock);
    private final CompetencyService competencies = new CompetencyService(competencyRepository, goalRepository, clock);
    private final MeasureService measures = new MeasureService(measureRepository, recordRepository,
            competencyRepository, goalRepository, clock);
    private final PracticeService practice = new PracticeService(routineRepository, sessionRepository,
            goalRepository, clock);
    private final ReviewService reviews = reviewsOver(cardRepository);
    private final TemplateService service = serviceOver(reviews);

    private final AreaId areaId = areas.save(Area.create("Languages", null, AreaIcon.GLOBE, 0, NOW)).id();

    private ReviewService reviewsOver(InMemoryCardRepository cards) {
        return new ReviewService(cards, new InMemoryReviewLogRepository(), goalRepository, areas, clock);
    }

    private TemplateService serviceOver(ReviewService reviewService) {
        return new TemplateService(catalog, goals, competencies, measures, practice, reviewService, clock);
    }

    private static GoalTemplate sample() {
        return new GoalTemplate("sample", "Sample plan", "A plan for testing.", 4, 200,
                List.of(new GoalTemplate.Step("One", "First step", new BigDecimal("1.5")),
                        new GoalTemplate.Step("Two", null, null),
                        new GoalTemplate.Step("Three", "Last step", new BigDecimal("2"))),
                List.of(new GoalTemplate.Competency("Listening", "Hear things", 4),
                        new GoalTemplate.Competency("Writing", null, 3)),
                List.of(new GoalTemplate.Measure("Mock exam", "%", new BigDecimal("100"), new BigDecimal("75"),
                                Direction.HIGHER_IS_BETTER, CombineRule.AVERAGE, "listening"),
                        new GoalTemplate.Measure("Errors", "errors", null, null, Direction.LOWER_IS_BETTER,
                                CombineRule.NONE, null)),
                List.of(new GoalTemplate.Routine("Daily words", Cadence.DAILY, null, 15, 0),
                        new GoalTemplate.Routine("Speak", Cadence.WEEKLY, 3, 20, 2),
                        new GoalTemplate.Routine("Free practice", Cadence.WEEKLY, 1, 30, null)),
                List.of(new GoalTemplate.Card("front 1", "back 1", false),
                        new GoalTemplate.Card("front 2", "back 2", true)));
    }

    // ---------- listing ----------

    @Test
    void listsTheShippedTemplatesOfTheLanguageThenTheUsersOwn() {
        GoalTemplate english = sample();
        GoalTemplate spanish = sample().withIdAndName("sample", "Plan de prueba");
        GoalTemplate mine = sample().withIdAndName("mine", "My own");
        catalog.addBuiltIn("en", english);
        catalog.addBuiltIn("es", spanish);
        catalog.saveUserTemplate(mine);

        assertThat(service.list(ENGLISH)).containsExactly(english, mine);
        assertThat(service.list(SPANISH)).containsExactly(spanish, mine);
    }

    // ---------- apply ----------

    @Test
    void applyCreatesTheGoalWithEverythingTheTemplateDescribes() {
        catalog.addBuiltIn("en", sample());

        GoalId goalId = service.apply("sample", areaId, null, LocalDate.of(2026, 11, 2), ENGLISH);

        GoalDetails goal = goals.getGoal(goalId);
        assertThat(goal.areaId()).isEqualTo(areaId);
        assertThat(goal.title()).isEqualTo("Sample plan");
        assertThat(goal.description()).isEqualTo("A plan for testing.");
        assertThat(goal.deadline()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(goal.weeklyTargetMinutes()).isEqualTo(200);
        assertThat(goal.steps()).extracting(step -> step.title(), step -> step.description(),
                        step -> step.estimatedHours(), step -> step.status())
                .containsExactly(
                        tuple("One", "First step", new BigDecimal("1.5"), StepStatus.NOT_STARTED),
                        tuple("Two", null, null, StepStatus.NOT_STARTED),
                        tuple("Three", "Last step", new BigDecimal("2.0"), StepStatus.NOT_STARTED));

        List<CompetencyView> createdCompetencies = competencies.forGoal(goalId);
        assertThat(createdCompetencies).extracting(CompetencyView::name, CompetencyView::targetLevel, CompetencyView::level)
                .containsExactly(tuple("Listening", 4, 0),
                        tuple("Writing", 3, 0));

        List<MeasureView> createdMeasures = measures.forGoal(goalId);
        assertThat(createdMeasures).extracting(MeasureView::name).containsExactly("Mock exam", "Errors");
        MeasureView mock = createdMeasures.getFirst();
        assertThat(mock.unit()).isEqualTo("%");
        assertThat(mock.maxValue()).isEqualByComparingTo("100");
        assertThat(mock.target()).isEqualByComparingTo("75");
        assertThat(mock.combineRule()).isEqualTo(CombineRule.AVERAGE);
        assertThat(mock.competencyId()).isEqualTo(createdCompetencies.getFirst().id());
        assertThat(createdMeasures.get(1).direction()).isEqualTo(Direction.LOWER_IS_BETTER);
        assertThat(createdMeasures.get(1).competencyId()).isNull();

        List<RoutineView> createdRoutines = practice.forGoal(goalId);
        assertThat(createdRoutines).extracting(RoutineView::title, RoutineView::cadence, RoutineView::timesPerWeek,
                        RoutineView::targetMinutes, RoutineView::stepId, RoutineView::active)
                .containsExactly(
                        tuple("Daily words", Cadence.DAILY, 7, 15, goal.steps().get(0).id(), true),
                        tuple("Speak", Cadence.WEEKLY, 3, 20, goal.steps().get(2).id(), true),
                        tuple("Free practice", Cadence.WEEKLY, 1, 30, null, true));

        List<CardView> createdCards = reviews.forGoal(goalId);
        assertThat(createdCards).extracting(CardView::front, CardView::back, CardView::reversible)
                .containsExactly(tuple("front 1", "back 1", false),
                        tuple("front 2", "back 2", true));
    }

    @Test
    void applyUsesTheGivenTitleAndTodayAsTheDefaultStart() {
        catalog.addBuiltIn("en", sample());

        GoalId goalId = service.apply("sample", areaId, "  My Spanish plan ", null, ENGLISH);

        GoalDetails goal = goals.getGoal(goalId);
        assertThat(goal.title()).isEqualTo("My Spanish plan");
        assertThat(goal.deadline()).isEqualTo(LocalDate.of(2026, 10, 27).plusWeeks(4));
    }

    @Test
    void aTemplateWithoutDurationLeavesTheGoalWithoutDeadlineAndTarget() {
        catalog.addBuiltIn("en", new GoalTemplate("open", "Open habit", null, null, null,
                List.of(new GoalTemplate.Step("Only step", null, null)), null, null, null, null));

        GoalDetails goal = goals.getGoal(service.apply("open", areaId, "", LocalDate.of(2026, 11, 2), ENGLISH));

        assertThat(goal.title()).isEqualTo("Open habit");
        assertThat(goal.deadline()).isNull();
        assertThat(goal.weeklyTargetMinutes()).isNull();
        assertThat(goal.steps()).hasSize(1);
    }

    @Test
    void applyFindsTemplatesOfTheRequestedLanguageAndTheUsersOwn() {
        catalog.addBuiltIn("en", sample());
        catalog.addBuiltIn("es", sample().withIdAndName("sample", "Plan de prueba"));
        catalog.saveUserTemplate(sample().withIdAndName("mine", "My own"));

        assertThat(goals.getGoal(service.apply("sample", areaId, null, null, SPANISH)).title()).isEqualTo("Plan de prueba");
        assertThat(goals.getGoal(service.apply("mine", areaId, null, null, SPANISH)).title()).isEqualTo("My own");
    }

    @Test
    void applyingAnUnknownTemplateOrToAnUnknownAreaCreatesNothing() {
        catalog.addBuiltIn("en", sample());

        assertThatThrownBy(() -> service.apply("nope", areaId, null, null, ENGLISH)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.apply("sample", AreaId.newId(), null, null, ENGLISH))
                .isInstanceOf(NotFoundException.class);
        assertThat(goalRepository.findAll()).isEmpty();
    }

    @Test
    void anInvalidTemplateIsRefusedBeforeAnythingIsCreated() {
        GoalTemplate broken = new GoalTemplate("broken", "Broken", null, null, null,
                List.of(new GoalTemplate.Step("Step", null, null)), null,
                List.of(new GoalTemplate.Measure("Mock", null, null, null, null, null, "Missing competency")), null, null);
        catalog.saveUserTemplate(broken);

        assertThatThrownBy(() -> service.apply("broken", areaId, null, null, ENGLISH))
                .isInstanceOfSatisfying(DomainValidationException.class,
                        problem -> assertThat(problem.code()).isEqualTo("template-invalid"));
        assertThat(goalRepository.findAll()).isEmpty();
    }

    @Test
    void aFailureHalfWayRemovesTheGoalAgain() {
        InMemoryCardRepository failingCards = new InMemoryCardRepository() {
            @Override
            public Card save(Card card) {
                throw new IllegalStateException("disk full");
            }

        };
        TemplateService failing = serviceOver(reviewsOver(failingCards));
        catalog.addBuiltIn("en", sample());

        assertThatThrownBy(() -> failing.apply("sample", areaId, null, null, ENGLISH))
                .isInstanceOf(IllegalStateException.class).hasMessage("disk full");

        assertThat(goalRepository.findAll()).isEmpty();
    }

    // ---------- from a goal ----------

    private GoalId buildGoal() {
        GoalId goalId = goals.createGoal(new GoalCommands.CreateGoal(areaId, "Learn Spanish", "Everyday talk",
                LocalDate.of(2026, 11, 11), 300)).id();
        goals.addStep(new GoalCommands.AddStep(goalId, "Basics", "Greetings", new BigDecimal("6"), StepStatus.DONE));
        goals.addStep(new GoalCommands.AddStep(goalId, "Verbs", null, null, StepStatus.NOT_STARTED));
        var listening = competencies.create(goalId, new CompetencyDetails("Listening", "Hear", 4));
        competencies.create(goalId, new CompetencyDetails("Speaking", null, 5));
        competencies.changeLevel(listening.id(), 3);
        measures.create(goalId, new MeasureDetails("Mock", "%", new BigDecimal("100"), new BigDecimal("80"), null,
                CombineRule.AVERAGE, listening.id()));
        var verbs = goals.getGoal(goalId).steps().get(1).id();
        practice.create(new PracticeCommands.CreateRoutine(goalId, verbs, "Conjugate", Cadence.WEEKLY, 4, 20));
        var paused = practice.create(new PracticeCommands.CreateRoutine(goalId, null, "Paused one", Cadence.DAILY, null, 5));
        practice.pause(paused.id());
        reviews.createCard(goalId, new CardDetails(null, "hola", "hello", Set.of("basic"), true));
        return goalId;
    }

    @Test
    void fromGoalDescribesThePlanButNotTheProgress() {
        GoalTemplate template = service.fromGoal(buildGoal());

        assertThat(template.id()).isEqualTo("learn-spanish");
        assertThat(template.name()).isEqualTo("Learn Spanish");
        assertThat(template.description()).isEqualTo("Everyday talk");
        assertThat(template.durationWeeks()).as("15 days rounded up to whole weeks").isEqualTo(3);
        assertThat(template.weeklyTargetMinutes()).isEqualTo(300);
        assertThat(template.steps()).containsExactly(
                new GoalTemplate.Step("Basics", "Greetings", new BigDecimal("6.0")),
                new GoalTemplate.Step("Verbs", null, null));
        assertThat(template.competencies()).containsExactly(
                new GoalTemplate.Competency("Listening", "Hear", 4),
                new GoalTemplate.Competency("Speaking", null, 5));
        assertThat(template.measures()).hasSize(1);
        assertThat(template.measures().getFirst().competency()).isEqualTo("Listening");
        assertThat(template.measures().getFirst().combine()).isEqualTo(CombineRule.AVERAGE);
        assertThat(template.routines()).as("only active routines").containsExactly(
                new GoalTemplate.Routine("Conjugate", Cadence.WEEKLY, 4, 20, 1));
        assertThat(template.cards()).containsExactly(new GoalTemplate.Card("hola", "hello", true));
    }

    @Test
    void aGoalWithoutDeadlineOrOneDueTodayHasNoDuration() {
        GoalId withoutDeadline = goals.createGoal(new GoalCommands.CreateGoal(areaId, "Open", null, null)).id();
        goals.addStep(new GoalCommands.AddStep(withoutDeadline, "Step", null, null, StepStatus.NOT_STARTED));
        GoalId dueToday = goals.createGoal(new GoalCommands.CreateGoal(areaId, "Today", null,
                LocalDate.of(2026, 10, 27))).id();

        assertThat(service.fromGoal(withoutDeadline).durationWeeks()).isNull();
        assertThat(service.fromGoal(dueToday).durationWeeks()).isNull();
    }

    @Test
    void aGoalRoundTripsThroughATemplateKeepingItsPlan() {
        GoalId original = buildGoal();

        GoalTemplate saved = service.saveAsTemplate(original, "My Spanish plan");
        GoalId copy = service.apply(saved.id(), areaId, "Copy", LocalDate.of(2026, 11, 2), ENGLISH);

        assertThat(saved.id()).isEqualTo("my-spanish-plan");
        assertThat(catalog.userTemplates()).containsExactly(saved);
        GoalDetails first = goals.getGoal(original);
        GoalDetails second = goals.getGoal(copy);
        assertThat(second.steps()).extracting(StepView::title, StepView::description, StepView::estimatedHours)
                .containsExactlyElementsOf(first.steps().stream()
                        .map(step -> tuple(step.title(), step.description(), step.estimatedHours())).toList());
        assertThat(second.deadline()).isEqualTo(LocalDate.of(2026, 11, 2).plusWeeks(3));
        assertThat(second.weeklyTargetMinutes()).isEqualTo(300);
        assertThat(competencies.forGoal(copy)).extracting(CompetencyView::name, CompetencyView::targetLevel, CompetencyView::level)
                .containsExactly(tuple("Listening", 4, 0),
                        tuple("Speaking", 5, 0));
        MeasureView measure = measures.forGoal(copy).getFirst();
        assertThat(measure.name()).isEqualTo("Mock");
        assertThat(measure.competencyId()).isEqualTo(competencies.forGoal(copy).getFirst().id());
        assertThat(practice.forGoal(copy)).extracting(RoutineView::title, RoutineView::stepId)
                .containsExactly(tuple("Conjugate", second.steps().get(1).id()));
        assertThat(reviews.forGoal(copy)).extracting(CardView::front, CardView::reversible)
                .containsExactly(tuple("hola", true));
    }

    @Test
    void savingAsATemplateGivesAFreshIdWhenTheNameIsTaken() {
        GoalId goalId = buildGoal();
        catalog.addBuiltIn("en", sample().withIdAndName("my-plan", "Shipped"));
        catalog.addBuiltIn("es", sample().withIdAndName("my-plan", "Incluida"));

        GoalTemplate first = service.saveAsTemplate(goalId, "My plan");
        GoalTemplate second = service.saveAsTemplate(goalId, "My plan");
        GoalTemplate third = service.saveAsTemplate(goalId, "Mi plan de español");

        assertThat(first.id()).isEqualTo("my-plan-2");
        assertThat(second.id()).isEqualTo("my-plan-3");
        assertThat(third.id()).isEqualTo("mi-plan-de-espanol");
        assertThat(catalog.userTemplates()).extracting(GoalTemplate::id)
                .containsExactly("my-plan-2", "my-plan-3", "mi-plan-de-espanol");
    }

    @Test
    void savingAsATemplateNeedsAName() {
        GoalId goalId = buildGoal();

        assertThatThrownBy(() -> service.saveAsTemplate(goalId, "  "))
                .isInstanceOfSatisfying(DomainValidationException.class,
                        problem -> assertThat(problem.code()).isEqualTo("required"));
    }

    // ---------- files ----------

    @Test
    void importedFilesAreValidatedAndStoredAsUserTemplates() {
        Path file = Path.of("friend.json");
        catalog.putFile(file, sample().withIdAndName("friend-plan", "Friend's plan"));

        GoalTemplate imported = service.importFile(file);

        assertThat(imported.id()).isEqualTo("friend-plan");
        assertThat(catalog.userTemplates()).containsExactly(imported);
    }

    @Test
    void importedTemplatesGetAFreshIdWhenTheirIdIsTaken() {
        catalog.addBuiltIn("en", sample());
        Path file = Path.of("friend.json");
        catalog.putFile(file, sample());

        GoalTemplate first = service.importFile(file);
        GoalTemplate second = service.importFile(file);

        assertThat(first.id()).isEqualTo("sample-2");
        assertThat(second.id()).isEqualTo("sample-3");
        assertThat(first.name()).isEqualTo("Sample plan");
        assertThat(service.list(ENGLISH)).extracting(GoalTemplate::id).containsExactly("sample", "sample-2", "sample-3");
    }

    @Test
    void invalidFilesAreRefusedAndNothingIsStored() {
        Path broken = Path.of("broken.json");
        catalog.putFile(broken, new GoalTemplate("bad id!", "Bad", null, null, null,
                List.of(new GoalTemplate.Step("Step", null, null)), null, null, null, null));
        Path empty = Path.of("empty.json");
        catalog.putFile(empty, new GoalTemplate("empty", "No steps", null, null, null, null, null, null, null, null));
        Path unknown = Path.of("unknown.json");

        for (Path file : List.of(broken, empty, unknown)) {
            assertThatThrownBy(() -> service.importFile(file))
                    .isInstanceOfSatisfying(DomainValidationException.class,
                            problem -> assertThat(problem.code()).isEqualTo("template-invalid"));
        }
        assertThat(catalog.userTemplates()).isEmpty();
    }

    @Test
    void exportWritesTheTemplateInTheRequestedLanguageEnglishByDefault() {
        GoalTemplate english = sample();
        GoalTemplate spanish = sample().withIdAndName("sample", "Plan de prueba");
        catalog.addBuiltIn("en", english);
        catalog.addBuiltIn("es", spanish);

        service.exportFile("sample", Path.of("english.json"));
        service.exportFile("sample", Path.of("spanish.json"), SPANISH);

        assertThat(catalog.fileAt(Path.of("english.json"))).isEqualTo(english);
        assertThat(catalog.fileAt(Path.of("spanish.json"))).isEqualTo(spanish);
        assertThatThrownBy(() -> service.exportFile("nope", Path.of("x.json"))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void userTemplatesCanBeDeleted() {
        catalog.saveUserTemplate(sample());

        service.deleteUserTemplate("sample");

        assertThat(catalog.userTemplates()).isEmpty();
    }

    // ---------- validation of the template itself ----------

    @Test
    void validateAppliesTheDomainRulesAndChecksReferences() {
        GoalTemplate valid = sample();
        valid.validate();

        assertCode(withSteps(valid, List.of()), "template-invalid");
        assertCode(new GoalTemplate("ok", " ", null, null, null, valid.steps(), null, null, null, null), "required");
        assertCode(new GoalTemplate("ok", "Name", null, 0, null, valid.steps(), null, null, null, null), "out-of-range");
        assertCode(new GoalTemplate("ok", "Name", null, null, 20000, valid.steps(), null, null, null, null), "out-of-range");
        assertCode(new GoalTemplate("has space", "Name", null, null, null, valid.steps(), null, null, null, null),
                "template-invalid");
        assertCode(new GoalTemplate("ok", "Name", null, null, null, valid.steps(),
                List.of(new GoalTemplate.Competency("A", null, 4), new GoalTemplate.Competency(" a ", null, 3)), null,
                null, null), "template-invalid");
        assertCode(new GoalTemplate("ok", "Name", null, null, null, valid.steps(),
                List.of(new GoalTemplate.Competency("A", null, 9)), null, null, null), "out-of-range");
        assertCode(new GoalTemplate("ok", "Name", null, null, null, valid.steps(), null, null,
                List.of(new GoalTemplate.Routine("R", Cadence.WEEKLY, 3, 20, 3)), null), "template-invalid");
        assertCode(new GoalTemplate("ok", "Name", null, null, null, valid.steps(), null, null,
                List.of(new GoalTemplate.Routine("R", Cadence.WEEKLY, 9, 20, null)), null), "out-of-range");
        assertCode(new GoalTemplate("ok", "Name", null, null, null, valid.steps(), null, null, null,
                List.of(new GoalTemplate.Card("front", " ", false))), "required");
        assertCode(new GoalTemplate("ok", "Name", null, null, null, valid.steps(), null,
                List.of(new GoalTemplate.Measure("M", null, new BigDecimal("5"), new BigDecimal("9"), null, null, null)),
                null, null), "too-large");
    }

    private static GoalTemplate withSteps(GoalTemplate template, List<GoalTemplate.Step> steps) {
        return new GoalTemplate(template.id(), template.name(), template.description(), template.durationWeeks(),
                template.weeklyTargetMinutes(), steps, template.competencies(), template.measures(),
                template.routines(), template.cards());
    }

    private static void assertCode(GoalTemplate template, String code) {
        assertThatThrownBy(template::validate)
                .isInstanceOfSatisfying(DomainValidationException.class,
                        problem -> assertThat(problem.code()).isEqualTo(code));
    }

    @Test
    void slugsAreLowerCaseAsciiWithDashes() {
        assertThat(TemplateService.slug("Aprender Español: nivel B2!")).isEqualTo("aprender-espanol-nivel-b2");
        assertThat(TemplateService.slug("  --  ")).isEqualTo("template");
        assertThat(TemplateService.slug("日本語")).isEqualTo("template");
        assertThat(TemplateService.slug("a".repeat(100))).hasSize(40);
    }
}

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
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.measure.MeasureDetails;
import com.edutrack.domain.review.CardDetails;
import com.edutrack.domain.shared.Validation;
import com.edutrack.domain.skill.CompetencyDetails;
import com.edutrack.domain.skill.CompetencyId;

import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Use cases for goal templates: starting a goal from a ready-made plan, turning a goal the learner
 * built into a template, and exchanging templates as files. Applying a template goes through the
 * ordinary services, so everything it creates obeys the same rules as anything typed by hand.
 */
public class TemplateService {

    private static final int SLUG_MAX_LENGTH = 40;

    private final TemplateCatalog catalog;
    private final GoalService goals;
    private final CompetencyService competencies;
    private final MeasureService measures;
    private final PracticeService practice;
    private final ReviewService reviews;
    private final Clock clock;

    public TemplateService(TemplateCatalog catalog, GoalService goals, CompetencyService competencies,
                           MeasureService measures, PracticeService practice, ReviewService reviews, Clock clock) {
        this.catalog = catalog;
        this.goals = goals;
        this.competencies = competencies;
        this.measures = measures;
        this.practice = practice;
        this.reviews = reviews;
        this.clock = clock;
    }

    /** The shipped templates in the locale's language, then the learner's own. */
    public List<GoalTemplate> list(Locale locale) {
        return Stream.concat(catalog.builtIn(locale).stream(), catalog.userTemplates().stream()).toList();
    }

    /**
     * Creates a goal in the area from the template: the roadmap, competencies, measures, practice
     * routines and starter cards. {@code title} defaults to the template's name; the deadline is
     * {@code start} (default today) plus the template's duration, when it has one. Nothing is created
     * unless the whole template is valid, and a failure half-way removes the goal again.
     *
     * @return the id of the new goal
     */
    public GoalId apply(String templateId, AreaId areaId, String title, LocalDate start, Locale locale) {
        GoalTemplate template = find(templateId, locale);
        template.validate();
        LocalDate begin = start == null ? LocalDate.now(clock) : start;
        LocalDate deadline = template.durationWeeks() == null ? null : begin.plusWeeks(template.durationWeeks());
        String goalTitle = title == null || title.isBlank() ? template.name() : title;

        GoalId goalId = goals.createGoal(new GoalCommands.CreateGoal(areaId, goalTitle, template.description(),
                deadline, template.weeklyTargetMinutes())).id();
        try {
            populate(goalId, template);
        } catch (RuntimeException failure) {
            goals.deleteGoal(goalId);
            throw failure;
        }
        return goalId;
    }

    /**
     * A template describing the goal: its roadmap with hours, competencies with target levels, measures,
     * active practice routines and cards. The template's id and name come from the goal's title.
     * Nothing recorded (levels, results, notes, sessions) is included.
     */
    public GoalTemplate fromGoal(GoalId goalId) {
        GoalDetails goal = goals.getGoal(goalId);
        List<GoalTemplate.Step> steps = goal.steps().stream()
                .map(step -> new GoalTemplate.Step(step.title(), step.description(), step.estimatedHours()))
                .toList();

        List<CompetencyView> goalCompetencies = competencies.forGoal(goalId);
        Map<CompetencyId, String> competencyNames = new HashMap<>();
        goalCompetencies.forEach(competency -> competencyNames.put(competency.id(), competency.name()));
        List<GoalTemplate.Competency> templateCompetencies = goalCompetencies.stream()
                .map(competency -> new GoalTemplate.Competency(competency.name(), competency.description(),
                        competency.targetLevel()))
                .toList();

        List<GoalTemplate.Measure> templateMeasures = new ArrayList<>();
        for (MeasureView measure : measures.forGoal(goalId)) {
            templateMeasures.add(new GoalTemplate.Measure(measure.name(), measure.unit(), measure.maxValue(),
                    measure.target(), measure.direction(), measure.combineRule(),
                    measure.competencyId() == null ? null : competencyNames.get(measure.competencyId())));
        }

        List<GoalTemplate.Routine> routines = new ArrayList<>();
        for (RoutineView routine : practice.forGoal(goalId)) {
            if (routine.active()) {
                routines.add(new GoalTemplate.Routine(routine.title(), routine.cadence(), routine.timesPerWeek(),
                        routine.targetMinutes(), stepIndex(goal, routine.stepId())));
            }
        }

        List<GoalTemplate.Card> cards = reviews.forGoal(goalId).stream()
                .map(card -> new GoalTemplate.Card(card.front(), card.back(), card.reversible()))
                .toList();

        return new GoalTemplate(slug(goal.title()), goal.title(), goal.description(), durationWeeks(goal),
                goal.weeklyTargetMinutes(), steps, templateCompetencies, templateMeasures, routines, cards);
    }

    /** Saves the goal's plan as one of the learner's own templates, under a fresh id, and returns it. */
    public GoalTemplate saveAsTemplate(GoalId goalId, String name) {
        String validName = Validation.requiredText(name, "Name", GoalTemplate.NAME_MAX_LENGTH);
        GoalTemplate template = fromGoal(goalId).withIdAndName(freshId(slug(validName)), validName);
        template.validate();
        catalog.saveUserTemplate(template);
        return template;
    }

    /** Reads a template file, checks it and keeps it as one of the learner's templates, under a fresh id when its id is taken. */
    public GoalTemplate importFile(Path file) {
        GoalTemplate template = catalog.readFile(file);
        template.validate();
        GoalTemplate stored = template.withIdAndName(freshId(template.id()), template.name());
        catalog.saveUserTemplate(stored);
        return stored;
    }

    /** Writes the template (any shipped one in English) to a file other people can import. */
    public void exportFile(String templateId, Path file) {
        exportFile(templateId, file, Locale.ENGLISH);
    }

    public void exportFile(String templateId, Path file, Locale locale) {
        GoalTemplate template = find(templateId, locale);
        catalog.writeFile(template, file);
    }

    /** Removes one of the learner's templates; the shipped ones cannot be removed. */
    public void deleteUserTemplate(String templateId) {
        catalog.deleteUserTemplate(templateId);
    }

    // ---------- helpers ----------

    private GoalTemplate find(String templateId, Locale locale) {
        return list(locale).stream()
                .filter(candidate -> candidate.id().equals(templateId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Template", templateId));
    }

    private void populate(GoalId goalId, GoalTemplate template) {
        List<StepId> stepIds = new ArrayList<>();
        for (GoalTemplate.Step step : template.steps()) {
            GoalDetails details = goals.addStep(new GoalCommands.AddStep(goalId, step.title(), step.description(),
                    step.hours(), StepStatus.NOT_STARTED));
            stepIds.add(details.steps().getLast().id());
        }

        Map<String, CompetencyId> competencyIds = new HashMap<>();
        for (GoalTemplate.Competency competency : template.competencies()) {
            CompetencyView created = competencies.create(goalId,
                    new CompetencyDetails(competency.name(), competency.description(), competency.target()));
            competencyIds.put(TemplateChecks.key(competency.name()), created.id());
        }

        for (GoalTemplate.Measure measure : template.measures()) {
            CompetencyId competencyId = measure.competency() == null ? null
                    : competencyIds.get(TemplateChecks.key(measure.competency()));
            measures.create(goalId, new MeasureDetails(measure.name(), measure.unit(), measure.max(),
                    measure.target(), measure.direction(), measure.combine(), competencyId));
        }

        for (GoalTemplate.Routine routine : template.routines()) {
            StepId stepId = routine.step() == null ? null : stepIds.get(routine.step());
            practice.create(new PracticeCommands.CreateRoutine(goalId, stepId, routine.title(), routine.cadence(),
                    routine.timesPerWeek(), routine.minutes()));
        }

        for (GoalTemplate.Card card : template.cards()) {
            reviews.createCard(goalId, new CardDetails(null, card.front(), card.back(), Set.of(), card.reversible()));
        }
    }

    private static Integer stepIndex(GoalDetails goal, StepId stepId) {
        if (stepId == null) {
            return null;
        }
        for (StepView step : goal.steps()) {
            if (step.id().equals(stepId)) {
                return step.number() - 1;
            }
        }
        return null;
    }

    /** Whole weeks between the day the goal was created and its deadline, rounded up; none without a deadline. */
    private Integer durationWeeks(GoalDetails goal) {
        if (goal.deadline() == null || goal.createdAt() == null) {
            return null;
        }
        LocalDate created = goal.createdAt().atZone(clock.getZone()).toLocalDate();
        long days = ChronoUnit.DAYS.between(created, goal.deadline());
        if (days < 1) {
            return null;
        }
        return (int) Math.min(GoalTemplate.MAX_DURATION_WEEKS, (days + 6) / 7);
    }

    /** {@code wanted}, or {@code wanted-2}, {@code wanted-3}... when a shipped or user template already has the id. */
    private String freshId(String wanted) {
        Set<String> taken = new HashSet<>();
        list(Locale.ENGLISH).forEach(template -> taken.add(template.id()));
        list(Locale.forLanguageTag("es")).forEach(template -> taken.add(template.id()));
        if (!taken.contains(wanted)) {
            return wanted;
        }
        for (int number = 2; ; number++) {
            String suffix = "-" + number;
            String candidate = wanted.substring(0, Math.min(wanted.length(), 64 - suffix.length())) + suffix;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    /** "Aprender Español: nivel B2" → "aprender-espanol-nivel-b2"; "template" when nothing is left. */
    static String slug(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > SLUG_MAX_LENGTH) {
            slug = slug.substring(0, SLUG_MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "template" : slug;
    }
}

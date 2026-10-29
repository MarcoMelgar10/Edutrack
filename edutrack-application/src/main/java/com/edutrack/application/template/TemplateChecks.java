package com.edutrack.application.template;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.measure.Measure;
import com.edutrack.domain.measure.MeasureDetails;
import com.edutrack.domain.practice.PracticeRoutine;
import com.edutrack.domain.practice.RoutineDetails;
import com.edutrack.domain.review.Card;
import com.edutrack.domain.review.CardDetails;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;
import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyDetails;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Validates a template by building throw-away domain objects from it, so the template rules are never
 * a copy of the domain rules: whatever the domain would refuse when the template is applied is refused here.
 */
final class TemplateChecks {

    private TemplateChecks() {
    }

    static void check(GoalTemplate template) {
        if (!GoalTemplate.isValidId(template.id())) {
            throw invalid("The template id must be 1 to 64 letters, digits, '-' or '_'.");
        }
        Validation.requiredText(template.name(), "Name", GoalTemplate.NAME_MAX_LENGTH);
        Validation.optionalText(template.description(), "Description", Validation.DESCRIPTION_MAX_LENGTH);
        Integer weeks = template.durationWeeks();
        if (weeks != null && (weeks < 1 || weeks > GoalTemplate.MAX_DURATION_WEEKS)) {
            throw new DomainValidationException("out-of-range", "Duration must be between 1 and "
                    + GoalTemplate.MAX_DURATION_WEEKS + " weeks.", "Duration", 1, GoalTemplate.MAX_DURATION_WEEKS);
        }
        if (template.steps().isEmpty()) {
            throw invalid("A template needs at least one step.");
        }

        Instant now = Instant.EPOCH;
        Goal goal = Goal.create(new AreaId(UUID.randomUUID()), template.name(), template.description(), null,
                template.weeklyTargetMinutes(), 0, now);
        for (GoalTemplate.Step step : template.steps()) {
            goal.addStep(new StepDetails(step.title(), step.description(), step.hours()), StepStatus.NOT_STARTED, now);
        }
        GoalId goalId = goal.id();

        Set<String> competencyNames = new HashSet<>();
        for (GoalTemplate.Competency competency : template.competencies()) {
            Competency.create(goalId,
                    new CompetencyDetails(competency.name(), competency.description(), competency.target()), 0, now);
            if (!competencyNames.add(key(competency.name()))) {
                throw invalid("Two competencies are called \"" + competency.name() + "\".");
            }
        }
        for (GoalTemplate.Measure measure : template.measures()) {
            Measure.create(goalId, new MeasureDetails(measure.name(), measure.unit(), measure.max(), measure.target(),
                    measure.direction(), measure.combine(), null), 0, now);
            if (measure.competency() != null && !competencyNames.contains(key(measure.competency()))) {
                throw invalid("The measure \"" + measure.name() + "\" refers to the unknown competency \""
                        + measure.competency() + "\".");
            }
        }
        for (GoalTemplate.Routine routine : template.routines()) {
            PracticeRoutine.create(goalId, new RoutineDetails(null, routine.title(), routine.cadence(),
                    routine.timesPerWeek(), routine.minutes()), 0, now);
            if (routine.step() != null && (routine.step() < 0 || routine.step() >= template.steps().size())) {
                throw invalid("The routine \"" + routine.title() + "\" refers to step " + routine.step()
                        + ", which does not exist.");
            }
        }
        for (GoalTemplate.Card card : template.cards()) {
            Card.create(goalId, new CardDetails(null, card.front(), card.back(), Set.of(), card.reversible()),
                    LocalDate.EPOCH, now);
        }
    }

    /** Competencies are found by name regardless of case, as the user would type them. */
    static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    static DomainValidationException invalid(String reason) {
        return new DomainValidationException("template-invalid", reason, reason);
    }
}

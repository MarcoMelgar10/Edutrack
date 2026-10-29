package com.edutrack.application.template;

import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.practice.Cadence;
import com.edutrack.domain.shared.DomainValidationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A reusable plan for a goal: a roadmap with hour estimates, the competencies it builds, the
 * measures it tracks, practice routines and starter flashcards. It mirrors the JSON template
 * format (see {@code templates/README.md}); everything but {@code id}, {@code name} and
 * {@code steps} is optional and missing lists are empty.
 *
 * @param id                  stable identifier, also the file name of a user template
 * @param durationWeeks       how long the plan is meant to take; applying it sets the deadline that far from the start
 * @param weeklyTargetMinutes the study time per week the plan suggests
 */
public record GoalTemplate(String id, String name, String description, Integer durationWeeks,
                           Integer weeklyTargetMinutes, List<Step> steps, List<Competency> competencies,
                           List<Measure> measures, List<Routine> routines, List<Card> cards) {

    public static final int NAME_MAX_LENGTH = 120;
    public static final int MAX_DURATION_WEEKS = 520;

    /** Ids are used as file names, so they stay simple. */
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    public record Step(String title, String description, BigDecimal hours) {
    }

    public record Competency(String name, String description, int target) {
    }

    /** {@code competency} is the name of one of the template's competencies, or {@code null}. */
    public record Measure(String name, String unit, BigDecimal max, BigDecimal target, Direction direction,
                          CombineRule combine, String competency) {
    }

    /** {@code step} is the 0-based index of one of the template's steps, or {@code null}. */
    public record Routine(String title, Cadence cadence, Integer timesPerWeek, int minutes, Integer step) {
    }

    public record Card(String front, String back, boolean reversible) {
    }

    public GoalTemplate {
        steps = steps == null ? List.of() : List.copyOf(steps);
        competencies = competencies == null ? List.of() : List.copyOf(competencies);
        measures = measures == null ? List.of() : List.copyOf(measures);
        routines = routines == null ? List.of() : List.copyOf(routines);
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    /** The same template under another id and name (a copy saved or imported by the user). */
    public GoalTemplate withIdAndName(String newId, String newName) {
        return new GoalTemplate(newId, newName, description, durationWeeks, weeklyTargetMinutes, steps,
                competencies, measures, routines, cards);
    }

    /** Whether {@code id} can be used as a template id (and so as a file name). */
    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    /**
     * Checks everything the domain would check when the template is applied, plus the references between
     * its parts, so that applying a valid template cannot fail half-way.
     *
     * @throws DomainValidationException {@code template-invalid} for a bad id, an empty roadmap or a broken
     *                                   reference, otherwise the code of the domain rule that was broken
     */
    public void validate() {
        TemplateChecks.check(this);
    }
}

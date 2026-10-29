package com.edutrack.domain.skill;

/**
 * The editable content of a competency as typed in its dialog, e.g. "Listening", level 5 wanted.
 * Validated by {@link Competency}; the current level is changed separately, so each change is dated.
 */
public record CompetencyDetails(String name, String description, Integer targetLevel) {
}

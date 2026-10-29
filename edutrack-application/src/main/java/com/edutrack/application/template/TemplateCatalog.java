package com.edutrack.application.template;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Port for where templates come from: the ones shipped with the app (in the learner's language), the
 * ones the learner saved or imported, and template files exchanged with other people. Implemented by
 * the infrastructure layer.
 */
public interface TemplateCatalog {

    /** The shipped templates written in the locale's language; English for any language without its own set. */
    List<GoalTemplate> builtIn(Locale locale);

    /** The learner's own templates, oldest first. */
    List<GoalTemplate> userTemplates();

    /** Stores the template as a user template, replacing the one with the same id. */
    void saveUserTemplate(GoalTemplate template);

    /** Forgets a user template; one that does not exist is ignored. */
    void deleteUserTemplate(String id);

    /**
     * Reads a template file.
     *
     * @throws com.edutrack.domain.shared.DomainValidationException {@code template-invalid} when the file is not a template
     */
    GoalTemplate readFile(Path file);

    void writeFile(GoalTemplate template, Path file);
}

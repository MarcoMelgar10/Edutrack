package com.edutrack.application.support;

import com.edutrack.application.template.GoalTemplate;
import com.edutrack.application.template.TemplateCatalog;
import com.edutrack.domain.shared.DomainValidationException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Templates kept in memory: shipped ones per language, the user's own, and "files" keyed by path. */
public class InMemoryTemplateCatalog implements TemplateCatalog {

    private final Map<String, List<GoalTemplate>> shipped = new HashMap<>();
    private final List<GoalTemplate> user = new ArrayList<>();
    private final Map<Path, GoalTemplate> files = new HashMap<>();

    public void addBuiltIn(String language, GoalTemplate template) {
        shipped.computeIfAbsent(language, key -> new ArrayList<>()).add(template);
    }

    /** Puts a template where {@link #readFile} will find it, as if it had been saved there. */
    public void putFile(Path file, GoalTemplate template) {
        files.put(file, template);
    }

    public GoalTemplate fileAt(Path file) {
        return files.get(file);
    }

    @Override
    public List<GoalTemplate> builtIn(Locale locale) {
        return List.copyOf(shipped.getOrDefault(locale.getLanguage(), shipped.getOrDefault("en", List.of())));
    }

    @Override
    public List<GoalTemplate> userTemplates() {
        return List.copyOf(user);
    }

    @Override
    public void saveUserTemplate(GoalTemplate template) {
        user.removeIf(existing -> existing.id().equals(template.id()));
        user.add(template);
    }

    @Override
    public void deleteUserTemplate(String id) {
        user.removeIf(existing -> existing.id().equals(id));
    }

    @Override
    public GoalTemplate readFile(Path file) {
        GoalTemplate template = files.get(file);
        if (template == null) {
            throw new DomainValidationException("template-invalid", "The file is not a template.", "The file is not a template.");
        }
        return template;
    }

    @Override
    public void writeFile(GoalTemplate template, Path file) {
        files.put(file, template);
    }
}

package com.edutrack.infrastructure.template;

import com.edutrack.application.template.GoalTemplate;
import com.edutrack.application.template.TemplateCatalog;
import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.practice.Cadence;
import com.edutrack.domain.shared.DomainValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Templates as JSON files (the format is described in {@code templates/README.md}). The ones shipped with
 * the app are classpath resources under {@code templates/<language>/}; the learner's own are
 * {@code <id>.json} files in {@code edutrack.templates-dir} (default {@code ~/.edutrack/templates}).
 */
@Component
public class JsonTemplateCatalog implements TemplateCatalog {

    private static final String DEFAULT_LANGUAGE = "en";
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final Path userDirectory;
    private final JsonMapper json = JsonMapper.builder().build();
    private final PathMatchingResourcePatternResolver resources = new PathMatchingResourcePatternResolver();
    private final Map<String, List<GoalTemplate>> shipped = new ConcurrentHashMap<>();

    @Autowired
    public JsonTemplateCatalog(@Value("${edutrack.templates-dir:${user.home}/.edutrack/templates}") String userDirectory) {
        this(Path.of(userDirectory));
    }

    public JsonTemplateCatalog(Path userDirectory) {
        this.userDirectory = userDirectory.toAbsolutePath().normalize();
    }

    // ---------- catalog ----------

    @Override
    public List<GoalTemplate> builtIn(Locale locale) {
        String language = locale == null ? DEFAULT_LANGUAGE : locale.getLanguage();
        List<GoalTemplate> templates = shipped.computeIfAbsent(language, this::loadShipped);
        return templates.isEmpty() ? shipped.computeIfAbsent(DEFAULT_LANGUAGE, this::loadShipped) : templates;
    }

    @Override
    public List<GoalTemplate> userTemplates() {
        if (!Files.isDirectory(userDirectory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(userDirectory)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".json") && Files.isRegularFile(file))
                    .sorted(Comparator.comparing(JsonTemplateCatalog::modified).thenComparing(Path::getFileName))
                    .map(this::readQuietly)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + userDirectory, e);
        }
    }

    @Override
    public void saveUserTemplate(GoalTemplate template) {
        Path target = userFile(template.id());
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.createDirectories(userDirectory);
            writeFile(template, partial);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + target, e);
        }
    }

    @Override
    public void deleteUserTemplate(String id) {
        Path file = userFile(id);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + file, e);
        }
    }

    @Override
    public GoalTemplate readFile(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw invalid("The file is too big to be a template.");
            }
            try (InputStream in = Files.newInputStream(file)) {
                return parse(json.readTree(in));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        } catch (JacksonException notJson) {
            throw invalid("The file is not valid JSON.");
        }
    }

    @Override
    public void writeFile(GoalTemplate template, Path file) {
        try (var out = Files.newOutputStream(file)) {
            json.writerWithDefaultPrettyPrinter().writeValue(out, toJson(template));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    // ---------- loading ----------

    private List<GoalTemplate> loadShipped(String language) {
        try {
            Resource[] files = resources.getResources("classpath*:templates/" + language + "/*.json");
            return Arrays.stream(files)
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .map(this::readResource)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the " + language + " templates", e);
        }
    }

    private GoalTemplate readResource(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return parse(json.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }

    private Optional<GoalTemplate> readQuietly(Path file) {
        try {
            return Optional.of(readFile(file));
        } catch (DomainValidationException | UncheckedIOException unreadable) {
            // one damaged file must not hide the learner's other templates
            return Optional.empty();
        }
    }

    private Path userFile(String id) {
        if (!GoalTemplate.isValidId(id)) {
            throw invalid("The template id must be 1 to 64 letters, digits, '-' or '_'.");
        }
        return userDirectory.resolve(id + ".json");
    }

    private static FileTime modified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    // ---------- parsing ----------

    private static GoalTemplate parse(JsonNode root) {
        if (!root.isObject()) {
            throw invalid("The file does not contain a template.");
        }
        return new GoalTemplate(text(root, "id"), text(root, "name"), text(root, "description"),
                integer(root, "durationWeeks"), integer(root, "weeklyTargetMinutes"),
                list(root, "steps", step -> new GoalTemplate.Step(text(step, "title"), text(step, "description"),
                        decimal(step, "hours"))),
                list(root, "competencies", competency -> new GoalTemplate.Competency(text(competency, "name"),
                        text(competency, "description"), integer(competency, "target") == null ? 0
                        : integer(competency, "target"))),
                list(root, "measures", measure -> new GoalTemplate.Measure(text(measure, "name"),
                        text(measure, "unit"), decimal(measure, "max"), decimal(measure, "target"),
                        constant(measure, "direction", Direction.class), constant(measure, "combine", CombineRule.class),
                        text(measure, "competency"))),
                list(root, "routines", routine -> new GoalTemplate.Routine(text(routine, "title"),
                        constant(routine, "cadence", Cadence.class), integer(routine, "timesPerWeek"),
                        integer(routine, "minutes") == null ? 0 : integer(routine, "minutes"), integer(routine, "step"))),
                list(root, "cards", card -> new GoalTemplate.Card(text(card, "front"), text(card, "back"),
                        flag(card, "reversible"))));
    }

    private static <T> List<T> list(JsonNode parent, String field, Function<JsonNode, T> item) {
        JsonNode array = parent.path(field);
        if (array.isMissingNode() || array.isNull()) {
            return List.of();
        }
        if (!array.isArray()) {
            throw invalid("\"" + field + "\" must be a list.");
        }
        List<T> items = new ArrayList<>();
        for (JsonNode element : array) {
            if (!element.isObject()) {
                throw invalid("Every entry of \"" + field + "\" must be an object.");
            }
            items.add(item.apply(element));
        }
        return items;
    }

    private static String text(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw invalid("\"" + field + "\" must be text.");
        }
        return value.stringValue();
    }

    private static Integer integer(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid("\"" + field + "\" must be a whole number.");
        }
        return value.intValue();
    }

    private static BigDecimal decimal(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw invalid("\"" + field + "\" must be a number.");
        }
        return value.decimalValue();
    }

    private static boolean flag(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return false;
        }
        if (!value.isBoolean()) {
            throw invalid("\"" + field + "\" must be true or false.");
        }
        return value.booleanValue();
    }

    private static <E extends Enum<E>> E constant(JsonNode parent, String field, Class<E> type) {
        String name = text(parent, field);
        if (name == null) {
            return null;
        }
        String normalized = name.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException unknown) {
            throw invalid("\"" + name + "\" is not a valid " + field + ".");
        }
    }

    // ---------- writing ----------

    private ObjectNode toJson(GoalTemplate template) {
        ObjectNode root = json.createObjectNode();
        root.put("id", template.id());
        root.put("name", template.name());
        putIfPresent(root, "description", template.description());
        putIfPresent(root, "durationWeeks", template.durationWeeks());
        putIfPresent(root, "weeklyTargetMinutes", template.weeklyTargetMinutes());
        ArrayNode steps = root.putArray("steps");
        for (GoalTemplate.Step step : template.steps()) {
            ObjectNode node = steps.addObject();
            node.put("title", step.title());
            putIfPresent(node, "description", step.description());
            putIfPresent(node, "hours", step.hours());
        }
        ArrayNode competencies = root.putArray("competencies");
        for (GoalTemplate.Competency competency : template.competencies()) {
            ObjectNode node = competencies.addObject();
            node.put("name", competency.name());
            putIfPresent(node, "description", competency.description());
            node.put("target", competency.target());
        }
        ArrayNode measures = root.putArray("measures");
        for (GoalTemplate.Measure measure : template.measures()) {
            ObjectNode node = measures.addObject();
            node.put("name", measure.name());
            putIfPresent(node, "unit", measure.unit());
            putIfPresent(node, "max", measure.max());
            putIfPresent(node, "target", measure.target());
            putIfPresent(node, "direction", measure.direction() == null ? null : measure.direction().name());
            putIfPresent(node, "combine", measure.combine() == null ? null : measure.combine().name());
            putIfPresent(node, "competency", measure.competency());
        }
        ArrayNode routines = root.putArray("routines");
        for (GoalTemplate.Routine routine : template.routines()) {
            ObjectNode node = routines.addObject();
            node.put("title", routine.title());
            node.put("cadence", routine.cadence() == null ? null : routine.cadence().name());
            putIfPresent(node, "timesPerWeek", routine.timesPerWeek());
            node.put("minutes", routine.minutes());
            putIfPresent(node, "step", routine.step());
        }
        ArrayNode cards = root.putArray("cards");
        for (GoalTemplate.Card card : template.cards()) {
            ObjectNode node = cards.addObject();
            node.put("front", card.front());
            node.put("back", card.back());
            if (card.reversible()) {
                node.put("reversible", true);
            }
        }
        return root;
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private static void putIfPresent(ObjectNode node, String field, Integer value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private static void putIfPresent(ObjectNode node, String field, BigDecimal value) {
        if (value != null) {
            node.put(field, value.stripTrailingZeros().scale() < 0 ? value.setScale(0) : value.stripTrailingZeros());
        }
    }

    private static DomainValidationException invalid(String reason) {
        return new DomainValidationException("template-invalid", reason, reason);
    }
}

package com.edutrack.desktop.ui.common;

import com.edutrack.desktop.ui.shell.ShellPage;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.record.ResourceType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the two language bundles and the places that use them in step: same keys, every key the code or
 * an FXML file refers to exists, and no FXML text slips through untranslated.
 */
class BundleConsistencyTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path SOURCES = Path.of("src/main/java");

    private final Map<String, String> english = load("/i18n/messages.properties");
    private final Map<String, String> spanish = load("/i18n/messages_es.properties");

    @Test
    void bothLanguagesHaveExactlyTheSameKeys() {
        Set<String> onlyEnglish = new TreeSet<>(english.keySet());
        onlyEnglish.removeAll(spanish.keySet());
        Set<String> onlySpanish = new TreeSet<>(spanish.keySet());
        onlySpanish.removeAll(english.keySet());

        assertThat(onlyEnglish).as("keys missing from messages_es.properties").isEmpty();
        assertThat(onlySpanish).as("keys missing from messages.properties").isEmpty();
    }

    @Test
    void noTextIsBlank() {
        assertThat(english.entrySet().stream().filter(entry -> entry.getValue().isBlank()).map(Map.Entry::getKey))
                .as("blank English texts").isEmpty();
        assertThat(spanish.entrySet().stream().filter(entry -> entry.getValue().isBlank()).map(Map.Entry::getKey))
                .as("blank Spanish texts").isEmpty();
    }

    @Test
    void everyPluralKeyHasBothForms() {
        for (Map<String, String> bundle : java.util.List.of(english, spanish)) {
            Set<String> bases = bundle.keySet().stream()
                    .filter(key -> key.endsWith(".one") || key.endsWith(".other"))
                    .map(key -> key.substring(0, key.lastIndexOf('.')))
                    .collect(Collectors.toCollection(TreeSet::new));
            for (String base : bases) {
                assertThat(bundle).containsKeys(base + ".one", base + ".other");
            }
        }
    }

    @Test
    void textsWithArgumentsEscapeApostrophesAndTheOthersDoNot() {
        Pattern argument = Pattern.compile("\\{\\d");
        Pattern lone = Pattern.compile("(?<!')'(?!')");
        for (Map<String, String> bundle : java.util.List.of(english, spanish)) {
            bundle.forEach((key, text) -> {
                boolean formatted = argument.matcher(text).find();
                if (formatted) {
                    assertThat(lone.matcher(text).find()).as("lone apostrophe in %s", key).isFalse();
                } else {
                    assertThat(text).as("doubled apostrophe in %s", key).doesNotContain("''");
                }
            });
        }
    }

    @Test
    void bothLanguagesUseTheSameArguments() {
        Pattern argument = Pattern.compile("\\{(\\d)");
        english.forEach((key, text) -> {
            if (spanish.containsKey(key)) {
                assertThat(arguments(argument, spanish.get(key))).as("arguments of %s", key)
                        .isEqualTo(arguments(argument, text));
            }
        });
    }

    @Test
    void everyKeyUsedInFxmlExists() throws IOException {
        Pattern reference = Pattern.compile("=\"%([A-Za-z0-9_.-]+)\"");
        for (Path file : files(RESOURCES, ".fxml")) {
            Matcher matcher = reference.matcher(Files.readString(file));
            while (matcher.find()) {
                assertThat(english).as("%s uses %%%s", file.getFileName(), matcher.group(1)).containsKey(matcher.group(1));
            }
        }
    }

    @Test
    void noFxmlTextIsLeftUntranslated() throws IOException {
        Pattern literal = Pattern.compile("\\b(text|promptText|accessibleText|title|headerText)=\"([^\"%][^\"]*)\"");
        for (Path file : files(RESOURCES, ".fxml")) {
            Matcher matcher = literal.matcher(Files.readString(file));
            while (matcher.find()) {
                assertThat(matcher.group(2)).as("%s: %s", file.getFileName(), matcher.group(0))
                        .doesNotContainPattern("\\p{L}{2,}");
            }
        }
    }

    @Test
    void everyKeyUsedInJavaExists() throws IOException {
        Pattern call = Pattern.compile("\\b(get|plural)\\(\\s*\"([A-Za-z0-9_.-]+)\"");
        for (Path file : files(SOURCES, ".java")) {
            Matcher matcher = call.matcher(Files.readString(file));
            while (matcher.find()) {
                String key = matcher.group(2);
                if (key.endsWith(".")) {
                    continue; // a prefix completed at run time; the enum checks below cover those
                }
                String[] keys = matcher.group(1).equals("plural")
                        ? new String[]{key + ".one", key + ".other"} : new String[]{key};
                for (String full : keys) {
                    assertThat(english).as("%s uses %s", file.getFileName(), full).containsKey(full);
                }
            }
        }
    }

    @Test
    void everyEnumValueTheUiShowsHasAText() {
        for (AreaIcon icon : AreaIcon.values()) {
            assertThat(english).containsKey("icon." + icon.name());
        }
        for (ShellPage page : ShellPage.values()) {
            assertThat(english).containsKey(page.titleKey());
        }
        for (StepStatus status : StepStatus.values()) {
            assertThat(english).containsKey("status." + status.name());
        }
        for (ResourceType type : ResourceType.values()) {
            assertThat(english).containsKey("resource.type." + type.name());
        }
        for (String kind : java.util.List.of("area", "goal", "step", "resource", "exercise", "result")) {
            assertThat(english).containsKey("delete.header." + kind);
        }
    }

    @Test
    void everyDomainErrorCodeAndFieldLabelIsTranslated() throws IOException {
        Set<String> codes = Set.of("required", "too-long", "negative", "too-large", "future-date", "not-positive",
                "out-of-range", "not-web-address", "score-above-max", "area-name-taken", "evidence-empty");
        codes.forEach(code -> assertThat(english).containsKey("error." + code));

        // Field labels are the literals handed to Validation.* and DomainValidationException in the domain.
        Pattern label = Pattern.compile("(?:requiredText|optionalText|required|amount)\\([^;]*?\"([A-Z][A-Za-z ]+)\"");
        Set<String> labels = new TreeSet<>();
        for (Path file : files(Path.of("..", "edutrack-domain", "src", "main", "java"), ".java")) {
            Matcher matcher = label.matcher(Files.readString(file));
            while (matcher.find()) {
                labels.add(matcher.group(1));
            }
        }
        assertThat(labels).isNotEmpty();
        for (String field : labels) {
            assertThat(english).as("field label %s", field).containsKey(Messages.fieldKey(field));
        }
    }

    // ---- helpers ----

    private static Set<String> arguments(Pattern argument, String text) {
        Matcher matcher = argument.matcher(text);
        Set<String> found = new TreeSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static Map<String, String> load(String resource) {
        Properties properties = new Properties();
        try (InputStream in = BundleConsistencyTest.class.getResourceAsStream(resource)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Map<String, String> bundle = new TreeMap<>();
        properties.forEach((key, value) -> bundle.put((String) key, (String) value));
        return bundle;
    }

    private static java.util.List<Path> files(Path root, String extension) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(path -> path.toString().endsWith(extension)).toList();
        }
    }
}

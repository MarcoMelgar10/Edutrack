package com.edutrack.infrastructure.template;

import com.edutrack.application.template.GoalTemplate;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTemplateCatalogTest {

    private static final List<String> SHIPPED = List.of("language-exam", "tech-certification",
            "new-programming-language", "instrument-practice", "university-course", "career-change", "sport-skill");

    @TempDir
    Path folder;

    private JsonTemplateCatalog catalog() {
        return new JsonTemplateCatalog(folder.resolve("user"));
    }

    @Test
    void shipsTheSameSevenTemplatesInEnglishAndSpanish() {
        List<GoalTemplate> english = catalog().builtIn(Locale.ENGLISH);
        List<GoalTemplate> spanish = catalog().builtIn(Locale.forLanguageTag("es-BO"));

        assertThat(english).extracting(GoalTemplate::id).containsExactlyInAnyOrderElementsOf(SHIPPED);
        assertThat(spanish).extracting(GoalTemplate::id).containsExactlyInAnyOrderElementsOf(SHIPPED);
        for (GoalTemplate template : spanish) {
            GoalTemplate counterpart = english.stream().filter(t -> t.id().equals(template.id())).findFirst().orElseThrow();
            assertThat(template.name()).isNotEqualTo(counterpart.name());
            assertThat(template.steps()).hasSameSizeAs(counterpart.steps());
            assertThat(template.competencies()).hasSameSizeAs(counterpart.competencies());
            assertThat(template.measures()).hasSameSizeAs(counterpart.measures());
            assertThat(template.routines()).hasSameSizeAs(counterpart.routines());
            assertThat(template.cards()).isNotEmpty();
        }
    }

    @Test
    void everyShippedTemplateIsValidAndUseful() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("es"))) {
            for (GoalTemplate template : catalog().builtIn(locale)) {
                template.validate();
                assertThat(template.steps()).as(template.id()).hasSizeBetween(6, 12);
                assertThat(template.competencies()).as(template.id()).hasSizeBetween(3, 6);
                assertThat(template.measures()).as(template.id()).isNotEmpty();
                assertThat(template.routines()).as(template.id()).isNotEmpty();
                assertThat(template.steps()).as(template.id()).allSatisfy(step -> assertThat(step.hours()).isNotNull());
            }
        }
    }

    @Test
    void aLanguageWithoutTemplatesFallsBackToEnglish() {
        assertThat(catalog().builtIn(Locale.JAPANESE)).extracting(GoalTemplate::id)
                .containsExactlyInAnyOrderElementsOf(SHIPPED);
        assertThat(catalog().builtIn(null)).hasSize(7);
    }

    @Test
    void theInstrumentTemplateHasNoDeadlineButAWeeklyTarget() {
        GoalTemplate instrument = catalog().builtIn(Locale.ENGLISH).stream()
                .filter(template -> template.id().equals("instrument-practice")).findFirst().orElseThrow();

        assertThat(instrument.durationWeeks()).isNull();
        assertThat(instrument.weeklyTargetMinutes()).isPositive();
    }

    @Test
    void userTemplatesAreSavedListedAndDeleted() {
        JsonTemplateCatalog catalog = catalog();
        GoalTemplate mine = catalog.builtIn(Locale.ENGLISH).getFirst().withIdAndName("my-plan", "My plan");

        assertThat(catalog.userTemplates()).isEmpty();
        catalog.saveUserTemplate(mine);

        assertThat(catalog.userTemplates()).containsExactly(mine);
        catalog.deleteUserTemplate("my-plan");
        catalog.deleteUserTemplate("my-plan"); // a second delete is ignored
        assertThat(catalog.userTemplates()).isEmpty();
    }

    @Test
    void aTemplateSurvivesAWriteAndReadOfAFile() {
        JsonTemplateCatalog catalog = catalog();
        for (GoalTemplate template : catalog.builtIn(Locale.forLanguageTag("es"))) {
            Path file = folder.resolve(template.id() + ".json");

            catalog.writeFile(template, file);

            assertThat(catalog.readFile(file)).isEqualTo(template);
        }
    }

    @Test
    void filesThatAreNotTemplatesAreRefused() throws IOException {
        Path notJson = Files.writeString(folder.resolve("a.json"), "not json at all");
        Path noSteps = Files.writeString(folder.resolve("b.json"), "{\"id\":\"x\",\"name\":\"X\",\"steps\":[]}");
        Path badId = Files.writeString(folder.resolve("c.json"),
                "{\"id\":\"../evil\",\"name\":\"X\",\"steps\":[{\"title\":\"One\"}]}");

        for (Path file : List.of(notJson, noSteps, badId)) {
            assertThatThrownBy(() -> catalog().readFile(file).validate())
                    .as(file.getFileName().toString())
                    .isInstanceOf(DomainValidationException.class);
        }
    }
}

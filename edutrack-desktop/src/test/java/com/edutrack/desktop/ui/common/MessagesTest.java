package com.edutrack.desktop.ui.common;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessagesTest {

    private final Messages messages = new Messages();

    @AfterEach
    void backToEnglish() {
        I18n.use(Language.ENGLISH);
    }

    @Test
    void aCodedRuleReadsItsEnglishTextByDefault() {
        DomainValidationException rule = new DomainValidationException("required", "Title is required.", "Title");

        assertThat(messages.error(rule)).isEqualTo("Title is required.");
    }

    @Test
    void theCodeAndTheFieldLabelAreTranslated() {
        I18n.use(Language.SPANISH);

        assertThat(messages.error(new DomainValidationException("required", "Title is required.", "Title")))
                .isEqualTo("El campo «Título» es obligatorio.");
        assertThat(messages.error(new DomainValidationException("negative", "Estimated hours cannot be negative.",
                "Estimated hours"))).isEqualTo("El campo «Horas estimadas» no puede ser negativo.");
    }

    @Test
    void numbersAreInsertedWithoutGrouping() {
        DomainValidationException rule = new DomainValidationException("too-long",
                "Notes must be at most 20000 characters.", "Notes", 20000);

        assertThat(messages.error(rule)).isEqualTo("Notes must be at most 20000 characters.");
        I18n.use(Language.SPANISH);
        assertThat(messages.error(rule)).isEqualTo("El campo «Notas» admite como máximo 20000 caracteres.");
    }

    @Test
    void decimalArgumentsFollowTheLanguage() {
        DomainValidationException rule = new DomainValidationException("too-large",
                "Estimated hours must be at most 1000.5.", "Estimated hours", new BigDecimal("1000.5"));

        assertThat(messages.error(rule)).isEqualTo("Estimated hours must be at most 1000.5.");
        I18n.use(Language.SPANISH);
        assertThat(messages.error(rule)).isEqualTo("El campo «Horas estimadas» no puede superar 1000,5.");
    }

    @Test
    void rulesWithoutArgumentsAreTranslatedToo() {
        I18n.use(Language.SPANISH);

        assertThat(messages.error(new DomainValidationException("score-above-max", "Score cannot be higher than the maximum score.")))
                .isEqualTo("La puntuación no puede ser mayor que la puntuación máxima.");
    }

    @Test
    void whatTheLearnerTypedIsNeverTranslatedAsAFieldLabel() {
        I18n.use(Language.SPANISH);

        // "Title" is also a field label, but here it is the name of an area.
        assertThat(messages.error(new DomainValidationException("area-name-taken",
                "An area named \"Title\" already exists.", "Title")))
                .isEqualTo("Ya existe un área llamada \"Title\".");
    }

    @Test
    void anUnknownCodeFallsBackToTheEnglishMessage() {
        DomainValidationException rule = new DomainValidationException("brand-new-rule", "Something new is wrong.", "Thing");

        assertThat(messages.error(rule)).isEqualTo("Something new is wrong.");
    }

    @Test
    void aRuleWithoutACodeShowsItsMessage() {
        assertThat(messages.error(new DomainValidationException("Plain message."))).isEqualTo("Plain message.");
    }

    @Test
    void anyOtherErrorShowsItsMessage() {
        assertThat(messages.error(new IllegalStateException("Disk full"))).isEqualTo("Disk full");
        assertThat(messages.error(new IllegalStateException())).isEqualTo("IllegalStateException");
    }

    @Test
    void theDomainsOwnMessagesMatchTheEnglishBundle() {
        // The English bundle is the domain's wording, so a change of either side shows up here.
        assertThatThrownBy(() -> Area.create(" ", null, AreaIcon.BOOK, 0, Instant.EPOCH))
                .isInstanceOfSatisfying(DomainValidationException.class,
                        rule -> assertThat(messages.error(rule)).isEqualTo(rule.getMessage()));
        assertThatThrownBy(() -> Area.create("x".repeat(500), null, AreaIcon.BOOK, 0, Instant.EPOCH))
                .isInstanceOfSatisfying(DomainValidationException.class,
                        rule -> assertThat(messages.error(rule)).isEqualTo(rule.getMessage()));
    }

    @Test
    void fieldKeysJoinTheWordsInCamelCase() {
        assertThat(Messages.fieldKey("Estimated hours")).isEqualTo("field.EstimatedHours");
        assertThat(Messages.fieldKey("Title")).isEqualTo("field.Title");
        assertThat(Messages.fieldKey("Maximum score")).isEqualTo("field.MaximumScore");
    }

    @Test
    void aMissingKeyShowsTheKeyInsteadOfFailing() {
        assertThat(messages.get("no.such.key")).isEqualTo("no.such.key");
    }

    @Test
    void textsWithArgumentsAreFormatted() {
        assertThat(messages.get("step.save.savedAt", "14:32")).isEqualTo("Saved · 14:32");
        assertThat(messages.plural("count.goals", 1)).isEqualTo("1 goal");
        assertThat(messages.plural("count.goals", 3)).isEqualTo("3 goals");
    }

    @Test
    void theLanguageIsEnglishUntilSomeoneSwitchesIt() {
        assertThat(messages.language()).isEqualTo(Language.ENGLISH);
        assertThat(messages.locale().getLanguage()).isEqualTo("en");
        I18n.use(Language.SPANISH);
        assertThat(messages.locale().getLanguage()).isEqualTo("es");
        assertThat(messages.bundle().getString("nav.today")).isEqualTo("Hoy");
    }

    @Test
    void theSavedChoiceWinsOverTheSystemLanguage() {
        assertThat(Language.resolve("ENGLISH", java.util.Locale.forLanguageTag("es-MX"))).isEqualTo(Language.ENGLISH);
        assertThat(Language.resolve("SPANISH", java.util.Locale.ENGLISH)).isEqualTo(Language.SPANISH);
        assertThat(Language.resolve(null, java.util.Locale.forLanguageTag("es-MX"))).isEqualTo(Language.SPANISH);
        assertThat(Language.resolve(null, java.util.Locale.FRANCE)).isEqualTo(Language.ENGLISH);
        assertThat(Language.resolve("garbage", java.util.Locale.forLanguageTag("es"))).isEqualTo(Language.SPANISH);
    }
}

package com.edutrack.desktop.ui.dialog;

import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.common.Language;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadlineConverterTest {

    private final DeadlineConverter converter = new DeadlineConverter();

    @Test
    void showsDatesAsDayMonthYear() {
        assertThat(converter.toString(LocalDate.of(2027, 3, 1))).isEqualTo("01 Mar 2027");
        assertThat(converter.toString(null)).isEmpty();
    }

    @Test
    void readsTheDisplayedForm() {
        assertThat(converter.fromString("01 Mar 2027")).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    void forgivesCaseSpacesAndAMissingLeadingZero() {
        assertThat(converter.fromString("  1 mar 2027 ")).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(converter.fromString("15 DEC 2026")).isEqualTo(LocalDate.of(2026, 12, 15));
    }

    @Test
    void readsIsoDates() {
        assertThat(converter.fromString("2027-03-01")).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    void blankMeansNoDeadline() {
        assertThat(converter.fromString("")).isNull();
        assertThat(converter.fromString("   ")).isNull();
        assertThat(converter.fromString(null)).isNull();
    }

    @Test
    void inSpanishDatesUseSpanishMonthNames() {
        I18n.use(Language.SPANISH);
        try {
            DeadlineConverter spanish = new DeadlineConverter();

            assertThat(spanish.toString(LocalDate.of(2027, 3, 1))).isEqualTo("01 mar 2027");
            assertThat(spanish.fromString("1 MAR 2027")).isEqualTo(LocalDate.of(2027, 3, 1));
            assertThat(spanish.fromString("2027-03-01")).isEqualTo(LocalDate.of(2027, 3, 1));
        } finally {
            I18n.use(Language.ENGLISH);
        }
    }

    @Test
    void rejectsTextThatIsNotADate() {
        assertThatThrownBy(() -> converter.fromString("tomorrow")).isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> converter.fromString("01/03/2027")).isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void rejectsDaysThatDoNotExist() {
        assertThatThrownBy(() -> converter.fromString("31 Feb 2027")).isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> converter.fromString("2027-02-30")).isInstanceOf(DateTimeParseException.class);
    }
}

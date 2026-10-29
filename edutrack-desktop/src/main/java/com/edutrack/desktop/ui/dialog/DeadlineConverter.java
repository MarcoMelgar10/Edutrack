package com.edutrack.desktop.ui.dialog;

import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.I18n;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Text form of a date field. Dates are shown as "01 Mar 2027" ("01 mar 2027" in Spanish); typing
 * "1 mar 2027" or the ISO form "2027-03-01" works too, and a blank field means no date.
 */
public final class DeadlineConverter extends StringConverter<LocalDate> {

    private final DateTimeFormatter typed = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(I18n.get("format.typedDate"))
            .toFormatter(I18n.locale())
            .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public String toString(LocalDate date) {
        return Formats.date(date);
    }

    /** @throws DateTimeParseException if the text is neither of the accepted forms */
    @Override
    public LocalDate fromString(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String entered = text.strip();
        try {
            return LocalDate.parse(entered, typed);
        } catch (DateTimeParseException notTheDisplayForm) {
            return LocalDate.parse(entered); // ISO yyyy-MM-dd; throws if this is not a date either
        }
    }
}

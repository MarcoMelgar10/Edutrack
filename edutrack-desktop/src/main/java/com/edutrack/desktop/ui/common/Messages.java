package com.edutrack.desktop.ui.common;

import com.edutrack.domain.shared.DomainValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Translated text for controllers and view models. Everything a person reads comes from the
 * {@code i18n/messages*.properties} bundles; this class and {@link I18n} are the only way in.
 */
@Component
public class Messages {

    /** Codes whose arguments are things the learner typed, never field labels. */
    private static final Set<String> USER_TEXT_ARGS = Set.of("area-name-taken");

    public String get(String key, Object... args) {
        return I18n.get(key, args);
    }

    /** "1 step" / "3 steps" from {@code key.one} / {@code key.other}; {0} is the count. */
    public String plural(String key, long count, Object... more) {
        return I18n.plural(key, count, more);
    }

    public ResourceBundle bundle() {
        return I18n.bundle();
    }

    public Locale locale() {
        return I18n.locale();
    }

    public Language language() {
        return I18n.language();
    }

    /**
     * What to show for a failed action. A domain rule with a code reads {@code error.<code>} filled with
     * its arguments, field labels translated through {@code field.<Label>}; anything else shows its own message.
     */
    public String error(Throwable failure) {
        if (failure instanceof DomainValidationException rule && rule.code() != null
                && I18n.has("error." + rule.code())) {
            boolean translateArgs = !USER_TEXT_ARGS.contains(rule.code());
            Object[] args = rule.args().stream()
                    .map(arg -> translateArgs ? translateArg(arg) : arg)
                    .map(Messages::plain)
                    .toArray();
            return I18n.get("error." + rule.code(), args);
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static Object translateArg(Object arg) {
        if (arg instanceof String label) {
            String key = fieldKey(label);
            if (I18n.has(key)) {
                return I18n.get(key);
            }
        }
        return arg;
    }

    /** "Estimated hours" → "field.EstimatedHours". */
    static String fieldKey(String label) {
        StringBuilder key = new StringBuilder("field.");
        for (String word : label.strip().split("\\s+")) {
            if (!word.isEmpty()) {
                key.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return key.toString();
    }

    /** Numbers go in as plain text: "2000", not the grouped "2,000" a MessageFormat would make of them. */
    private static Object plain(Object arg) {
        return switch (arg) {
            case BigDecimal decimal -> Formats.number(decimal);
            case Number number -> number.toString();
            default -> arg;
        };
    }
}

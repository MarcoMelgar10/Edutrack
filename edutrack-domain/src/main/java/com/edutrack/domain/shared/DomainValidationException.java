package com.edutrack.domain.shared;

import java.util.List;

/**
 * A business rule was violated. The message is written in English for the person using the app
 * (e.g. "Title is required.") so it can be shown as-is.
 *
 * <p>For translation, most rules also carry a stable {@link #code()} (e.g. {@code "required"})
 * and the values that fill it ({@link #args()}, e.g. the field name {@code "Title"}). The UI looks
 * up {@code error.<code>} in its bundle, translates any argument that has a {@code field.<arg>}
 * entry and falls back to the English message when a code is unknown.
 */
public class DomainValidationException extends RuntimeException {

    private final String code;
    private final List<Object> args;

    public DomainValidationException(String message) {
        this(null, message);
    }

    public DomainValidationException(String code, String message, Object... args) {
        super(message);
        this.code = code;
        this.args = List.of(args);
    }

    /** The stable rule code, or {@code null} when only the English message exists. */
    public String code() {
        return code;
    }

    /** The values the message is built from, in the order the translations use them ({0}, {1}, …). */
    public List<Object> args() {
        return args;
    }
}

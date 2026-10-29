package com.edutrack.domain.shared;

/** Text rules shared by all aggregates. Values are stripped; blank optional text becomes {@code null}. */
public final class Validation {

    public static final int DESCRIPTION_MAX_LENGTH = 2000;

    private Validation() {
    }

    public static String requiredText(String value, String field, int maxLength) {
        String text = optionalText(value, field, maxLength);
        if (text == null) {
            throw new DomainValidationException("required", field + " is required.", field);
        }
        return text;
    }

    public static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        if (text.length() > maxLength) {
            throw new DomainValidationException("too-long", field + " must be at most " + maxLength + " characters.", field, maxLength);
        }
        return text;
    }

    public static <T> T required(T value, String field) {
        if (value == null) {
            throw new DomainValidationException("required", field + " is required.", field);
        }
        return value;
    }

    public static int position(int value) {
        if (value < 0) {
            throw new DomainValidationException("negative", "Position cannot be negative.", "Position");
        }
        return value;
    }
}

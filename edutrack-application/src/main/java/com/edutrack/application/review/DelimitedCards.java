package com.edutrack.application.review;

import java.util.Optional;

/**
 * Splits one line of pasted text into the front and back of a card. The separator is the first
 * tab, else the first {@code ;}, else the first {@code ,}. A front wrapped in double quotes may
 * itself contain separators ({@code "to be, or not";ser o no ser}); a doubled quote inside quotes
 * stands for one quote. Surrounding quotes are removed from both sides.
 */
final class DelimitedCards {

    record Sides(String front, String back) {
    }

    private DelimitedCards() {
    }

    /** Empty when the line has no separator, or one of the two sides is blank. */
    static Optional<Sides> split(String line) {
        String text = line.strip();
        Sides sides = splitQuotedFront(text);
        if (sides == null) {
            sides = splitAtFirstSeparator(text);
        }
        if (sides == null) {
            return Optional.empty();
        }
        String front = unquote(sides.front());
        String back = unquote(sides.back());
        return front.isEmpty() || back.isEmpty() ? Optional.empty() : Optional.of(new Sides(front, back));
    }

    private static Sides splitQuotedFront(String text) {
        if (!text.startsWith("\"")) {
            return null;
        }
        int close = closingQuote(text);
        if (close < 0) {
            return null;
        }
        int next = close + 1;
        while (next < text.length() && text.charAt(next) == ' ') {
            next++;
        }
        if (next < text.length() && isSeparator(text.charAt(next))) {
            return new Sides(text.substring(0, close + 1), text.substring(next + 1));
        }
        return null;
    }

    /** The index of the quote that ends the quoted text starting at 0, skipping doubled quotes; -1 when it never ends. */
    private static int closingQuote(String text) {
        for (int i = 1; i < text.length(); i++) {
            if (text.charAt(i) == '"') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    i++;
                } else {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Sides splitAtFirstSeparator(String text) {
        for (char separator : new char[]{'\t', ';', ','}) {
            int at = text.indexOf(separator);
            if (at >= 0) {
                return new Sides(text.substring(0, at), text.substring(at + 1));
            }
        }
        return null;
    }

    private static boolean isSeparator(char c) {
        return c == '\t' || c == ';' || c == ',';
    }

    private static String unquote(String side) {
        String text = side.strip();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).replace("\"\"", "\"").strip();
        }
        return text;
    }
}

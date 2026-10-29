package com.edutrack.desktop.ui.cards;

import com.edutrack.application.review.CardView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.I18n;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * How card intervals and due dates read: "today", "1 d", "6 d", "2 mo", "1.5 y" for a span of days and
 * "new", "due today", "in 6 d", "overdue 3 d" for a card. Texts come from the bundle.
 */
public final class CardFormats {

    private static final int DAYS_PER_MONTH = 30;
    private static final int DAYS_PER_YEAR = 365;

    private CardFormats() {
    }

    /** An interval in days: 0 is "today"; from 30 days on months, from a year on years. */
    public static String interval(int days) {
        if (days <= 0) {
            return I18n.get("review.interval.today");
        }
        if (days < DAYS_PER_MONTH) {
            return I18n.get("review.interval.days", days);
        }
        if (days < DAYS_PER_YEAR) {
            return I18n.get("review.interval.months", Math.max(1, Math.round(days / (float) DAYS_PER_MONTH)));
        }
        BigDecimal years = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(DAYS_PER_YEAR), 1, RoundingMode.HALF_UP);
        return I18n.get("review.interval.years", Formats.number(years));
    }

    /** When the card is next due, in the words of the card list. */
    public static String due(CardView card, LocalDate today) {
        if (card.isNew()) {
            return I18n.get("cards.due.new");
        }
        long days = ChronoUnit.DAYS.between(today, card.nextDue());
        if (days == 0) {
            return I18n.get("cards.due.today");
        }
        return days > 0
                ? I18n.get("cards.due.in", interval((int) days))
                : I18n.get("cards.due.overdue", interval((int) -days));
    }

    /**
     * The first line of Markdown as plain text for a list row: heading, quote and list markers and bold,
     * strikethrough and code marks are dropped. Empty for blank text.
     */
    public static String firstLine(String markdown) {
        if (markdown == null) {
            return "";
        }
        for (String line : markdown.split("\\R")) {
            String plain = line.strip()
                    .replaceFirst("^(?:[#>]+|[-*+]\\s|\\d+[.)]\\s)+\\s*", "")
                    .replaceAll("\\*\\*|__|~~|`", "")
                    .strip();
            if (!plain.isEmpty()) {
                return plain;
            }
        }
        return "";
    }
}

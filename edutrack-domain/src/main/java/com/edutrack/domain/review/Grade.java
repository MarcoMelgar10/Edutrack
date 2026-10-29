package com.edutrack.domain.review;

/**
 * How well a card was recalled, as chosen with the four review buttons. Each grade maps to an
 * SM-2 quality (0–5); below 3 counts as a lapse.
 */
public enum Grade {
    AGAIN(1),
    HARD(3),
    GOOD(4),
    EASY(5);

    private final int quality;

    Grade(int quality) {
        this.quality = quality;
    }

    /** The SM-2 response quality. */
    public int quality() {
        return quality;
    }

    public boolean isLapse() {
        return quality < 3;
    }
}

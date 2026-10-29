package com.edutrack.domain.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A completion percentage between 0 and 100, kept with two decimals.
 * The UI shows {@link #rounded()} and feeds {@link #fraction()} to progress bars.
 */
public record Progress(BigDecimal percent) implements Comparable<Progress> {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static final Progress ZERO = new Progress(BigDecimal.ZERO);
    public static final Progress COMPLETE = new Progress(HUNDRED);

    public Progress {
        if (percent == null || percent.signum() < 0 || percent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Progress must be between 0 and 100, was " + percent);
        }
        percent = percent.setScale(2, RoundingMode.HALF_UP);
    }

    /** {@code part / whole} as a percentage; an empty whole means nothing to do yet, so 0%. */
    public static Progress ofRatio(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return ZERO;
        }
        return new Progress(part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP));
    }

    /** Whole percent for display, rounded half-up (26.32 → 26). */
    public int rounded() {
        return percent.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** 0.0–1.0 for JavaFX progress bars. */
    public double fraction() {
        return percent.doubleValue() / 100.0;
    }

    public boolean isComplete() {
        return percent.compareTo(HUNDRED) == 0;
    }

    @Override
    public int compareTo(Progress other) {
        return percent.compareTo(other.percent);
    }

    @Override
    public String toString() {
        return rounded() + "%";
    }
}

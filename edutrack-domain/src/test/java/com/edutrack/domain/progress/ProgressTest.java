package com.edutrack.domain.progress;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgressTest {

    @Test
    void nullPercentIsRejected() {
        assertThatThrownBy(() -> new Progress(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativePercentIsRejected() {
        assertThatThrownBy(() -> new Progress(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 100");
    }

    @Test
    void percentAbove100IsRejected() {
        assertThatThrownBy(() -> new Progress(new BigDecimal("100.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 100");
    }

    @Test
    void zeroAndOneHundredAreAccepted() {
        assertThat(new Progress(BigDecimal.ZERO)).isEqualTo(Progress.ZERO);
        assertThat(new Progress(BigDecimal.valueOf(100))).isEqualTo(Progress.COMPLETE);
    }

    @Test
    void percentIsRoundedHalfUpToTwoDecimals() {
        assertThat(new Progress(new BigDecimal("12.345")).percent()).isEqualTo(new BigDecimal("12.35"));
    }

    @Test
    void wholePercentGetsTwoDecimals() {
        assertThat(new Progress(BigDecimal.valueOf(5)).percent()).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void ratioWithAnEmptyWholeIsZero() {
        assertThat(Progress.ofRatio(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualTo(Progress.ZERO);
    }

    @Test
    void doneHoursOverTotalHoursGivesThePercentage() {
        Progress progress = Progress.ofRatio(BigDecimal.TEN, BigDecimal.valueOf(38));

        assertThat(progress.percent()).isEqualTo(new BigDecimal("26.32"));
        assertThat(progress.rounded()).isEqualTo(26);
    }

    @Test
    void roundedGoesHalfUp() {
        assertThat(new Progress(new BigDecimal("26.50")).rounded()).isEqualTo(27);
    }

    @Test
    void fractionIsThePercentOverOneHundred() {
        assertThat(new Progress(BigDecimal.valueOf(25)).fraction()).isEqualTo(0.25);
    }

    @Test
    void onlyOneHundredPercentIsComplete() {
        assertThat(Progress.COMPLETE.isComplete()).isTrue();
        assertThat(new Progress(new BigDecimal("99.99")).isComplete()).isFalse();
        assertThat(Progress.ZERO.isComplete()).isFalse();
    }

    @Test
    void progressesCompareByPercent() {
        assertThat(new Progress(BigDecimal.TEN)).isLessThan(new Progress(BigDecimal.valueOf(20)));
    }

    @Test
    void toStringShowsTheRoundedPercent() {
        assertThat(Progress.ofRatio(BigDecimal.TEN, BigDecimal.valueOf(38))).hasToString("26%");
    }
}

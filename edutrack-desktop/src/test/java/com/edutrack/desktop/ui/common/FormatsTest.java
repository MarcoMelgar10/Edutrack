package com.edutrack.desktop.ui.common;

import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.record.ResourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FormatsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    @Test
    void deadlineCountsDaysLeft() {
        assertThat(Formats.deadline(LocalDate.of(2027, 3, 1), TODAY))
                .isEqualTo("Deadline 01 Mar 2027 · 125 days left");
    }

    @Test
    void deadlineUsesTheSingularForOneDay() {
        assertThat(Formats.deadline(TODAY.plusDays(1), TODAY))
                .isEqualTo("Deadline 28 Oct 2026 · 1 day left");
    }

    @Test
    void deadlineSaysDueTodayOnTheDay() {
        assertThat(Formats.deadline(TODAY, TODAY)).isEqualTo("Deadline 27 Oct 2026 · due today");
    }

    @Test
    void deadlineCountsOverdueDays() {
        assertThat(Formats.deadline(TODAY.minusDays(1), TODAY)).isEqualTo("Deadline 26 Oct 2026 · 1 day overdue");
        assertThat(Formats.deadline(TODAY.minusDays(3), TODAY)).isEqualTo("Deadline 24 Oct 2026 · 3 days overdue");
    }

    @Test
    void missingDeadlineReadsNoDeadline() {
        assertThat(Formats.deadline(null, TODAY)).isEqualTo("No deadline");
    }

    @Test
    void hoursDropTrailingZeros() {
        assertThat(Formats.hours(new BigDecimal("10.0"))).isEqualTo("10h");
        assertThat(Formats.hours(new BigDecimal("2.50"))).isEqualTo("2.5h");
        assertThat(Formats.hours(new BigDecimal("100"))).isEqualTo("100h");
    }

    @Test
    void missingHoursAreEmpty() {
        assertThat(Formats.hours(null)).isEmpty();
    }

    @Test
    void effortShowsDoneOverTotal() {
        assertThat(Formats.effort(new BigDecimal("10.0"), new BigDecimal("38.0"), 0, 1, 3)).isEqualTo("10 / 38 h");
        assertThat(Formats.effort(BigDecimal.ZERO, new BigDecimal("2.5"), 0, 0, 1)).isEqualTo("0 / 2.5 h");
        assertThat(Formats.effort(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0)).isEqualTo("0 / 0 h");
    }

    @Test
    void effortNamesStepsWithoutEstimateInsteadOfInventingHours() {
        assertThat(Formats.effort(new BigDecimal("33"), new BigDecimal("194.5"), 1, 3, 9))
                .isEqualTo("33 / 194.5 h · 1 step without estimate");
        assertThat(Formats.effort(new BigDecimal("2"), new BigDecimal("8"), 2, 1, 4))
                .isEqualTo("2 / 8 h · 2 steps without estimate");
        assertThat(Formats.effort(BigDecimal.ZERO, BigDecimal.ZERO, 4, 1, 4)).isEqualTo("1 of 4 steps");
    }

    @Test
    void monthIsCompact() {
        assertThat(Formats.month(LocalDate.of(2027, 3, 1))).isEqualTo("Mar 2027");
    }

    @Test
    void missingMonthReadsNoDeadline() {
        assertThat(Formats.month(null)).isEqualTo("No deadline");
    }

    @Test
    void dateIsDayMonthYearInEnglish() {
        assertThat(Formats.date(LocalDate.of(2027, 3, 1))).isEqualTo("01 Mar 2027");
        assertThat(Formats.date((LocalDate) null)).isEmpty();
    }

    @Test
    void instantsAreShownInTheGivenZone() {
        Instant lateEvening = Instant.parse("2026-10-27T23:30:00Z");

        assertThat(Formats.date(lateEvening, ZoneOffset.UTC)).isEqualTo("27 Oct 2026");
        assertThat(Formats.date(lateEvening, ZoneOffset.ofHours(2))).isEqualTo("28 Oct 2026");
        assertThat(Formats.date((Instant) null, ZoneOffset.UTC)).isEmpty();
    }

    @Test
    void linkLosesSchemeWwwAndTrailingSlash() {
        assertThat(Formats.link("https://www.bbc.co.uk/learningenglish/")).isEqualTo("bbc.co.uk/learningenglish");
        assertThat(Formats.link("http://example.com")).isEqualTo("example.com");
        assertThat(Formats.link("https://example.com/a/b?c=1")).isEqualTo("example.com/a/b?c=1");
    }

    @Test
    void missingLinkStaysMissing() {
        assertThat(Formats.link(null)).isNull();
    }

    @Test
    void percentRoundsHalfUp() {
        assertThat(Formats.percent(new Progress(new BigDecimal("26.32")))).isEqualTo("26%");
        assertThat(Formats.percent(new Progress(new BigDecimal("26.50")))).isEqualTo("27%");
        assertThat(Formats.percent(Progress.ZERO)).isEqualTo("0%");
        assertThat(Formats.percent(Progress.COMPLETE)).isEqualTo("100%");
    }

    @Test
    void recordSummaryListsOnlyWhatIsRecorded() {
        assertThat(Formats.recordSummary(RecordSummary.EMPTY)).isEmpty();
        assertThat(Formats.recordSummary(new RecordSummary(true, 1, 2, 5, 3)))
                .isEqualTo("Notes · 1 resource · Exercises 2/5 · 3 results");
        assertThat(Formats.recordSummary(new RecordSummary(false, 3, 0, 0, 1))).isEqualTo("3 resources · 1 result");
    }

    @Test
    void scoreShowsTheMaximumAndPercentageWhenKnown() {
        assertThat(Formats.score(new BigDecimal("6.50"), new BigDecimal("9"), new Progress(new BigDecimal("72.22"))))
                .isEqualTo("6.5 / 9 · 72%");
        assertThat(Formats.score(new BigDecimal("85"), null, null)).isEqualTo("85");
    }

    @Test
    void resourceTypesReadAsWords() {
        assertThat(Formats.resourceType(ResourceType.COURSE)).isEqualTo("Course");
        assertThat(Formats.resourceType(ResourceType.DOCUMENTATION)).isEqualTo("Documentation");
        assertThat(Formats.resourceType(ResourceType.OTHER)).isEqualTo("Other");
        assertThat(Formats.resourceType(ResourceType.PODCAST)).isEqualTo("Podcast");
        assertThat(Formats.resourceType(ResourceType.PERSON)).isEqualTo("Mentor or teacher");
    }

    @Test
    void pluralPicksTheOneOrOtherForm() {
        assertThat(I18n.plural("count.resources", 1)).isEqualTo("1 resource");
        assertThat(I18n.plural("count.resources", 0)).isEqualTo("0 resources");
    }

    @Nested
    class InSpanish {

        @BeforeEach
        void speakSpanish() {
            I18n.use(Language.SPANISH);
        }

        @AfterEach
        void backToEnglish() {
            I18n.use(Language.ENGLISH);
        }

        @Test
        void datesUseSpanishMonthNames() {
            assertThat(Formats.date(LocalDate.of(2026, 10, 12))).isEqualTo("12 oct 2026");
            assertThat(Formats.month(LocalDate.of(2027, 3, 1))).isEqualTo("mar 2027");
            assertThat(Formats.date(Instant.parse("2026-10-27T23:30:00Z"), ZoneOffset.UTC)).isEqualTo("27 oct 2026");
        }

        @Test
        void theLongDateStartsWithACapitalLetter() {
            assertThat(Formats.longDate(LocalDate.of(2026, 10, 27))).isEqualTo("Martes, 27 de octubre de 2026");
        }

        @Test
        void decimalsUseAComma() {
            assertThat(Formats.hours(new BigDecimal("2.50"))).isEqualTo("2,5 h");
            assertThat(Formats.hours(new BigDecimal("10.0"))).isEqualTo("10 h");
            assertThat(Formats.score(new BigDecimal("6.50"), new BigDecimal("9"), new Progress(new BigDecimal("72.22"))))
                    .isEqualTo("6,5 / 9 · 72%");
            assertThat(Formats.effort(new BigDecimal("33"), new BigDecimal("194.5"), 1, 3, 9))
                    .isEqualTo("33 / 194,5 h · 1 paso sin estimación");
        }

        @Test
        void deadlineAndPluralsFollowSpanishRules() {
            assertThat(Formats.deadline(LocalDate.of(2027, 3, 1), TODAY))
                    .isEqualTo("Fecha límite 01 mar 2027 · quedan 125 días");
            assertThat(Formats.deadline(TODAY.plusDays(1), TODAY)).isEqualTo("Fecha límite 28 oct 2026 · queda 1 día");
            assertThat(Formats.deadline(TODAY, TODAY)).isEqualTo("Fecha límite 27 oct 2026 · vence hoy");
            assertThat(Formats.deadline(TODAY.minusDays(3), TODAY))
                    .isEqualTo("Fecha límite 24 oct 2026 · 3 días de retraso");
            assertThat(Formats.deadline(null, TODAY)).isEqualTo("Sin fecha límite");
        }

        @Test
        void statusesTypesAndRecordSummaryAreTranslated() {
            assertThat(Formats.status(StepStatus.IN_PROGRESS)).isEqualTo("En curso");
            assertThat(Formats.resourceType(ResourceType.ARTICLE)).isEqualTo("Artículo");
            assertThat(Formats.recordSummary(new RecordSummary(true, 1, 2, 5, 3)))
                    .isEqualTo("Notas · 1 recurso · Ejercicios 2/5 · 3 resultados");
        }
    }

    @Test
    void statusesReadAsPlainWords() {
        assertThat(Formats.status(StepStatus.NOT_STARTED)).isEqualTo("Not started");
        assertThat(Formats.status(StepStatus.IN_PROGRESS)).isEqualTo("In progress");
        assertThat(Formats.status(StepStatus.DONE)).isEqualTo("Done");
    }
}

package com.edutrack.application.export;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.StepView;
import com.edutrack.application.measure.MeasureService;
import com.edutrack.application.measure.MeasureView;
import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.record.RecordView;
import com.edutrack.application.record.RecordView.ResultView;
import com.edutrack.application.review.CardView;
import com.edutrack.application.review.ReviewService;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.record.RecordOwner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns a goal into text the learner can keep or open elsewhere: its notes as Markdown, its
 * cards and results as CSV (RFC 4180: comma separated, CRLF line ends, fields quoted when they need
 * it). The text is English; saving it to a file is the caller's job.
 */
public class ExportService {

    private static final String CSV_NEWLINE = "\r\n";
    private static final Pattern LEADING_BLANK_LINES = Pattern.compile("^(?:[ \\t]*\\R)+");

    private final GoalService goals;
    private final LearningRecordService records;
    private final ReviewService reviews;
    private final MeasureService measures;

    public ExportService(GoalService goals, LearningRecordService records, ReviewService reviews,
                         MeasureService measures) {
        this.goals = goals;
        this.records = records;
        this.reviews = reviews;
        this.measures = measures;
    }

    /**
     * The goal as one Markdown document: title and description, then each step with its status, notes
     * (verbatim), resources, exercises and results, then the goal's own results. Empty parts are left out.
     */
    public String goalNotesMarkdown(GoalId goalId) {
        GoalDetails goal = goals.getGoal(goalId);
        StringBuilder markdown = new StringBuilder("# ").append(goal.title()).append("\n");
        if (goal.description() != null) {
            markdown.append('\n').append(goal.description()).append('\n');
        }
        for (StepView step : goal.steps()) {
            markdown.append("\n## ").append(step.number()).append(". ").append(step.title()).append("\n\n")
                    .append(statusLine(step)).append('\n');
            if (step.description() != null) {
                markdown.append('\n').append(step.description()).append('\n');
            }
            RecordView record = records.open(RecordOwner.ofStep(step.id()));
            if (!record.notes().isBlank()) {
                markdown.append('\n').append(notesAsWritten(record.notes())).append('\n');
            }
            appendResources(markdown, record);
            appendExercises(markdown, record);
            appendResults(markdown, "Results", record);
        }
        RecordView finalResults = records.open(RecordOwner.ofGoal(goalId));
        if (!finalResults.results().isEmpty()) {
            markdown.append("\n## Goal results\n");
            appendResultList(markdown, finalResults);
        }
        return markdown.toString();
    }

    /** The goal's cards, one per row: {@code front,back,tags,reversible}; tags are separated by {@code ;}. */
    public String cardsCsv(GoalId goalId) {
        StringBuilder csv = new StringBuilder("front,back,tags,reversible").append(CSV_NEWLINE);
        for (CardView card : reviews.forGoal(goalId)) {
            csv.append(row(card.front(), card.back(), String.join(";", card.tags()), String.valueOf(card.reversible())));
        }
        return csv.toString();
    }

    /**
     * Every result of the goal and its steps, oldest first: {@code date,title,part,measure,score,max,step,note}.
     * {@code measure} and {@code step} are names, empty when the result has none.
     */
    public String resultsCsv(GoalId goalId) {
        GoalDetails goal = goals.getGoal(goalId);
        Map<MeasureId, String> measureNames = new HashMap<>();
        for (MeasureView measure : measures.forGoal(goalId)) {
            measureNames.put(measure.id(), measure.name());
        }
        List<ResultRow> results = new ArrayList<>();
        for (StepView step : goal.steps()) {
            records.open(RecordOwner.ofStep(step.id())).results()
                    .forEach(result -> results.add(new ResultRow(result, step.title())));
        }
        records.open(RecordOwner.ofGoal(goalId)).results().forEach(result -> results.add(new ResultRow(result, null)));
        results.sort(Comparator.comparing(row -> row.result().takenOn()));

        StringBuilder csv = new StringBuilder("date,title,part,measure,score,max,step,note").append(CSV_NEWLINE);
        for (ResultRow row : results) {
            ResultView result = row.result();
            csv.append(row(result.takenOn().toString(), result.title(), result.part(),
                    result.measureId() == null ? null : measureNames.get(result.measureId()),
                    number(result.score()), number(result.maxScore()), row.stepTitle(), result.note()));
        }
        return csv.toString();
    }

    private record ResultRow(ResultView result, String stepTitle) {
    }

    // ---------- markdown parts ----------

    private static String statusLine(StepView step) {
        String status = switch (step.status()) {
            case NOT_STARTED -> "Not started";
            case IN_PROGRESS -> "In progress";
            case DONE -> "Done";
        };
        return step.estimatedHours() == null ? status : status + " · " + number(step.estimatedHours()) + " h";
    }

    /** The notes exactly as typed, only without the blank lines around them (leading spaces can be a code block). */
    private static String notesAsWritten(String notes) {
        return LEADING_BLANK_LINES.matcher(notes).replaceFirst("").stripTrailing();
    }

    private static void appendResources(StringBuilder markdown, RecordView record) {
        if (record.resources().isEmpty()) {
            return;
        }
        markdown.append("\n### Resources\n\n");
        record.resources().forEach(resource -> markdown.append("- ")
                .append(resource.url() == null ? resource.title() : "[" + resource.title() + "](" + resource.url() + ")")
                .append('\n'));
    }

    private static void appendExercises(StringBuilder markdown, RecordView record) {
        if (record.exercises().isEmpty()) {
            return;
        }
        markdown.append("\n### Exercises\n\n");
        record.exercises().forEach(exercise -> markdown.append(exercise.done() ? "- [x] " : "- [ ] ")
                .append(exercise.title()).append('\n'));
    }

    private static void appendResults(StringBuilder markdown, String heading, RecordView record) {
        if (record.results().isEmpty()) {
            return;
        }
        markdown.append("\n### ").append(heading).append('\n');
        appendResultList(markdown, record);
    }

    private static void appendResultList(StringBuilder markdown, RecordView record) {
        markdown.append('\n');
        for (ResultView result : record.results()) {
            markdown.append("- ").append(result.takenOn()).append(" · ").append(result.title());
            if (result.part() != null) {
                markdown.append(" (").append(result.part()).append(')');
            }
            markdown.append(": ").append(number(result.score()));
            if (result.maxScore() != null) {
                markdown.append(" / ").append(number(result.maxScore()));
            }
            if (result.note() != null) {
                markdown.append(" — ").append(result.note());
            }
            markdown.append('\n');
        }
    }

    // ---------- csv ----------

    private static String row(String... fields) {
        return Arrays.stream(fields).map(ExportService::csvField).collect(Collectors.joining(","))
                + CSV_NEWLINE;
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        return needsQuotes ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    /** 6.50 → "6.5", 9.00 → "9": no trailing zeros, never an exponent. */
    private static String number(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }
}

package com.edutrack.desktop.ui.skills;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.measure.MeasureService;
import com.edutrack.application.measure.MeasureView;
import com.edutrack.application.skill.CompetencyService;
import com.edutrack.application.skill.CompetencyView;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.measure.MeasureSeries;
import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyId;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Subscription;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * State of the Skills tab of a goal: its competencies (level against target) and its measures (latest
 * value against target, with the points for a small trend line). The goal's percentage still comes from
 * its roadmap steps only; skills are shown next to it, not mixed into it. Follows the data while shown.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SkillsViewModel {

    /** A competency with its level and target already worded. */
    public record CompetencyRow(CompetencyView competency, String levelText, String targetText, String stepsText) {
    }

    /** A measure with its latest value, target and trend worded; {@code trend} are 0..1 heights, oldest first. */
    public record MeasureRow(MeasureView measure, String latestText, String targetText, String bestText,
                             String resultsText, List<Double> trend) {
    }

    private final CompetencyService competencyService;
    private final MeasureService measureService;
    private final DataChanges dataChanges;
    private final Messages messages;

    private final ObservableList<CompetencyRow> competencies = FXCollections.observableArrayList();
    private final ObservableList<CompetencyRow> competenciesReadOnly =
            FXCollections.unmodifiableObservableList(competencies);
    private final ObservableList<MeasureRow> measures = FXCollections.observableArrayList();
    private final ObservableList<MeasureRow> measuresReadOnly = FXCollections.unmodifiableObservableList(measures);

    private GoalDetails goal;
    private Subscription following = Subscription.EMPTY;

    public SkillsViewModel(CompetencyService competencyService, MeasureService measureService, DataChanges dataChanges,
                           Messages messages) {
        this.competencyService = competencyService;
        this.measureService = measureService;
        this.dataChanges = dataChanges;
        this.messages = messages;
    }

    public void show(GoalDetails shown) {
        this.goal = shown;
        following.unsubscribe();
        following = dataChanges.subscribe(this::refresh);
        refresh();
    }

    public void clear() {
        following.unsubscribe();
        following = Subscription.EMPTY;
        goal = null;
    }

    public void refresh() {
        if (goal == null) {
            return;
        }
        competencies.setAll(competencyService.forGoal(goal.id()).stream().map(this::row).toList());
        measures.setAll(measureService.forGoal(goal.id()).stream().map(this::row).toList());
    }

    // ---- actions ----

    public void setLevel(CompetencyId id, int level) {
        competencyService.changeLevel(id, level);
        dataChanges.markChanged();
    }

    public void moveCompetency(CompetencyId id, MoveDirection direction) {
        competencyService.move(id, direction);
        dataChanges.markChanged();
    }

    public void deleteCompetency(CompetencyId id) {
        competencyService.delete(id);
        dataChanges.markChanged();
    }

    public void deleteMeasure(MeasureId id) {
        measureService.delete(id);
        dataChanges.markChanged();
    }

    // ---- text ----

    /** "Not started", "Aware", … for a level from 0 to 5. */
    public String levelLabel(int level) {
        return messages.get("skill.level." + Math.max(Competency.MIN_LEVEL, Math.min(Competency.MAX_LEVEL, level)));
    }

    CompetencyRow row(CompetencyView competency) {
        String steps = competency.steps().isEmpty() ? messages.get("skills.noSteps")
                : messages.plural("skills.steps", competency.steps().size());
        return new CompetencyRow(competency, levelLabel(competency.level()),
                messages.get("skills.target", levelLabel(competency.targetLevel())), steps);
    }

    MeasureRow row(MeasureView measure) {
        String unit = measure.unit() == null ? "" : " " + measure.unit();
        String latest;
        if (measure.latest() == null) {
            latest = messages.get("skills.measure.none");
        } else {
            latest = Formats.number(measure.latest().value()) + (measure.maxValue() == null ? ""
                    : " / " + Formats.number(measure.maxValue())) + unit;
        }
        String target = measure.target() == null ? ""
                : messages.get(measure.direction() == Direction.LOWER_IS_BETTER ? "skills.measure.targetLower"
                : "skills.measure.target", Formats.number(measure.target()) + unit);
        String best = measure.best() == null || measure.resultCount() < 2 ? ""
                : messages.get("skills.measure.best", Formats.number(measure.best().value()) + unit);
        String results = messages.plural("skills.measure.results", measure.resultCount());
        return new MeasureRow(measure, latest, target, best, results, trend(measure));
    }

    /** The points of a measure scaled to 0..1 (higher is always better, so lower-is-better measures are flipped). */
    static List<Double> trend(MeasureView measure) {
        List<MeasureSeries.Point> points = measure.points();
        if (points.size() < 2) {
            return List.of();
        }
        BigDecimal min = points.stream().map(MeasureSeries.Point::value).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = points.stream().map(MeasureSeries.Point::value).max(BigDecimal::compareTo).orElseThrow();
        double span = max.subtract(min).doubleValue();
        boolean flip = measure.direction() == Direction.LOWER_IS_BETTER;
        return points.stream().map(point -> {
            double height = span == 0 ? 0.5 : point.value().subtract(min).doubleValue() / span;
            return flip ? 1 - height : height;
        }).toList();
    }

    // ---- reading ----

    public ObservableList<CompetencyRow> competencies() {
        return competenciesReadOnly;
    }

    public ObservableList<MeasureRow> measures() {
        return measuresReadOnly;
    }

    public GoalId goalId() {
        return goal == null ? null : goal.id();
    }

    public GoalDetails goal() {
        return goal;
    }
}

package com.edutrack.desktop.ui.insights;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.application.insights.InsightsService;
import com.edutrack.application.insights.ProgressPoint;
import com.edutrack.application.insights.WeekMinutes;
import com.edutrack.application.measure.MeasureService;
import com.edutrack.application.measure.MeasureView;
import com.edutrack.application.progress.ConsistencyService;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.session.SessionFormats;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.progress.StreakCalculator;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Subscription;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The Insights page: how much was studied each week, which days count as study days, how a goal's progress
 * grew, how each measure moved, and every area with its goal count and progress. All the numbers come from
 * what is already recorded: no snapshot is stored, so old data charts as soon as it is there.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InsightsViewModel {

    /** How many weeks the study-time chart covers. */
    public record Range(int weeks, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** An entry of the area filter; {@code id} is {@code null} for every area. */
    public record AreaChoice(AreaId id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** An entry of the goal box. */
    public record GoalChoice(GoalId id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** One bar: the week starting {@code weekStart} with the hours studied. */
    public record WeekBar(LocalDate weekStart, String label, double hours, String tooltip, boolean current) {
    }

    /** One point of a line chart. */
    public record LinePoint(LocalDate on, String label, double value) {
    }

    /** A measure's trend: the points, the target (or {@code null}), and a caption saying which way is better. */
    public record MeasureLine(String name, String unit, List<LinePoint> points, Double target, Double max,
                              String caption) {
    }

    /** The study days of the last weeks: minutes per day, with the week the grid starts on. */
    public record Heat(LocalDate firstDay, int weeks, Map<LocalDate, Integer> minutes) {
    }

    static final int HEAT_WEEKS = 26;

    private final AreaService areaService;
    private final GoalService goalService;
    private final InsightsService insightsService;
    private final ConsistencyService consistencyService;
    private final MeasureService measureService;
    private final UiPreferences preferences;
    private final Clock clock;
    private final Messages messages;

    private final ObservableList<AreaSummary> areas = FXCollections.observableArrayList();
    private final ObservableList<AreaSummary> areasReadOnly = FXCollections.unmodifiableObservableList(areas);
    private final BooleanBinding empty = Bindings.isEmpty(areas);
    private final ObservableList<Range> ranges;
    private final ObservableList<AreaChoice> areaChoices = FXCollections.observableArrayList();
    private final ObservableList<GoalChoice> goalChoices = FXCollections.observableArrayList();
    private final ObjectProperty<Range> range = new SimpleObjectProperty<>();
    private final ObjectProperty<AreaChoice> areaFilter = new SimpleObjectProperty<>();
    private final ObjectProperty<GoalChoice> goal = new SimpleObjectProperty<>();
    private final ObservableList<WeekBar> weekBars = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper weekSummary = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper streakText = new ReadOnlyStringWrapper("");
    private final ObjectProperty<Heat> heat = new SimpleObjectProperty<>();
    private final ObservableList<LinePoint> progressPoints = FXCollections.observableArrayList();
    private final ObservableList<MeasureLine> measureLines = FXCollections.observableArrayList();
    private final Subscription dataSubscription;
    private boolean loading;

    public InsightsViewModel(AreaService areaService, GoalService goalService, InsightsService insightsService,
                             ConsistencyService consistencyService, MeasureService measureService,
                             UiPreferences preferences, DataChanges dataChanges, Clock clock, Messages messages) {
        this.areaService = areaService;
        this.goalService = goalService;
        this.insightsService = insightsService;
        this.consistencyService = consistencyService;
        this.measureService = measureService;
        this.preferences = preferences;
        this.clock = clock;
        this.messages = messages;
        this.ranges = FXCollections.observableArrayList(new Range(12, messages.get("insights.range.12")),
                new Range(26, messages.get("insights.range.26")), new Range(52, messages.get("insights.range.52")));
        range.set(ranges.getFirst());
        range.subscribe(next -> refreshWeeks());
        areaFilter.subscribe(next -> refreshWeeks());
        goal.subscribe(next -> refreshGoal());
        refresh();
        dataSubscription = dataChanges.subscribe(this::refresh);
    }

    /** Stops following data changes; called when the shell holding the page is replaced. */
    public void dispose() {
        dataSubscription.unsubscribe();
    }

    public void refresh() {
        loading = true;
        try {
            areas.setAll(areaService.listAreas());
            AreaChoice previousArea = areaFilter.get();
            areaChoices.setAll(areaChoices(areas));
            areaFilter.set(areaChoices.stream().filter(choice -> previousArea != null && Objects.equals(choice.id(), previousArea.id()))
                    .findFirst().orElse(areaChoices.getFirst()));

            GoalChoice previousGoal = goal.get();
            goalChoices.setAll(goalChoices(areas));
            goal.set(goalChoices.stream().filter(choice -> previousGoal != null && choice.id().equals(previousGoal.id()))
                    .findFirst().orElse(goalChoices.isEmpty() ? null : goalChoices.getFirst()));
        } finally {
            loading = false;
        }
        refreshWeeks();
        refreshHeat();
        refreshGoal();
    }

    private List<AreaChoice> areaChoices(List<AreaSummary> all) {
        List<AreaChoice> choices = new ArrayList<>();
        choices.add(new AreaChoice(null, messages.get("insights.allAreas")));
        all.forEach(area -> choices.add(new AreaChoice(area.id(), area.name())));
        return choices;
    }

    private List<GoalChoice> goalChoices(List<AreaSummary> all) {
        List<GoalChoice> choices = new ArrayList<>();
        for (AreaSummary area : all) {
            for (GoalSummary summary : goalService.listGoals(area.id())) {
                choices.add(new GoalChoice(summary.id(), area.name() + " · " + summary.title()));
            }
        }
        return choices;
    }

    // ---- study time per week ----

    private void refreshWeeks() {
        if (loading || range.get() == null || areaFilter.get() == null) {
            return;
        }
        DayOfWeek weekStart = preferences.getWeekStart();
        List<WeekMinutes> weeks = insightsService.weeklyMinutes(range.get().weeks(), weekStart);
        AreaId filter = areaFilter.get().id();
        LocalDate thisWeek = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(weekStart));
        List<WeekBar> bars = new ArrayList<>();
        int totalMinutes = 0;
        for (WeekMinutes week : weeks) {
            int minutes = filter == null ? week.total() : week.minutesByArea().getOrDefault(filter, 0);
            totalMinutes += minutes;
            String day = SessionFormats.dayMonth(week.weekStart());
            bars.add(new WeekBar(week.weekStart(), day, hours(minutes),
                    messages.get("insights.week.tooltip", day, SessionFormats.duration(minutes)),
                    week.weekStart().equals(thisWeek)));
        }
        weekBars.setAll(bars);
        weekSummary.set(totalMinutes == 0 ? messages.get("insights.week.none")
                : messages.get("insights.week.summary", SessionFormats.duration(totalMinutes), weeks.size(),
                SessionFormats.perWeek(Math.round((float) totalMinutes / Math.max(1, weeks.size())))));
    }

    static double hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).doubleValue();
    }

    // ---- study days ----

    private void refreshHeat() {
        LocalDate today = LocalDate.now(clock);
        DayOfWeek weekStart = preferences.getWeekStart();
        LocalDate firstDay = today.with(TemporalAdjusters.previousOrSame(weekStart)).minusWeeks(HEAT_WEEKS - 1L);
        heat.set(new Heat(firstDay, HEAT_WEEKS, insightsService.dailyMinutes(firstDay, today)));
        streakText.set(streakText(consistencyService.streak(), consistencyService.daysThisWeek(weekStart)));
    }

    String streakText(StreakCalculator.Streak streak, int daysThisWeek) {
        return messages.get("insights.streak", streak.current(), streak.longest(), daysThisWeek);
    }

    // ---- one goal: progress over time and its measures ----

    private void refreshGoal() {
        if (loading) {
            return;
        }
        progressPoints.clear();
        measureLines.clear();
        if (goal.get() == null) {
            return;
        }
        GoalId id = goal.get().id();
        List<ProgressPoint> history = insightsService.goalProgressHistory(id);
        progressPoints.setAll(history.stream()
                .map(point -> new LinePoint(point.on(), SessionFormats.dayMonth(point.on()), point.progress().percent().doubleValue()))
                .toList());
        measureLines.setAll(measureService.forGoal(id).stream().filter(measure -> !measure.points().isEmpty())
                .map(this::line).toList());
    }

    MeasureLine line(MeasureView measure) {
        List<LinePoint> points = measure.points().stream()
                .map(point -> new LinePoint(point.on(), SessionFormats.dayMonth(point.on()), point.value().doubleValue()))
                .toList();
        String caption = messages.get(measure.direction() == Direction.LOWER_IS_BETTER ? "insights.measure.lower"
                : "insights.measure.higher");
        return new MeasureLine(measure.name(), measure.unit(), points,
                measure.target() == null ? null : measure.target().doubleValue(),
                measure.maxValue() == null ? null : measure.maxValue().doubleValue(), caption);
    }

    // ---- reading ----

    public ObservableList<AreaSummary> areas() {
        return areasReadOnly; // one shared wrapper: it only weakly listens to the source list
    }

    /** True when there are no areas yet: the page says there is nothing to show. */
    public BooleanBinding emptyProperty() {
        return empty;
    }

    public ObservableList<Range> ranges() {
        return ranges;
    }

    public ObservableList<AreaChoice> areaChoices() {
        return areaChoices;
    }

    public ObservableList<GoalChoice> goalChoices() {
        return goalChoices;
    }

    public ObjectProperty<Range> rangeProperty() {
        return range;
    }

    public ObjectProperty<AreaChoice> areaFilterProperty() {
        return areaFilter;
    }

    public ObjectProperty<GoalChoice> goalProperty() {
        return goal;
    }

    public ObservableList<WeekBar> weekBars() {
        return weekBars;
    }

    /** "38 h in the last 12 weeks · 3.2 h/week on average". */
    public ReadOnlyStringProperty weekSummaryProperty() {
        return weekSummary.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty streakTextProperty() {
        return streakText.getReadOnlyProperty();
    }

    public ObjectProperty<Heat> heatProperty() {
        return heat;
    }

    public ObservableList<LinePoint> progressPoints() {
        return progressPoints;
    }

    public ObservableList<MeasureLine> measureLines() {
        return measureLines;
    }

    /** The first day of the week, for laying the study-days grid out. */
    public DayOfWeek weekStart() {
        return preferences.getWeekStart();
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}

package com.edutrack.desktop.ui.review;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.application.review.ReviewItem;
import com.edutrack.application.review.ReviewScope;
import com.edutrack.application.review.ReviewService;
import com.edutrack.application.session.SessionCommands;
import com.edutrack.application.session.SessionService;
import com.edutrack.desktop.ui.cards.CardFormats;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.review.Grade;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.shared.DomainValidationException;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The Review page: choose what to study (all subjects, an area or one goal), go through the queue one
 * card side at a time, and see how it went.
 *
 * <p>A side graded Again goes to the end of the queue and comes back before the session ends. Each
 * answer is timed from the moment the card appeared (at most {@value #MAX_CARD_SECONDS} seconds, so a
 * forgotten window does not inflate it) and passed to the review history. At the end the minutes spent
 * on each goal's cards can be logged as study time.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ReviewSessionViewModel {

    /** Where the page is: choosing, looking at a prompt, looking at the answer, or reading the summary. */
    public enum Phase {
        START,
        QUESTION,
        ANSWER,
        SUMMARY
    }

    /** One entry of the subject selector. */
    public record ScopeOption(String label, ReviewScope scope) {

        @Override
        public String toString() {
            return label;
        }
    }

    /** The longest time one card counts for. */
    public static final int MAX_CARD_SECONDS = 300;

    private static final int MAX_SESSION_MINUTES = 1440;

    private final ReviewService reviews;
    private final SessionService sessions;
    private final AreaService areas;
    private final GoalService goals;
    private final UiPreferences preferences;
    private final DataChanges dataChanges;
    private final Clock clock;
    private final Messages messages;

    private final ObservableList<ScopeOption> scopes = FXCollections.observableArrayList();
    private final ObservableList<ScopeOption> scopesReadOnly = FXCollections.unmodifiableObservableList(scopes);
    private final ObjectProperty<ScopeOption> scope = new SimpleObjectProperty<>();
    private final ReadOnlyObjectWrapper<Phase> phase = new ReadOnlyObjectWrapper<>(Phase.START);

    private final ReadOnlyStringWrapper countsText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper limitsText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper nextDueText = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper nothingToReview = new ReadOnlyBooleanWrapper(true);

    private final ReadOnlyStringWrapper progressText = new ReadOnlyStringWrapper("");
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(0);
    private final ReadOnlyStringWrapper captionText = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper newCard = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper prompt = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper answer = new ReadOnlyStringWrapper("");
    private final Map<Grade, ReadOnlyStringWrapper> intervalTexts = new EnumMap<>(Grade.class);

    private final ReadOnlyStringWrapper cardsText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper againText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper accuracyText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper timeText = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper canLogTime = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper logText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper("");
    private final BooleanProperty logged = new SimpleBooleanProperty(false);

    private final Deque<ReviewItem> queue = new ArrayDeque<>();
    private final Map<GoalId, Long> secondsByGoal = new LinkedHashMap<>();
    private ReviewItem current;
    private Instant cardShownAt;
    private Instant sessionStartedAt;
    private int answered;
    private int again;
    private long totalSeconds;
    private boolean reloading;

    public ReviewSessionViewModel(ReviewService reviews, SessionService sessions, AreaService areas,
                                  GoalService goals, UiPreferences preferences, DataChanges dataChanges,
                                  Clock clock, Messages messages) {
        this.reviews = reviews;
        this.sessions = sessions;
        this.areas = areas;
        this.goals = goals;
        this.preferences = preferences;
        this.dataChanges = dataChanges;
        this.clock = clock;
        this.messages = messages;
        for (Grade grade : Grade.values()) {
            intervalTexts.put(grade, new ReadOnlyStringWrapper(""));
        }
        reloadScopes(ReviewScope.ALL);
        updateCounts();
        // Only the learner's own choice reloads the counts; reloading the list does it itself.
        scope.subscribe((before, after) -> {
            if (!reloading && phase.get() == Phase.START && after != null && !Objects.equals(before, after)) {
                updateCounts();
            }
        });
    }

    // ---------- choosing what to study ----------

    /**
     * Reloads the subject list and the counts of the start page; a session in progress (or its summary)
     * is left alone, so coming back to the page does not lose it.
     */
    public void refresh() {
        reloadScopes(currentScope());
        if (phase.get() == Phase.START) {
            updateCounts();
        }
    }

    /** Starts over on the start page with {@code wanted} chosen, e.g. after "Review this goal". */
    public void show(ReviewScope wanted) {
        resetSession();
        reloadScopes(wanted == null ? ReviewScope.ALL : wanted);
        phase.set(Phase.START);
        updateCounts();
    }

    private ReviewScope currentScope() {
        ScopeOption chosen = scope.get();
        return chosen == null ? ReviewScope.ALL : chosen.scope();
    }

    private void reloadScopes(ReviewScope keep) {
        List<ScopeOption> options = new ArrayList<>();
        options.add(new ScopeOption(messages.get("review.scope.all"), ReviewScope.ALL));
        for (AreaSummary area : areas.listAreas()) {
            options.add(new ScopeOption(area.name(), ReviewScope.ofArea(area.id())));
            for (GoalSummary goal : goals.listGoals(area.id())) {
                options.add(new ScopeOption(messages.get("review.scope.goal", area.name(), goal.title()),
                        ReviewScope.ofGoal(goal.id())));
            }
        }
        reloading = true;
        try {
            scopes.setAll(options);
            scope.set(options.stream().filter(option -> option.scope().equals(keep)).findFirst().orElse(options.getFirst()));
        } finally {
            reloading = false;
        }
    }

    private void updateCounts() {
        List<ReviewItem> waiting = waiting();
        int fresh = (int) waiting.stream().filter(ReviewItem::isNew).count();
        int due = waiting.size() - fresh;
        countsText.set(messages.get("review.counts", due, fresh));
        limitsText.set(messages.get("review.limits", preferences.getDailyNewCards(), preferences.getDailyReviewLimit()));
        nothingToReview.set(waiting.isEmpty());
        nextDueText.set(waiting.isEmpty()
                ? reviews.nextDue(currentScope()).map(day -> messages.get("review.nextDue", Formats.date(day))).orElse("")
                : "");
    }

    private List<ReviewItem> waiting() {
        return reviews.queue(currentScope(), preferences.getDailyNewCards(), preferences.getDailyReviewLimit());
    }

    // ---------- the session ----------

    /** Begins a session with what is waiting in the chosen subject; does nothing when nothing is. */
    public void start() {
        List<ReviewItem> waiting = waiting();
        if (waiting.isEmpty()) {
            updateCounts();
            return;
        }
        resetSession();
        queue.addAll(waiting);
        sessionStartedAt = clock.instant();
        present(queue.poll());
    }

    /** Shows the answer of the card on screen. */
    public void reveal() {
        if (phase.get() != Phase.QUESTION) {
            return;
        }
        answer.set(current.answer());
        Map<Grade, Integer> days = reviews.previewIntervals(current.cardId(), current.side());
        days.forEach((grade, interval) -> intervalTexts.get(grade).set(CardFormats.interval(interval)));
        phase.set(Phase.ANSWER);
    }

    /**
     * Records the answer to the card on screen and moves on. Again puts the card at the end of the queue.
     * A card deleted in the meantime is skipped.
     */
    public void grade(Grade grade) {
        if (phase.get() != Phase.ANSWER) {
            return;
        }
        long seconds = Math.min(MAX_CARD_SECONDS, Math.max(0, Duration.between(cardShownAt, clock.instant()).toSeconds()));
        try {
            reviews.grade(current.cardId(), current.side(), grade, (int) seconds);
        } catch (NotFoundException deleted) {
            advance();
            return;
        }
        answered++;
        totalSeconds += seconds;
        if (current.goalId() != null) {
            secondsByGoal.merge(current.goalId(), seconds, Long::sum);
        }
        if (grade == Grade.AGAIN) {
            again++;
            queue.addLast(current);
        }
        dataChanges.markChanged();
        advance();
    }

    /** Leaves the session early: the summary when something was answered, the start page otherwise. */
    public void endSession() {
        if (answered > 0) {
            summarize();
        } else {
            backToStart();
        }
    }

    /** Back from the summary (or a session without answers) to choosing what to study. */
    public void backToStart() {
        resetSession();
        phase.set(Phase.START);
        updateCounts();
    }

    private void advance() {
        if (queue.isEmpty()) {
            summarize();
        } else {
            present(queue.poll());
        }
    }

    private void present(ReviewItem item) {
        current = item;
        cardShownAt = clock.instant();
        prompt.set(item.prompt());
        answer.set("");
        newCard.set(item.isNew());
        captionText.set(messages.get("review.caption", item.areaName(), item.goalTitle()));
        int position = answered + 1;
        int total = answered + 1 + queue.size();
        progressText.set(messages.get("review.progress", position, total));
        progress.set(answered / (double) total);
        phase.set(Phase.QUESTION);
    }

    private void resetSession() {
        queue.clear();
        secondsByGoal.clear();
        current = null;
        cardShownAt = null;
        sessionStartedAt = null;
        answered = 0;
        again = 0;
        totalSeconds = 0;
        logged.set(false);
        error.set("");
        logText.set("");
        prompt.set("");
        answer.set("");
    }

    // ---------- the summary ----------

    private void summarize() {
        int cards = answered - again;
        cardsText.set(String.valueOf(cards));
        againText.set(String.valueOf(again));
        int accuracy = answered == 0 ? 0 : (int) Math.round(100.0 * (answered - again) / answered);
        accuracyText.set(accuracy + "%");
        timeText.set(duration(totalSeconds));
        canLogTime.set(!minutesByGoal().isEmpty());
        logText.set(canLogTime.get() ? "" : messages.get("review.log.nothing"));
        phase.set(Phase.SUMMARY);
    }

    /** "45 s", "3 min 20 s" or "1 h 05 min". */
    String duration(long seconds) {
        if (seconds < 60) {
            return messages.get("review.time.seconds", seconds);
        }
        if (seconds < 3600) {
            return messages.get("review.time.minutes", seconds / 60, seconds % 60);
        }
        return messages.get("review.time.hours", seconds / 3600, String.format("%02d", seconds % 3600 / 60));
    }

    /** Whole minutes spent on each goal's cards; goals under a minute are left out. */
    private Map<GoalId, Integer> minutesByGoal() {
        Map<GoalId, Integer> minutes = new LinkedHashMap<>();
        secondsByGoal.forEach((goal, seconds) -> {
            long whole = seconds / 60;
            if (whole >= 1) {
                minutes.put(goal, (int) Math.min(whole, MAX_SESSION_MINUTES));
            }
        });
        return minutes;
    }

    /**
     * Logs one manual study session per goal with the minutes spent on that goal's cards, started when
     * the review began. Does nothing twice, and a failure keeps the goals not yet logged for a retry.
     */
    public void logAsStudyTime() {
        if (logged.get() || !canLogTime.get()) {
            return;
        }
        error.set("");
        Map<GoalId, Integer> minutes = minutesByGoal();
        int total = 0;
        try {
            for (Map.Entry<GoalId, Integer> entry : minutes.entrySet()) {
                sessions.log(new SessionCommands.LogSession(entry.getKey(), null, null, sessionStartedAt,
                        entry.getValue(), messages.get("review.log.note"), SessionSource.MANUAL));
                total += entry.getValue();
                secondsByGoal.remove(entry.getKey());
            }
        } catch (DomainValidationException | NotFoundException rejected) {
            error.set(messages.error(rejected));
            return;
        } finally {
            if (total > 0) {
                dataChanges.markChanged();
            }
        }
        logged.set(true);
        canLogTime.set(false);
        logText.set(messages.get("review.logged", total));
    }

    // ---------- bindable state ----------

    public ObservableList<ScopeOption> scopes() {
        return scopesReadOnly;
    }

    public ObjectProperty<ScopeOption> scopeProperty() {
        return scope;
    }

    public ReadOnlyObjectProperty<Phase> phaseProperty() {
        return phase.getReadOnlyProperty();
    }

    /** "18 due · 5 new" for the chosen subject. */
    public ReadOnlyStringProperty countsTextProperty() {
        return countsText.getReadOnlyProperty();
    }

    /** "Today's limits: 20 new cards, 200 reviews". */
    public ReadOnlyStringProperty limitsTextProperty() {
        return limitsText.getReadOnlyProperty();
    }

    /** "Next review: 03 Nov 2026" when nothing is waiting but studied cards will come back; otherwise empty. */
    public ReadOnlyStringProperty nextDueTextProperty() {
        return nextDueText.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty nothingToReviewProperty() {
        return nothingToReview.getReadOnlyProperty();
    }

    /** "7 / 23". */
    public ReadOnlyStringProperty progressTextProperty() {
        return progressText.getReadOnlyProperty();
    }

    /** Answers given so far out of the cards in the session, 0 to 1. */
    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    /** "English › IELTS 7.0". */
    public ReadOnlyStringProperty captionTextProperty() {
        return captionText.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty newCardProperty() {
        return newCard.getReadOnlyProperty();
    }

    /** Markdown of the side being asked. */
    public ReadOnlyStringProperty promptProperty() {
        return prompt.getReadOnlyProperty();
    }

    /** Markdown of the answer; empty until it is revealed. */
    public ReadOnlyStringProperty answerProperty() {
        return answer.getReadOnlyProperty();
    }

    /** The caption under a grade button: "today", "1 d", "6 d", "2 mo". Filled when the answer is shown. */
    public ReadOnlyStringProperty intervalTextProperty(Grade grade) {
        return intervalTexts.get(grade).getReadOnlyProperty();
    }

    public ReadOnlyStringProperty cardsTextProperty() {
        return cardsText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty againTextProperty() {
        return againText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty accuracyTextProperty() {
        return accuracyText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty timeTextProperty() {
        return timeText.getReadOnlyProperty();
    }

    /** True while the summary has at least one goal with a minute or more that is not logged yet. */
    public ReadOnlyBooleanProperty canLogTimeProperty() {
        return canLogTime.getReadOnlyProperty();
    }

    /** "Logged 12 minutes of study time" once the time was logged; a note when there was too little to log. */
    public ReadOnlyStringProperty logTextProperty() {
        return logText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty errorProperty() {
        return error.getReadOnlyProperty();
    }

    /** Whether a session is in progress (a prompt or an answer on screen). */
    public boolean inSession() {
        return phase.get() == Phase.QUESTION || phase.get() == Phase.ANSWER;
    }
}

package com.edutrack.desktop.ui.settings;

import com.edutrack.desktop.ui.common.Language;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * The learner's settings, kept per Windows user (registry-backed {@link Preferences}, like the theme).
 * They are interface and habit choices, not learning data, so they stay out of PostgreSQL.
 *
 * <p>Every setting is an observable property and saves the moment it changes. Numbers are clamped to
 * their allowed range, so a view can never store something the rest of the app does not expect.
 * Settings that later features implement (tray, start with Windows, backups, reminders) are stored
 * here already and simply read when those features arrive.
 */
@Component
public class UiPreferences {

    public static final int NEW_CARDS_MAX = 500;
    public static final int REVIEW_LIMIT_MAX = 9999;
    public static final int FOCUS_MINUTES_MIN = 5;
    public static final int FOCUS_MINUTES_MAX = 120;
    public static final int BREAK_MINUTES_MIN = 1;
    public static final int BREAK_MINUTES_MAX = 60;
    public static final int REMINDER_HOUR_MAX = 23;

    private static final String LANGUAGE = "language";
    private static final String WEEK_START = "weekStart";
    private static final String CHECK_IN_DAY = "checkInDay";
    private static final String DAILY_NEW_CARDS = "dailyNewCards";
    private static final String DAILY_REVIEW_LIMIT = "dailyReviewLimit";
    private static final String FOCUS_MINUTES = "pomodoroFocusMinutes";
    private static final String BREAK_MINUTES = "pomodoroBreakMinutes";
    private static final String NOTIFICATIONS = "notificationsEnabled";
    private static final String MINIMIZE_TO_TRAY = "minimizeToTray";
    private static final String SIDEBAR_COLLAPSED = "sidebarCollapsed";
    private static final String START_WITH_WINDOWS = "startWithWindows";
    private static final String BACKUP_FOLDER = "backupFolder";
    private static final String WEEKLY_BACKUP = "automaticWeeklyBackup";
    private static final String REMINDER_HOUR = "reminderHour";
    private static final String TIMER_PREFIX = "studyTimer.";

    private final Preferences node;

    private final ReadOnlyObjectWrapper<Language> language = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<DayOfWeek> weekStart = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<DayOfWeek> checkInDay = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyIntegerWrapper dailyNewCards = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper dailyReviewLimit = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper pomodoroFocusMinutes = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper pomodoroBreakMinutes = new ReadOnlyIntegerWrapper();
    private final ReadOnlyBooleanWrapper notificationsEnabled = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper minimizeToTray = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper sidebarCollapsed = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper startWithWindows = new ReadOnlyBooleanWrapper();
    private final ReadOnlyStringWrapper backupFolder = new ReadOnlyStringWrapper();
    private final ReadOnlyBooleanWrapper automaticWeeklyBackup = new ReadOnlyBooleanWrapper();
    private final ReadOnlyIntegerWrapper reminderHour = new ReadOnlyIntegerWrapper();

    /** The settings of the real user, in this package's preferences node. */
    @Autowired
    public UiPreferences() {
        this(defaultNode());
    }

    /** For tests: any node, so the user's real settings are never touched. */
    public UiPreferences(Preferences node) {
        this.node = node;
        language.set(Language.resolve(node.get(LANGUAGE, null), Locale.getDefault()));
        weekStart.set(day(WEEK_START, DayOfWeek.MONDAY));
        checkInDay.set(day(CHECK_IN_DAY, DayOfWeek.SUNDAY));
        dailyNewCards.set(clamp(node.getInt(DAILY_NEW_CARDS, 20), 0, NEW_CARDS_MAX));
        dailyReviewLimit.set(clamp(node.getInt(DAILY_REVIEW_LIMIT, 200), 0, REVIEW_LIMIT_MAX));
        pomodoroFocusMinutes.set(clamp(node.getInt(FOCUS_MINUTES, 25), FOCUS_MINUTES_MIN, FOCUS_MINUTES_MAX));
        pomodoroBreakMinutes.set(clamp(node.getInt(BREAK_MINUTES, 5), BREAK_MINUTES_MIN, BREAK_MINUTES_MAX));
        notificationsEnabled.set(node.getBoolean(NOTIFICATIONS, true));
        minimizeToTray.set(node.getBoolean(MINIMIZE_TO_TRAY, false));
        sidebarCollapsed.set(node.getBoolean(SIDEBAR_COLLAPSED, false));
        startWithWindows.set(node.getBoolean(START_WITH_WINDOWS, false));
        backupFolder.set(node.get(BACKUP_FOLDER, defaultBackupFolder()));
        automaticWeeklyBackup.set(node.getBoolean(WEEKLY_BACKUP, true));
        reminderHour.set(clamp(node.getInt(REMINDER_HOUR, 19), 0, REMINDER_HOUR_MAX));
    }

    private static Preferences defaultNode() {
        return Preferences.userNodeForPackage(UiPreferences.class);
    }

    /** The language to show, decided before Spring starts (the start-up error dialog needs it too). */
    public static Language savedLanguage() {
        return Language.resolve(defaultNode().get(LANGUAGE, null), Locale.getDefault());
    }

    public static String defaultBackupFolder() {
        return Path.of(System.getProperty("user.home"), "EduTrack Backups").toString();
    }

    // ---- language ----

    public Language getLanguage() {
        return language.get();
    }

    public void setLanguage(Language value) {
        language.set(value);
        node.put(LANGUAGE, value.name());
    }

    public ReadOnlyObjectProperty<Language> languageProperty() {
        return language.getReadOnlyProperty();
    }

    // ---- calendar ----

    /** Monday or Sunday: the first day of the week in calendars and weekly counts. */
    public DayOfWeek getWeekStart() {
        return weekStart.get();
    }

    public void setWeekStart(DayOfWeek value) {
        DayOfWeek day = value == DayOfWeek.SUNDAY ? DayOfWeek.SUNDAY : DayOfWeek.MONDAY;
        weekStart.set(day);
        node.put(WEEK_START, day.name());
    }

    public ReadOnlyObjectProperty<DayOfWeek> weekStartProperty() {
        return weekStart.getReadOnlyProperty();
    }

    /** The day of the weekly check-in. */
    public DayOfWeek getCheckInDay() {
        return checkInDay.get();
    }

    public void setCheckInDay(DayOfWeek value) {
        checkInDay.set(value);
        node.put(CHECK_IN_DAY, value.name());
    }

    public ReadOnlyObjectProperty<DayOfWeek> checkInDayProperty() {
        return checkInDay.getReadOnlyProperty();
    }

    // ---- study ----

    /** New flashcards introduced per day, 0–{@value #NEW_CARDS_MAX}. */
    public int getDailyNewCards() {
        return dailyNewCards.get();
    }

    public void setDailyNewCards(int value) {
        int next = clamp(value, 0, NEW_CARDS_MAX);
        dailyNewCards.set(next);
        node.putInt(DAILY_NEW_CARDS, next);
    }

    public ReadOnlyIntegerProperty dailyNewCardsProperty() {
        return dailyNewCards.getReadOnlyProperty();
    }

    /** Maximum reviews shown per day, 0–{@value #REVIEW_LIMIT_MAX}. */
    public int getDailyReviewLimit() {
        return dailyReviewLimit.get();
    }

    public void setDailyReviewLimit(int value) {
        int next = clamp(value, 0, REVIEW_LIMIT_MAX);
        dailyReviewLimit.set(next);
        node.putInt(DAILY_REVIEW_LIMIT, next);
    }

    public ReadOnlyIntegerProperty dailyReviewLimitProperty() {
        return dailyReviewLimit.getReadOnlyProperty();
    }

    /** Length of a focus block in minutes, 5–120. */
    public int getPomodoroFocusMinutes() {
        return pomodoroFocusMinutes.get();
    }

    public void setPomodoroFocusMinutes(int value) {
        int next = clamp(value, FOCUS_MINUTES_MIN, FOCUS_MINUTES_MAX);
        pomodoroFocusMinutes.set(next);
        node.putInt(FOCUS_MINUTES, next);
    }

    public ReadOnlyIntegerProperty pomodoroFocusMinutesProperty() {
        return pomodoroFocusMinutes.getReadOnlyProperty();
    }

    /** Length of a break in minutes, 1–60. */
    public int getPomodoroBreakMinutes() {
        return pomodoroBreakMinutes.get();
    }

    public void setPomodoroBreakMinutes(int value) {
        int next = clamp(value, BREAK_MINUTES_MIN, BREAK_MINUTES_MAX);
        pomodoroBreakMinutes.set(next);
        node.putInt(BREAK_MINUTES, next);
    }

    public ReadOnlyIntegerProperty pomodoroBreakMinutesProperty() {
        return pomodoroBreakMinutes.getReadOnlyProperty();
    }

    /** Hour of the day (0–23) at which routines remind the learner. */
    public int getReminderHour() {
        return reminderHour.get();
    }

    public void setReminderHour(int value) {
        int next = clamp(value, 0, REMINDER_HOUR_MAX);
        reminderHour.set(next);
        node.putInt(REMINDER_HOUR, next);
    }

    public ReadOnlyIntegerProperty reminderHourProperty() {
        return reminderHour.getReadOnlyProperty();
    }

    // ---- notifications ----

    public boolean isNotificationsEnabled() {
        return notificationsEnabled.get();
    }

    public void setNotificationsEnabled(boolean value) {
        notificationsEnabled.set(value);
        node.putBoolean(NOTIFICATIONS, value);
    }

    public ReadOnlyBooleanProperty notificationsEnabledProperty() {
        return notificationsEnabled.getReadOnlyProperty();
    }

    public boolean isMinimizeToTray() {
        return minimizeToTray.get();
    }

    public void setMinimizeToTray(boolean value) {
        minimizeToTray.set(value);
        node.putBoolean(MINIMIZE_TO_TRAY, value);
    }

    public ReadOnlyBooleanProperty minimizeToTrayProperty() {
        return minimizeToTray.getReadOnlyProperty();
    }

    public boolean isStartWithWindows() {
        return startWithWindows.get();
    }

    public void setStartWithWindows(boolean value) {
        startWithWindows.set(value);
        node.putBoolean(START_WITH_WINDOWS, value);
    }

    public ReadOnlyBooleanProperty startWithWindowsProperty() {
        return startWithWindows.getReadOnlyProperty();
    }

    // ---- window ----

    /** Whether the main window's left rail is folded down to its toggle button. */
    public boolean isSidebarCollapsed() {
        return sidebarCollapsed.get();
    }

    public void setSidebarCollapsed(boolean value) {
        sidebarCollapsed.set(value);
        node.putBoolean(SIDEBAR_COLLAPSED, value);
    }

    public ReadOnlyBooleanProperty sidebarCollapsedProperty() {
        return sidebarCollapsed.getReadOnlyProperty();
    }

    // ---- data ----

    /** Where backups are written; {@code ~/EduTrack Backups} until the learner picks another folder. */
    public String getBackupFolder() {
        return backupFolder.get();
    }

    public void setBackupFolder(String value) {
        String folder = value == null || value.isBlank() ? defaultBackupFolder() : value.strip();
        backupFolder.set(folder);
        node.put(BACKUP_FOLDER, folder);
    }

    public ReadOnlyStringProperty backupFolderProperty() {
        return backupFolder.getReadOnlyProperty();
    }

    public boolean isAutomaticWeeklyBackup() {
        return automaticWeeklyBackup.get();
    }

    public void setAutomaticWeeklyBackup(boolean value) {
        automaticWeeklyBackup.set(value);
        node.putBoolean(WEEKLY_BACKUP, value);
    }

    public ReadOnlyBooleanProperty automaticWeeklyBackupProperty() {
        return automaticWeeklyBackup.getReadOnlyProperty();
    }

    // ---- study timer (state kept so a session survives closing the app) ----

    /**
     * Where the study timer stood at its last state change. Plain strings and numbers, so this class
     * knows nothing of the timer itself; {@code stepId}, {@code routineId} and {@code runningSince} may be {@code null}.
     */
    public record TimerSnapshot(String goalId, String stepId, String routineId, String title, String mode,
                                String phase, Instant startedAt, long focusMillis, long phaseMillis,
                                Instant runningSince, int completedBlocks) {
    }

    /** The saved timer state, or empty when no session was active when the app last changed it. */
    public Optional<TimerSnapshot> getSavedTimer() {
        String goal = node.get(TIMER_PREFIX + "goal", null);
        String started = node.get(TIMER_PREFIX + "startedAt", null);
        if (goal == null || started == null) {
            return Optional.empty();
        }
        try {
            String running = node.get(TIMER_PREFIX + "runningSince", null);
            return Optional.of(new TimerSnapshot(goal, node.get(TIMER_PREFIX + "step", null),
                    node.get(TIMER_PREFIX + "routine", null), node.get(TIMER_PREFIX + "title", ""),
                    node.get(TIMER_PREFIX + "mode", "FREE"), node.get(TIMER_PREFIX + "phase", "FOCUS"),
                    Instant.parse(started), node.getLong(TIMER_PREFIX + "focusMillis", 0),
                    node.getLong(TIMER_PREFIX + "phaseMillis", 0), running == null ? null : Instant.parse(running),
                    node.getInt(TIMER_PREFIX + "blocks", 0)));
        } catch (RuntimeException unreadable) {
            clearSavedTimer();
            return Optional.empty();
        }
    }

    public void saveTimer(TimerSnapshot timer) {
        node.put(TIMER_PREFIX + "goal", timer.goalId());
        putOrRemove(TIMER_PREFIX + "step", timer.stepId());
        putOrRemove(TIMER_PREFIX + "routine", timer.routineId());
        node.put(TIMER_PREFIX + "title", timer.title() == null ? "" : timer.title());
        node.put(TIMER_PREFIX + "mode", timer.mode());
        node.put(TIMER_PREFIX + "phase", timer.phase());
        node.put(TIMER_PREFIX + "startedAt", timer.startedAt().toString());
        node.putLong(TIMER_PREFIX + "focusMillis", timer.focusMillis());
        node.putLong(TIMER_PREFIX + "phaseMillis", timer.phaseMillis());
        putOrRemove(TIMER_PREFIX + "runningSince", timer.runningSince() == null ? null : timer.runningSince().toString());
        node.putInt(TIMER_PREFIX + "blocks", timer.completedBlocks());
    }

    public void clearSavedTimer() {
        for (String key : new String[]{"goal", "step", "routine", "title", "mode", "phase", "startedAt",
                "focusMillis", "phaseMillis", "runningSince", "blocks"}) {
            node.remove(TIMER_PREFIX + key);
        }
    }

    private void putOrRemove(String key, String value) {
        if (value == null) {
            node.remove(key);
        } else {
            node.put(key, value);
        }
    }

    // ---- helpers ----

    private DayOfWeek day(String key, DayOfWeek fallback) {
        try {
            return DayOfWeek.valueOf(node.get(key, fallback.name()));
        } catch (IllegalArgumentException unknownValue) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

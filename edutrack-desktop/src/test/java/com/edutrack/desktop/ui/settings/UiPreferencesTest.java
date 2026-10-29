package com.edutrack.desktop.ui.settings;

import com.edutrack.desktop.ui.common.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against a node of its own, so the learner's real settings are never read or changed. */
class UiPreferencesTest {

    private final Preferences node = Preferences.userRoot().node("edutrack-test/" + UUID.randomUUID());

    @AfterEach
    void removeNode() throws BackingStoreException {
        node.removeNode();
    }

    private UiPreferences fresh() {
        return new UiPreferences(node);
    }

    @Test
    void defaultsMatchTheSpecification() {
        UiPreferences settings = fresh();

        assertThat(settings.getWeekStart()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(settings.getCheckInDay()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(settings.getDailyNewCards()).isEqualTo(20);
        assertThat(settings.getDailyReviewLimit()).isEqualTo(200);
        assertThat(settings.getPomodoroFocusMinutes()).isEqualTo(25);
        assertThat(settings.getPomodoroBreakMinutes()).isEqualTo(5);
        assertThat(settings.isNotificationsEnabled()).isTrue();
        assertThat(settings.isMinimizeToTray()).isFalse();
        assertThat(settings.isStartWithWindows()).isFalse();
        assertThat(settings.getBackupFolder()).isEqualTo(Path.of(System.getProperty("user.home"), "EduTrack Backups").toString());
        assertThat(settings.isAutomaticWeeklyBackup()).isTrue();
        assertThat(settings.getReminderHour()).isEqualTo(19);
    }

    @Test
    void everySettingIsSavedAtOnceAndReadBackByANewInstance() {
        UiPreferences settings = fresh();

        settings.setLanguage(Language.SPANISH);
        settings.setWeekStart(DayOfWeek.SUNDAY);
        settings.setCheckInDay(DayOfWeek.FRIDAY);
        settings.setDailyNewCards(35);
        settings.setDailyReviewLimit(500);
        settings.setPomodoroFocusMinutes(50);
        settings.setPomodoroBreakMinutes(10);
        settings.setNotificationsEnabled(false);
        settings.setMinimizeToTray(true);
        settings.setStartWithWindows(true);
        settings.setBackupFolder("D:\\Backups");
        settings.setAutomaticWeeklyBackup(false);
        settings.setReminderHour(8);

        UiPreferences reopened = fresh();
        assertThat(reopened.getLanguage()).isEqualTo(Language.SPANISH);
        assertThat(reopened.getWeekStart()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(reopened.getCheckInDay()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(reopened.getDailyNewCards()).isEqualTo(35);
        assertThat(reopened.getDailyReviewLimit()).isEqualTo(500);
        assertThat(reopened.getPomodoroFocusMinutes()).isEqualTo(50);
        assertThat(reopened.getPomodoroBreakMinutes()).isEqualTo(10);
        assertThat(reopened.isNotificationsEnabled()).isFalse();
        assertThat(reopened.isMinimizeToTray()).isTrue();
        assertThat(reopened.isStartWithWindows()).isTrue();
        assertThat(reopened.getBackupFolder()).isEqualTo("D:\\Backups");
        assertThat(reopened.isAutomaticWeeklyBackup()).isFalse();
        assertThat(reopened.getReminderHour()).isEqualTo(8);
    }

    @Test
    void numbersStayInsideTheirRange() {
        UiPreferences settings = fresh();

        settings.setDailyNewCards(9000);
        settings.setDailyReviewLimit(-4);
        settings.setPomodoroFocusMinutes(1);
        settings.setPomodoroBreakMinutes(500);
        settings.setReminderHour(30);

        assertThat(settings.getDailyNewCards()).isEqualTo(500);
        assertThat(settings.getDailyReviewLimit()).isZero();
        assertThat(settings.getPomodoroFocusMinutes()).isEqualTo(5);
        assertThat(settings.getPomodoroBreakMinutes()).isEqualTo(60);
        assertThat(settings.getReminderHour()).isEqualTo(23);
        assertThat(fresh().getDailyNewCards()).isEqualTo(500);
    }

    @Test
    void storedValuesOutsideTheRangeAreBroughtBackInside() {
        node.putInt("dailyReviewLimit", 100_000);
        node.putInt("pomodoroFocusMinutes", 0);

        UiPreferences settings = fresh();

        assertThat(settings.getDailyReviewLimit()).isEqualTo(9999);
        assertThat(settings.getPomodoroFocusMinutes()).isEqualTo(5);
    }

    @Test
    void weekStartIsMondayOrSunday() {
        UiPreferences settings = fresh();

        settings.setWeekStart(DayOfWeek.WEDNESDAY);

        assertThat(settings.getWeekStart()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void anUnknownStoredDayFallsBackToTheDefault() {
        node.put("checkInDay", "FUNDAY");

        assertThat(fresh().getCheckInDay()).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    void aBlankBackupFolderMeansTheDefaultFolder() {
        UiPreferences settings = fresh();
        settings.setBackupFolder("D:\\Backups");

        settings.setBackupFolder("  ");

        assertThat(settings.getBackupFolder()).isEqualTo(UiPreferences.defaultBackupFolder());
    }

    @Test
    void settingsCanBeObserved() {
        UiPreferences settings = fresh();
        AtomicInteger changes = new AtomicInteger();
        settings.dailyNewCardsProperty().addListener((observable, before, after) -> changes.incrementAndGet());
        AtomicInteger languageChanges = new AtomicInteger();
        settings.languageProperty().addListener((observable, before, after) -> languageChanges.incrementAndGet());

        settings.setDailyNewCards(30);
        settings.setLanguage(Language.SPANISH);

        assertThat(changes).hasValue(1);
        assertThat(languageChanges).hasValue(1);
        assertThat(settings.dailyNewCardsProperty().get()).isEqualTo(30);
    }

    @Test
    void theLanguageFollowsTheSavedChoice() {
        node.put("language", "SPANISH");

        assertThat(fresh().getLanguage()).isEqualTo(Language.SPANISH);
    }
}

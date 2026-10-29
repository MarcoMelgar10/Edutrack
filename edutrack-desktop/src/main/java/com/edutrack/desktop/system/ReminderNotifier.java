package com.edutrack.desktop.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Checks once a minute what is due and shows a toast for each item, once per item per day.
 * An item only counts as shown when the toast was really displayed (no tray, no toast, and it is
 * retried on the next check). Clock, source and display are injected so the logic is testable.
 */
public class ReminderNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReminderNotifier.class);

    private final ReminderSource source;
    private final Predicate<Reminder> display;
    private final Clock clock;
    private final BooleanSupplier enabled;
    private final Set<String> shownToday = new HashSet<>();
    private LocalDate shownOn;
    private ScheduledExecutorService scheduler;

    public ReminderNotifier(ReminderSource source, Predicate<Reminder> display, Clock clock, BooleanSupplier enabled) {
        this.source = source;
        this.display = display;
        this.clock = clock;
        this.enabled = enabled;
    }

    /** Runs one check; returns how many toasts were shown. */
    public synchronized int check() {
        if (!enabled.getAsBoolean()) {
            return 0;
        }
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(shownOn)) {
            shownToday.clear();
            shownOn = today;
        }
        int shown = 0;
        try {
            for (Reminder reminder : source.due(today, java.time.LocalTime.now(clock))) {
                if (!shownToday.contains(reminder.key()) && display.test(reminder)) {
                    shownToday.add(reminder.key());
                    shown++;
                }
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not check reminders", failure);
        }
        return shown;
    }

    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "reminder-notifier");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::check, 15, 60, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}

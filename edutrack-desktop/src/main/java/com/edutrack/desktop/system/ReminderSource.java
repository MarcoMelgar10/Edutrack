package com.edutrack.desktop.system;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Where the notifier finds out what is due. Called off the FX thread. */
public interface ReminderSource {

    List<Reminder> due(LocalDate today, LocalTime now);
}

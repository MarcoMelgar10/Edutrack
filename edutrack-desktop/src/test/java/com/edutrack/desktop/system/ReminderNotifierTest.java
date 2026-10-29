package com.edutrack.desktop.system;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderNotifierTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-29T09:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final List<Reminder> due = new ArrayList<>(List.of(new Reminder("a", "t", "A"), new Reminder("b", "t", "B")));
    private final List<String> shown = new ArrayList<>();
    private boolean enabled = true;
    private boolean trayPresent = true;

    private final ReminderNotifier notifier = new ReminderNotifier(
            (LocalDate today, LocalTime now) -> List.copyOf(due),
            reminder -> {
                if (trayPresent) {
                    shown.add(reminder.key());
                }
                return trayPresent;
            }, clock, () -> enabled);

    @Test
    void showsEachItemOnlyOncePerDay() {
        assertThat(notifier.check()).isEqualTo(2);
        assertThat(notifier.check()).isZero();
        due.add(new Reminder("c", "t", "C"));
        assertThat(notifier.check()).isEqualTo(1);
        assertThat(shown).containsExactly("a", "b", "c");
    }

    @Test
    void showsAgainTheNextDay() {
        notifier.check();
        clock.now = clock.now.plusSeconds(24 * 3600);
        assertThat(notifier.check()).isEqualTo(2);
    }

    @Test
    void nothingWhenDisabledAndRetriesWhenNoTray() {
        enabled = false;
        assertThat(notifier.check()).isZero();
        enabled = true;
        trayPresent = false;
        assertThat(notifier.check()).isZero();
        trayPresent = true;
        assertThat(notifier.check()).isEqualTo(2);
    }

    @Test
    void aFailingSourceDoesNotThrow() {
        ReminderNotifier broken = new ReminderNotifier((today, now) -> {
            throw new IllegalStateException("db down");
        }, reminder -> true, clock, () -> true);
        assertThat(broken.check()).isZero();
    }

    @Test
    void startupWritesAndRemovesTheRunValue() {
        List<List<String>> commands = new ArrayList<>();
        boolean[] present = {false};
        RegistryWindowsStartup startup = new RegistryWindowsStartup("HKCU\\Test", command -> {
            commands.add(command);
            if (command.get(1).equals("add")) {
                present[0] = true;
            } else if (command.get(1).equals("delete")) {
                present[0] = false;
            }
            return command.get(1).equals("query") && !present[0] ? 1 : 0;
        }, Optional.of(Path.of("C:\\Apps\\EduTrack\\EduTrack.exe")));

        assertThat(startup.isEnabled()).isFalse();
        startup.setEnabled(true);
        assertThat(commands.get(1)).startsWith("reg", "add", "HKCU\\Test", "/v", "EduTrack");
        assertThat(startup.isEnabled()).isTrue();
        startup.setEnabled(false);
        assertThat(commands.getLast()).containsExactly("reg", "delete", "HKCU\\Test", "/v", "EduTrack", "/f");
        startup.setEnabled(false); // already gone: no second delete
        assertThat(commands.stream().filter(c -> c.get(1).equals("delete"))).hasSize(1);
    }

    @Test
    void startupDoesNothingWithoutALauncher() {
        List<List<String>> commands = new ArrayList<>();
        new RegistryWindowsStartup("HKCU\\Test", command -> {
            commands.add(command);
            return 1;
        }, Optional.empty()).setEnabled(true);
        assertThat(commands).isEmpty();
    }
}

package com.edutrack.desktop.system;

/** One thing worth a desktop toast. {@code key} identifies it, so it is shown at most once per day. */
public record Reminder(String key, String title, String body) {
}

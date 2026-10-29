package com.edutrack.desktop.system;

/** Starting EduTrack when the user signs in to Windows. */
public interface WindowsStartup {

    boolean isEnabled();

    void setEnabled(boolean enabled);
}

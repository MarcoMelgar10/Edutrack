package com.edutrack.desktop.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Writes or removes a value under {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Run} with
 * {@code reg.exe} (per user, no administrator rights). The command runner is injected so tests need no registry.
 */
public class RegistryWindowsStartup implements WindowsStartup {

    public static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    public static final String VALUE_NAME = "EduTrack";

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryWindowsStartup.class);

    /** Runs a command and returns its exit code. */
    public interface CommandRunner extends Function<List<String>, Integer> {
    }

    private final String key;
    private final CommandRunner runner;
    private final Optional<Path> target;

    public RegistryWindowsStartup() {
        this(RUN_KEY, RegistryWindowsStartup::run, findTarget());
    }

    public RegistryWindowsStartup(String key, CommandRunner runner, Optional<Path> target) {
        this.key = key;
        this.runner = runner;
        this.target = target;
    }

    @Override
    public boolean isEnabled() {
        return runner.apply(List.of("reg", "query", key, "/v", VALUE_NAME)) == 0;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled) {
            if (target.isEmpty()) {
                LOGGER.warn("Cannot start with Windows: neither an installed exe nor scripts/run-edutrack.cmd was found");
                return;
            }
            runner.apply(add(target.get()));
        } else if (isEnabled()) {
            runner.apply(List.of("reg", "delete", key, "/v", VALUE_NAME, "/f"));
        }
    }

    List<String> add(Path launcher) {
        // Escaped quotes (checked against reg.exe) make the stored value quoted, so paths with spaces work.
        return new ArrayList<>(List.of("reg", "add", key, "/v", VALUE_NAME, "/t", "REG_SZ",
                "/d", "\\\"" + launcher.toAbsolutePath() + "\\\"", "/f"));
    }

    /** The installed exe (jpackage sets {@code jpackage.app-path}), else {@code scripts\run-edutrack.cmd} above the jar. */
    static Optional<Path> findTarget() {
        String installed = System.getProperty("jpackage.app-path");
        if (installed != null && Files.isRegularFile(Path.of(installed))) {
            return Optional.of(Path.of(installed));
        }
        try {
            Path location = Path.of(RegistryWindowsStartup.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (Path dir = location.toAbsolutePath().getParent(); dir != null; dir = dir.getParent()) {
                Path script = dir.resolve("scripts").resolve("run-edutrack.cmd");
                if (Files.isRegularFile(script)) {
                    return Optional.of(script);
                }
            }
        } catch (Exception unknownLocation) {
            // fall through
        }
        return Optional.empty();
    }

    private static int run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor();
        } catch (IOException failure) {
            return -1;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
}

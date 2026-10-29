package com.edutrack.desktop.ui.goal;

import com.edutrack.application.export.ExportService;
import com.edutrack.desktop.ui.common.ActionResult;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exports a goal's notes, cards or results to a file the learner picked. The view asks for the file
 * name and location; this class builds the text, writes it and turns any failure into a message.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GoalExportViewModel {

    /** What can be exported; the key names the i18n entries for its file name part and dialog title. */
    public enum Kind {
        NOTES("notes", "md"),
        CARDS("cards", "csv"),
        RESULTS("results", "csv");

        private final String key;
        private final String extension;

        Kind(String key, String extension) {
            this.key = key;
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }

        public String key() {
            return key;
        }
    }

    private static final int NAME_MAX_LENGTH = 60;

    private final ExportService exportService;
    private final Messages messages;

    public GoalExportViewModel(ExportService exportService, Messages messages) {
        this.exportService = exportService;
        this.messages = messages;
    }

    /** "IELTS 7.0 - notes.md": the goal's title without characters a file name cannot hold. */
    public String suggestedFileName(Kind kind, String goalTitle) {
        String base = goalTitle == null ? "" : goalTitle.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "-").strip();
        if (base.length() > NAME_MAX_LENGTH) {
            base = base.substring(0, NAME_MAX_LENGTH).strip();
        }
        if (base.replace("-", "").isBlank()) {
            base = messages.get("goal.export.defaultName");
        }
        return base + " - " + messages.get("goal.export.suffix." + kind.key()) + "." + kind.extension();
    }

    public String chooserTitle(Kind kind) {
        return messages.get("goal.export.chooser." + kind.key());
    }

    /** Writes the export to {@code file}. Never throws: a failure comes back as a failed result. */
    public ActionResult export(Kind kind, GoalId goalId, Path file) {
        try {
            String text = switch (kind) {
                case NOTES -> exportService.goalNotesMarkdown(goalId);
                case CARDS -> exportService.cardsCsv(goalId);
                case RESULTS -> exportService.resultsCsv(goalId);
            };
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return ActionResult.ok(messages.get("goal.export.done", file.getFileName()));
        } catch (IOException | RuntimeException failure) {
            return ActionResult.failed(messages.get("goal.export.failed", describe(failure)));
        }
    }

    private String describe(Exception failure) {
        if (failure instanceof IOException io) {
            return io.getMessage() == null ? io.getClass().getSimpleName() : io.getMessage();
        }
        return messages.error(failure);
    }
}

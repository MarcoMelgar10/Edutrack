package com.edutrack.desktop.ui.goal;

import com.edutrack.application.export.ExportService;
import com.edutrack.desktop.ui.common.ActionResult;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.goal.GoalExportViewModel.Kind;
import com.edutrack.domain.goal.GoalId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoalExportViewModelTest {

    private final ExportService exportService = mock(ExportService.class);
    private final GoalExportViewModel viewModel = new GoalExportViewModel(exportService, new Messages());
    private final GoalId goalId = GoalId.newId();

    @TempDir
    Path folder;

    @Test
    void suggestsAFileNameFromTheGoalTitleAndKind() {
        assertThat(viewModel.suggestedFileName(Kind.NOTES, "IELTS 7.0")).isEqualTo("IELTS 7.0 - notes.md");
        assertThat(viewModel.suggestedFileName(Kind.CARDS, "IELTS 7.0")).isEqualTo("IELTS 7.0 - cards.csv");
        assertThat(viewModel.suggestedFileName(Kind.RESULTS, "IELTS 7.0")).isEqualTo("IELTS 7.0 - results.csv");
    }

    @Test
    void removesCharactersAFileNameCannotHold() {
        assertThat(viewModel.suggestedFileName(Kind.NOTES, "A/B: C?")).isEqualTo("A-B- C- - notes.md");
        assertThat(viewModel.suggestedFileName(Kind.NOTES, "???")).isEqualTo("Goal - notes.md");
        assertThat(viewModel.suggestedFileName(Kind.NOTES, null)).isEqualTo("Goal - notes.md");
    }

    @Test
    void writesTheNotesAsUtf8() throws IOException {
        when(exportService.goalNotesMarkdown(goalId)).thenReturn("# Español ñ\n");
        Path file = folder.resolve("notes.md");

        ActionResult result = viewModel.export(Kind.NOTES, goalId, file);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Exported to notes.md.");
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("# Español ñ\n");
    }

    @Test
    void writesCardsAndResultsFromTheirOwnExports() throws IOException {
        when(exportService.cardsCsv(goalId)).thenReturn("front,back\r\n");
        when(exportService.resultsCsv(goalId)).thenReturn("date,title\r\n");

        viewModel.export(Kind.CARDS, goalId, folder.resolve("cards.csv"));
        viewModel.export(Kind.RESULTS, goalId, folder.resolve("results.csv"));

        assertThat(Files.readString(folder.resolve("cards.csv"))).isEqualTo("front,back\r\n");
        assertThat(Files.readString(folder.resolve("results.csv"))).isEqualTo("date,title\r\n");
    }

    @Test
    void reportsAFileThatCannotBeWrittenInsteadOfThrowing() {
        when(exportService.cardsCsv(goalId)).thenReturn("x");
        Path missingFolder = folder.resolve("nope").resolve("cards.csv");

        ActionResult result = viewModel.export(Kind.CARDS, goalId, missingFolder);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).startsWith("Could not export: ");
    }

    @Test
    void reportsAFailureOfTheExportItself() {
        when(exportService.goalNotesMarkdown(goalId)).thenThrow(new IllegalStateException("boom"));

        ActionResult result = viewModel.export(Kind.NOTES, goalId, folder.resolve("notes.md"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Could not export: boom");
        assertThat(folder.resolve("notes.md")).doesNotExist();
    }
}

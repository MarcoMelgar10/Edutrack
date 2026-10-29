package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.template.GoalTemplate;
import com.edutrack.application.template.TemplateService;
import com.edutrack.desktop.ui.common.ActionResult;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplatePickerViewModelTest {

    private final TemplateService templateService = mock(TemplateService.class);
    private final Messages messages = new Messages();
    private final TemplatePickerViewModel viewModel = new TemplatePickerViewModel(templateService, messages);
    private final AreaId areaId = AreaId.newId();

    private static GoalTemplate template(String id, String name, int steps) {
        List<GoalTemplate.Step> list = java.util.stream.IntStream.rangeClosed(1, steps)
                .mapToObj(number -> new GoalTemplate.Step("Step " + number, null, BigDecimal.ONE)).toList();
        return new GoalTemplate(id, name, "About " + name, 8, null, list, null, null, null, null);
    }

    private void listing(GoalTemplate... templates) {
        when(templateService.list(any(Locale.class))).thenReturn(List.of(templates));
    }

    @Test
    void listsTheTemplatesWithTheirStepCounts() {
        listing(template("ielts", "IELTS", 7), template("one", "Single", 1));

        viewModel.create(areaId);

        assertThat(viewModel.items()).extracting(TemplatePickerViewModel.Item::name).containsExactly("IELTS", "Single");
        assertThat(viewModel.items().getFirst().description()).isEqualTo("About IELTS");
        assertThat(viewModel.stepsText(viewModel.items().get(0))).isEqualTo("7 steps");
        assertThat(viewModel.stepsText(viewModel.items().get(1))).isEqualTo("1 step");
        assertThat(viewModel.title()).isEqualTo("New goal from template");
    }

    @Test
    void canCreateOnlyOnceATemplateIsPicked() {
        listing(template("ielts", "IELTS", 7));
        viewModel.create(areaId);
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.selectedProperty().set(viewModel.items().getFirst());

        assertThat(viewModel.validProperty().get()).isTrue();
    }

    @Test
    void submitCreatesTheGoalInTheCurrentArea() {
        listing(template("ielts", "IELTS", 7));
        GoalId created = GoalId.newId();
        when(templateService.apply(eq("ielts"), eq(areaId), eq(null), eq(null), any(Locale.class))).thenReturn(created);
        viewModel.create(areaId);
        viewModel.selectedProperty().set(viewModel.items().getFirst());

        assertThat(viewModel.submit()).isEqualTo(created);
    }

    @Test
    void importingSelectsTheStoredTemplate() {
        GoalTemplate imported = template("mine", "Mine", 2);
        when(templateService.list(any(Locale.class))).thenReturn(List.of(template("ielts", "IELTS", 7)));
        viewModel.create(areaId);
        when(templateService.importFile(Path.of("mine.json"))).thenReturn(imported);
        when(templateService.list(any(Locale.class)))
                .thenReturn(List.of(template("ielts", "IELTS", 7), imported));

        ActionResult result = viewModel.importFile(Path.of("mine.json"));

        assertThat(result.success()).isTrue();
        assertThat(viewModel.statusProperty().get()).isEqualTo("Template “Mine” imported.");
        assertThat(viewModel.items()).hasSize(2);
        assertThat(viewModel.selectedProperty().get().id()).isEqualTo("mine");
    }

    @Test
    void anInvalidTemplateFileIsReportedNotThrown() {
        listing(template("ielts", "IELTS", 7));
        viewModel.create(areaId);
        when(templateService.importFile(any(Path.class)))
                .thenThrow(new DomainValidationException("template-invalid", "Not a template"));

        ActionResult result = viewModel.importFile(Path.of("bad.json"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).startsWith("Could not import the template: ");
        assertThat(viewModel.statusProperty().get()).isEqualTo(result.message());
    }

    @Test
    void exportsThePickedTemplateInTheUiLanguage() {
        listing(template("ielts", "IELTS", 7));
        viewModel.create(areaId);
        viewModel.selectedProperty().set(viewModel.items().getFirst());
        Path file = Path.of("out", "ielts.json");

        ActionResult result = viewModel.exportSelected(file);

        assertThat(result.success()).isTrue();
        assertThat(viewModel.suggestedFileName()).isEqualTo("ielts.json");
        verify(templateService).exportFile("ielts", file, messages.locale());
    }

    @Test
    void exportingWithoutAPickAsksForOne() {
        listing(template("ielts", "IELTS", 7));
        viewModel.create(areaId);

        ActionResult result = viewModel.exportSelected(Path.of("x.json"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Pick a template first.");
    }

    @Test
    void anExportThatFailsIsReported() {
        listing(template("ielts", "IELTS", 7));
        viewModel.create(areaId);
        viewModel.selectedProperty().set(viewModel.items().getFirst());
        org.mockito.Mockito.doThrow(new IllegalStateException("disk full"))
                .when(templateService).exportFile(any(), any(Path.class), any(Locale.class));

        ActionResult result = viewModel.exportSelected(Path.of("x.json"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Could not export the template: disk full");
    }
}

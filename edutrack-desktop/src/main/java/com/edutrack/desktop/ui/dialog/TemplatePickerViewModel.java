package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.template.GoalTemplate;
import com.edutrack.application.template.TemplateService;
import com.edutrack.desktop.ui.common.ActionResult;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * State of the "New goal from template" dialog: the templates in the UI language, the one picked,
 * and importing or exporting template files. Submitting creates the goal in the area.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TemplatePickerViewModel {

    /** One row of the list. */
    public record Item(String id, String name, String description, int stepCount) {
    }

    private final TemplateService templateService;
    private final Messages messages;

    private final ObservableList<Item> items = FXCollections.observableArrayList();
    private final ObjectProperty<Item> selected = new SimpleObjectProperty<>();
    private final StringProperty error = new SimpleStringProperty("");
    private final StringProperty status = new SimpleStringProperty("");
    private final BooleanBinding valid;
    private AreaId areaId;

    public TemplatePickerViewModel(TemplateService templateService, Messages messages) {
        this.templateService = templateService;
        this.messages = messages;
        this.valid = selected.isNotNull();
    }

    /** Create mode for a goal in {@code area}; lists the templates. */
    public void create(AreaId area) {
        this.areaId = area;
        refresh();
    }

    private void refresh() {
        String keep = selected.get() == null ? null : selected.get().id();
        items.setAll(templateService.list(messages.locale()).stream().map(TemplatePickerViewModel::item).toList());
        selected.set(items.stream().filter(item -> item.id().equals(keep)).findFirst().orElse(null));
    }

    private static Item item(GoalTemplate template) {
        return new Item(template.id(), template.name(), template.description() == null ? "" : template.description(),
                template.steps().size());
    }

    public String title() {
        return messages.get("dialog.template.pick.title");
    }

    public String saveLabel() {
        return messages.get("dialog.goal.create");
    }

    /** "7 steps" for the list rows. */
    public String stepsText(Item item) {
        return messages.plural("template.steps", item.stepCount());
    }

    /** Creates the goal from the picked template; domain errors propagate to the dialog. */
    public GoalId submit() {
        error.set("");
        return templateService.apply(selected.get().id(), areaId, null, null, messages.locale());
    }

    /** Suggested file name for exporting the picked template. */
    public String suggestedFileName() {
        return selected.get() == null ? "template.json" : selected.get().id() + ".json";
    }

    /** Reads a template file and adds it to the list; never throws. */
    public ActionResult importFile(Path file) {
        try {
            GoalTemplate stored = templateService.importFile(file);
            refresh();
            items.stream().filter(item -> item.id().equals(stored.id())).findFirst().ifPresent(selected::set);
            return report(ActionResult.ok(messages.get("template.imported", stored.name())));
        } catch (RuntimeException failure) {
            return report(ActionResult.failed(messages.get("template.importFailed", messages.error(failure))));
        }
    }

    /** Writes the picked template, in the UI language, to a file; never throws. */
    public ActionResult exportSelected(Path file) {
        Item item = selected.get();
        if (item == null) {
            return report(ActionResult.failed(messages.get("template.pickFirst")));
        }
        try {
            templateService.exportFile(item.id(), file, messages.locale());
            return report(ActionResult.ok(messages.get("template.exported", file.getFileName())));
        } catch (RuntimeException failure) {
            return report(ActionResult.failed(messages.get("template.exportFailed", messages.error(failure))));
        }
    }

    private ActionResult report(ActionResult result) {
        status.set(result.message());
        return result;
    }

    public ObservableList<Item> items() {
        return items;
    }

    public ObjectProperty<Item> selectedProperty() {
        return selected;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public StringProperty statusProperty() {
        return status;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}

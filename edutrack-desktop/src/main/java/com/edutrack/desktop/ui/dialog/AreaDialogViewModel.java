package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.area.AreaCommands;
import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** State of the Area dialog. Create mode by default; {@link #edit(AreaSummary)} switches to edit mode. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AreaDialogViewModel {

    private final AreaService areaService;
    private final Messages messages;

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final ObjectProperty<AreaIcon> icon = new SimpleObjectProperty<>(AreaIcon.BOOK);
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding valid;
    private AreaId editing;

    public AreaDialogViewModel(AreaService areaService, Messages messages) {
        this.areaService = areaService;
        this.messages = messages;
        this.valid = Bindings.createBooleanBinding(
                () -> name.get() != null && !name.get().isBlank() && icon.get() != null, name, icon);
    }

    public void edit(AreaSummary area) {
        editing = area.id();
        name.set(area.name());
        description.set(area.description() == null ? "" : area.description());
        icon.set(area.icon());
    }

    public String title() {
        return messages.get(editing == null ? "dialog.area.new" : "dialog.area.edit");
    }

    public String saveLabel() {
        return messages.get(editing == null ? "dialog.area.create" : "common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public AreaSummary submit() {
        error.set("");
        return editing == null
                ? areaService.createArea(new AreaCommands.CreateArea(name.get(), description.get(), icon.get()))
                : areaService.updateArea(new AreaCommands.UpdateArea(editing, name.get(), description.get(), icon.get()));
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public ObjectProperty<AreaIcon> iconProperty() {
        return icon;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}

package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.template.GoalTemplate;
import com.edutrack.application.template.TemplateService;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** State of the "Save as template" dialog: the name the plan of a goal is saved under. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SaveTemplateDialogViewModel {

    private final TemplateService templateService;
    private final Messages messages;

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding valid;
    private GoalId goalId;

    public SaveTemplateDialogViewModel(TemplateService templateService, Messages messages) {
        this.templateService = templateService;
        this.messages = messages;
        this.valid = Bindings.createBooleanBinding(() -> name.get() != null && !name.get().isBlank(), name);
    }

    /** Starts from the goal's title. */
    public void create(GoalId goal, String goalTitle) {
        this.goalId = goal;
        name.set(goalTitle == null ? "" : goalTitle);
    }

    public String title() {
        return messages.get("dialog.template.save.title");
    }

    public String saveLabel() {
        return messages.get("dialog.template.save.button");
    }

    public GoalTemplate submit() {
        error.set("");
        return templateService.saveAsTemplate(goalId, name.get());
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}

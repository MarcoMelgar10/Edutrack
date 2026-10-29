package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.record.RecordView;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.record.ExerciseId;
import com.edutrack.domain.record.RecordOwner;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** State of the Exercise dialog, which only renames an existing exercise; new ones are typed inline. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExerciseDialogViewModel {

    private final LearningRecordService records;
    private final Messages messages;

    private final StringProperty exerciseTitle = new SimpleStringProperty("");
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding valid;
    private RecordOwner owner;
    private ExerciseId editing;

    public ExerciseDialogViewModel(LearningRecordService records, Messages messages) {
        this.records = records;
        this.messages = messages;
        this.valid = Bindings.createBooleanBinding(
                () -> exerciseTitle.get() != null && !exerciseTitle.get().isBlank(), exerciseTitle);
    }

    public void edit(RecordOwner owner, RecordView.ExerciseView exercise) {
        this.owner = owner;
        editing = exercise.id();
        exerciseTitle.set(exercise.title());
    }

    public String title() {
        return messages.get("dialog.exercise.edit");
    }

    public String saveLabel() {
        return messages.get("common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public RecordView submit() {
        error.set("");
        return records.renameExercise(owner, editing, exerciseTitle.get());
    }

    public StringProperty exerciseTitleProperty() {
        return exerciseTitle;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}

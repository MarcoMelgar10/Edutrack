package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.record.RecordView;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.ResourceDetails;
import com.edutrack.domain.record.ResourceId;
import com.edutrack.domain.record.ResourceType;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * State of the Resource dialog. Call {@link #create(RecordOwner)} for a new resource of a record, or
 * {@link #edit(RecordOwner, RecordView.ResourceView)} to change an existing one.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ResourceDialogViewModel {

    private final LearningRecordService records;
    private final Messages messages;

    private final StringProperty resourceTitle = new SimpleStringProperty("");
    private final StringProperty url = new SimpleStringProperty("");
    private final ObjectProperty<ResourceType> type = new SimpleObjectProperty<>(ResourceType.ARTICLE);
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding valid;
    private RecordOwner owner;
    private ResourceId editing;

    public ResourceDialogViewModel(LearningRecordService records, Messages messages) {
        this.records = records;
        this.messages = messages;
        this.valid = Bindings.createBooleanBinding(
                () -> resourceTitle.get() != null && !resourceTitle.get().isBlank(), resourceTitle);
    }

    public void create(RecordOwner owner) {
        this.owner = owner;
        this.editing = null;
    }

    public void edit(RecordOwner owner, RecordView.ResourceView resource) {
        this.owner = owner;
        editing = resource.id();
        resourceTitle.set(resource.title());
        url.set(resource.url() == null ? "" : resource.url());
        type.set(resource.type());
    }

    public String title() {
        return messages.get(editing == null ? "dialog.resource.new" : "dialog.resource.edit");
    }

    public String saveLabel() {
        return messages.get(editing == null ? "dialog.resource.create" : "common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public RecordView submit() {
        error.set("");
        ResourceDetails details = new ResourceDetails(resourceTitle.get(), url.get(), type.get());
        return editing == null
                ? records.addResource(owner, details)
                : records.updateResource(owner, editing, details);
    }

    public StringProperty resourceTitleProperty() {
        return resourceTitle;
    }

    public StringProperty urlProperty() {
        return url;
    }

    public ObjectProperty<ResourceType> typeProperty() {
        return type;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}

package com.edutrack.desktop.ui.files;

import com.edutrack.application.attachment.AttachmentService;
import com.edutrack.application.attachment.AttachmentView;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.shared.DomainValidationException;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Subscription;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * State of the Files tab of a step: the files copied into EduTrack for it (a recording, a worksheet, a
 * certificate scan). Adding copies each file; a file that cannot be added (too big, unreadable) is reported
 * with the others still added.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FilesViewModel {

    /** 1 KB = 1024 bytes; the largest unit shown is GB. */
    private static final String[] UNITS = {"B", "KB", "MB", "GB"};

    private final AttachmentService attachmentService;
    private final DataChanges dataChanges;
    private final Messages messages;

    private final ObservableList<AttachmentView> files = FXCollections.observableArrayList();
    private final ObservableList<AttachmentView> filesReadOnly = FXCollections.unmodifiableObservableList(files);
    private final ReadOnlyStringWrapper tabText = new ReadOnlyStringWrapper("");
    private final StringProperty error = new SimpleStringProperty("");
    private StepId stepId;
    private Subscription following = Subscription.EMPTY;

    public FilesViewModel(AttachmentService attachmentService, DataChanges dataChanges, Messages messages) {
        this.attachmentService = attachmentService;
        this.dataChanges = dataChanges;
        this.messages = messages;
        tabText.set(messages.get("files.tab"));
    }

    public void show(StepId step) {
        stepId = step;
        error.set("");
        following.unsubscribe();
        following = dataChanges.subscribe(this::refresh);
        refresh();
    }

    public void clear() {
        following.unsubscribe();
        following = Subscription.EMPTY;
        stepId = null;
        files.clear();
    }

    public void refresh() {
        if (stepId == null) {
            return;
        }
        files.setAll(attachmentService.forOwner(RecordOwner.ofStep(stepId)));
        tabText.set(files.isEmpty() ? messages.get("files.tab") : messages.get("files.tab.count", files.size()));
    }

    /** Copies each file in; problems are collected in {@link #errorProperty()} and do not stop the others. */
    public void add(List<Path> paths) {
        List<String> problems = new java.util.ArrayList<>();
        for (Path path : paths) {
            try {
                attachmentService.add(RecordOwner.ofStep(stepId), path);
            } catch (DomainValidationException rejected) {
                problems.add(path.getFileName() + ": " + messages.error(rejected));
            } catch (RuntimeException unreadable) {
                problems.add(path.getFileName() + ": " + messages.get("files.unreadable"));
            }
        }
        error.set(String.join("\n", problems));
        refresh();
        dataChanges.markChanged();
    }

    public void delete(AttachmentId id) {
        attachmentService.delete(id);
        error.set("");
        refresh();
        dataChanges.markChanged();
    }

    public Path pathOf(AttachmentId id) {
        return attachmentService.pathOf(id);
    }

    /** 1536 → "1.5 KB". */
    public static String size(long bytes) {
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return unit == 0 ? bytes + " B" : String.format(java.util.Locale.ROOT, "%.1f %s", value, UNITS[unit]);
    }

    public ObservableList<AttachmentView> files() {
        return filesReadOnly;
    }

    public ReadOnlyStringProperty tabTextProperty() {
        return tabText.getReadOnlyProperty();
    }

    /** What could not be added, one per line; empty when everything went in. */
    public StringProperty errorProperty() {
        return error;
    }
}

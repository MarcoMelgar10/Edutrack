package com.edutrack.desktop.ui.evidence;

import com.edutrack.application.attachment.AttachmentService;
import com.edutrack.application.evidence.EvidenceService;
import com.edutrack.application.evidence.EvidenceView;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.session.SessionFormats;
import com.edutrack.domain.evidence.EvidenceId;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Subscription;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * State of the Evidence tab of a goal: the portfolio of what proves the learning, newest first. Follows the
 * data while a goal is shown.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EvidencePanelViewModel {

    private final EvidenceService evidenceService;
    private final AttachmentService attachmentService;
    private final DataChanges dataChanges;
    private final Messages messages;

    private final ObservableList<EvidenceView> items = FXCollections.observableArrayList();
    private final ObservableList<EvidenceView> itemsReadOnly = FXCollections.unmodifiableObservableList(items);
    private final ReadOnlyStringWrapper tabText = new ReadOnlyStringWrapper("");
    private GoalDetails goal;
    private Subscription following = Subscription.EMPTY;

    public EvidencePanelViewModel(EvidenceService evidenceService, AttachmentService attachmentService,
                                  DataChanges dataChanges, Messages messages) {
        this.evidenceService = evidenceService;
        this.attachmentService = attachmentService;
        this.dataChanges = dataChanges;
        this.messages = messages;
        tabText.set(messages.get("evidence.tab"));
    }

    public void show(GoalDetails shown) {
        goal = shown;
        following.unsubscribe();
        following = dataChanges.subscribe(this::refresh);
        refresh();
    }

    public void clear() {
        following.unsubscribe();
        following = Subscription.EMPTY;
        goal = null;
    }

    public void refresh() {
        if (goal == null) {
            return;
        }
        items.setAll(evidenceService.forGoal(goal.id()));
        tabText.set(items.isEmpty() ? messages.get("evidence.tab") : messages.get("evidence.tab.count", items.size()));
    }

    public void delete(EvidenceId id) {
        evidenceService.delete(id);
        dataChanges.markChanged();
    }

    /** The stored file of a piece of evidence, ready to open; {@code null} when it has none. */
    public Path fileOf(EvidenceView evidence) {
        return evidence.attachment() == null ? null : attachmentService.pathOf(evidence.attachment().id());
    }

    /** "28 Oct 2026 · Step 3 title". */
    public String captionOf(EvidenceView evidence) {
        String date = SessionFormats.dayMonth(evidence.takenOn()) + " " + evidence.takenOn().getYear();
        return evidence.stepTitle() == null ? date : date + " · " + evidence.stepTitle();
    }

    public GoalDetails goal() {
        return goal;
    }

    public ObservableList<EvidenceView> items() {
        return itemsReadOnly;
    }

    public ReadOnlyStringProperty tabTextProperty() {
        return tabText.getReadOnlyProperty();
    }
}

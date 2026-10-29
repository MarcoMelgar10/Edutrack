package com.edutrack.desktop.ui.evidence;

import com.edutrack.application.evidence.EvidenceService;
import com.edutrack.application.evidence.EvidenceView;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.skill.CompetencyService;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.session.SessionChoices;
import com.edutrack.desktop.ui.session.StepChoice;
import com.edutrack.domain.evidence.EvidenceDetails;
import com.edutrack.domain.evidence.EvidenceKind;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.skill.CompetencyId;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * State of the evidence dialog: a piece of proof of what was learned (a project, a recording, an essay, a
 * certificate…) with a kind, a title, a date, an optional step, a link and/or a file, the competency it shows
 * and a note. A file is copied into EduTrack's own folder when the evidence is saved.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EvidenceDialogViewModel {

    /** An entry of the kind or competency box. */
    public record Choice<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final EvidenceService evidenceService;
    private final GoalService goalService;
    private final CompetencyService competencyService;
    private final Clock clock;
    private final Messages messages;

    private final ObservableList<Choice<EvidenceKind>> kinds;
    private final ObservableList<StepChoice> steps = FXCollections.observableArrayList();
    private final ObservableList<Choice<CompetencyId>> competencies = FXCollections.observableArrayList();
    private final ObjectProperty<Choice<EvidenceKind>> kind = new SimpleObjectProperty<>();
    private final StringProperty title = new SimpleStringProperty("");
    private final ObjectProperty<LocalDate> takenOn = new SimpleObjectProperty<>();
    private final ObjectProperty<StepChoice> step = new SimpleObjectProperty<>();
    private final StringProperty url = new SimpleStringProperty("");
    private final ObjectProperty<Choice<CompetencyId>> competency = new SimpleObjectProperty<>();
    private final StringProperty note = new SimpleStringProperty("");
    private final StringProperty fileName = new SimpleStringProperty("");
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding valid;

    private GoalId goalId;
    private EvidenceView editing;
    private Path newFile;
    private boolean removeFile;

    public EvidenceDialogViewModel(EvidenceService evidenceService, GoalService goalService,
                                   CompetencyService competencyService, Clock clock, Messages messages) {
        this.evidenceService = evidenceService;
        this.goalService = goalService;
        this.competencyService = competencyService;
        this.clock = clock;
        this.messages = messages;
        List<Choice<EvidenceKind>> choices = new ArrayList<>();
        for (EvidenceKind each : EvidenceKind.values()) {
            choices.add(new Choice<>(each, messages.get("evidence.kind." + each.name())));
        }
        this.kinds = FXCollections.observableArrayList(choices);
        kind.set(kinds.getFirst());
        this.valid = Bindings.createBooleanBinding(() -> !title.get().isBlank() && takenOn.get() != null, title, takenOn);
    }

    /** New evidence for the goal; {@code stepId} preselects the step when given. */
    public void create(GoalId goal, StepId stepId) {
        goalId = goal;
        editing = null;
        takenOn.set(LocalDate.now(clock));
        load(stepId, null);
    }

    public void edit(EvidenceView evidence) {
        goalId = evidence.goalId();
        editing = evidence;
        load(evidence.stepId(), evidence.competencyId());
        kind.set(kinds.stream().filter(choice -> choice.value() == evidence.kind()).findFirst().orElseThrow());
        title.set(evidence.title());
        takenOn.set(evidence.takenOn());
        url.set(evidence.url() == null ? "" : evidence.url());
        note.set(evidence.note() == null ? "" : evidence.note());
        fileName.set(evidence.attachment() == null ? "" : evidence.attachment().name());
    }

    private void load(StepId selectedStep, CompetencyId selectedCompetency) {
        steps.setAll(SessionChoices.steps(goalService, goalId, messages));
        step.set(steps.stream().filter(choice -> Objects.equals(choice.id(), selectedStep)).findFirst()
                .orElse(steps.getFirst()));
        List<Choice<CompetencyId>> choices = new ArrayList<>();
        choices.add(new Choice<>(null, messages.get("evidence.competency.none")));
        competencyService.forGoal(goalId).forEach(each -> choices.add(new Choice<>(each.id(), each.name())));
        competencies.setAll(choices);
        competency.set(choices.stream().filter(choice -> Objects.equals(choice.value(), selectedCompetency)).findFirst()
                .orElse(choices.getFirst()));
    }

    // ---- the file ----

    /** The file to copy in when saving. */
    public void chooseFile(Path file) {
        newFile = file;
        removeFile = false;
        fileName.set(file.getFileName().toString());
    }

    public void removeChosenFile() {
        newFile = null;
        removeFile = editing != null && editing.attachment() != null;
        fileName.set("");
    }

    public String title() {
        return messages.get(editing == null ? "dialog.evidence.new" : "dialog.evidence.edit");
    }

    public String saveLabel() {
        return messages.get(editing == null ? "dialog.evidence.create" : "common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public EvidenceView submit() {
        error.set("");
        EvidenceDetails details = new EvidenceDetails(step.get() == null ? null : step.get().id(), kind.get().value(),
                title.get(), takenOn.get(), url.get(), note.get(),
                competency.get() == null ? null : competency.get().value());
        if (editing == null) {
            return evidenceService.add(goalId, details, newFile);
        }
        EvidenceView saved = evidenceService.update(editing.id(), details);
        if (newFile != null) {
            return evidenceService.replaceFile(editing.id(), newFile);
        }
        if (removeFile) {
            return evidenceService.removeFile(editing.id());
        }
        return saved;
    }

    public ObservableList<Choice<EvidenceKind>> kinds() {
        return kinds;
    }

    public ObservableList<StepChoice> steps() {
        return steps;
    }

    public ObservableList<Choice<CompetencyId>> competencies() {
        return competencies;
    }

    public ObjectProperty<Choice<EvidenceKind>> kindProperty() {
        return kind;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public ObjectProperty<LocalDate> takenOnProperty() {
        return takenOn;
    }

    public ObjectProperty<StepChoice> stepProperty() {
        return step;
    }

    public StringProperty urlProperty() {
        return url;
    }

    public ObjectProperty<Choice<CompetencyId>> competencyProperty() {
        return competency;
    }

    public StringProperty noteProperty() {
        return note;
    }

    /** The name of the file that will be kept, or empty. */
    public StringProperty fileNameProperty() {
        return fileName;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public BooleanBinding validProperty() {
        return valid;
    }

    /** The last day evidence can be dated. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }
}

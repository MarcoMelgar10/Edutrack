package com.edutrack.desktop.ui.review;

import com.edutrack.application.review.ReviewScope;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.markdown.MarkdownView;
import com.edutrack.desktop.ui.review.ReviewSessionViewModel.Phase;
import com.edutrack.desktop.ui.shell.Navigator;
import com.edutrack.desktop.ui.shell.PageLifecycle;
import com.edutrack.desktop.ui.theme.Motion;
import com.edutrack.domain.review.Grade;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Binds {@code ReviewView.fxml} to {@link ReviewSessionViewModel}: the start page (subject, counts, Start),
 * the card with its four grade buttons, and the summary.
 *
 * <p>Keyboard: Enter starts, Space (or Enter) shows the answer, 1–4 grade it as Again / Hard / Good / Easy.
 * The keys are read on the page as a whole, so they work whichever button has the focus.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ReviewController implements PageLifecycle {

    private final ReviewSessionViewModel viewModel;
    private final Navigator navigator;
    private final HostServices hostServices;
    private final Messages messages;

    @FXML
    private StackPane root;
    @FXML
    private ScrollPane startPane;
    @FXML
    private ComboBox<ReviewSessionViewModel.ScopeOption> scopeBox;
    @FXML
    private VBox waitingBox;
    @FXML
    private VBox emptyBox;
    @FXML
    private Label countsLabel;
    @FXML
    private Label limitsLabel;
    @FXML
    private Label nextDueLabel;
    @FXML
    private Label limitsEmptyLabel;
    @FXML
    private Button startButton;

    @FXML
    private VBox sessionPane;
    @FXML
    private Label progressLabel;
    @FXML
    private Label captionLabel;
    @FXML
    private ProgressBar sessionProgress;
    @FXML
    private VBox reviewCard;
    @FXML
    private Label newLabel;
    @FXML
    private MarkdownView promptView;
    @FXML
    private Separator answerSeparator;
    @FXML
    private MarkdownView answerView;
    @FXML
    private Button showAnswerButton;
    @FXML
    private HBox gradeBox;
    @FXML
    private Button againButton;
    @FXML
    private Button hardButton;
    @FXML
    private Button goodButton;
    @FXML
    private Button easyButton;
    @FXML
    private Label againInterval;
    @FXML
    private Label hardInterval;
    @FXML
    private Label goodInterval;
    @FXML
    private Label easyInterval;

    @FXML
    private ScrollPane summaryPane;
    @FXML
    private Label cardsValue;
    @FXML
    private Label againValue;
    @FXML
    private Label accuracyValue;
    @FXML
    private Label timeValue;
    @FXML
    private Label logLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Button logButton;
    @FXML
    private Button backButton;

    public ReviewController(ReviewSessionViewModel viewModel, Navigator navigator, HostServices hostServices,
                            Messages messages) {
        this.viewModel = viewModel;
        this.navigator = navigator;
        this.hostServices = hostServices;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        initializeStart();
        initializeSession();
        initializeSummary();

        viewModel.phaseProperty().subscribe(this::onPhase);
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        sessionPane.setFocusTraversable(true);
    }

    private void initializeStart() {
        scopeBox.setItems(viewModel.scopes());
        scopeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ReviewSessionViewModel.ScopeOption option) {
                return option == null ? "" : option.label();
            }

            @Override
            public ReviewSessionViewModel.ScopeOption fromString(String text) {
                return null; // not editable
            }
        });
        scopeBox.valueProperty().bindBidirectional(viewModel.scopeProperty());

        countsLabel.textProperty().bind(viewModel.countsTextProperty());
        limitsLabel.textProperty().bind(viewModel.limitsTextProperty());
        limitsEmptyLabel.textProperty().bind(viewModel.limitsTextProperty());
        nextDueLabel.textProperty().bind(viewModel.nextDueTextProperty());
        nextDueLabel.visibleProperty().bind(viewModel.nextDueTextProperty().isNotEmpty());
        nextDueLabel.managedProperty().bind(nextDueLabel.visibleProperty());

        emptyBox.visibleProperty().bind(viewModel.nothingToReviewProperty());
        emptyBox.managedProperty().bind(emptyBox.visibleProperty());
        waitingBox.visibleProperty().bind(viewModel.nothingToReviewProperty().not());
        waitingBox.managedProperty().bind(waitingBox.visibleProperty());
    }

    private void initializeSession() {
        progressLabel.textProperty().bind(viewModel.progressTextProperty());
        captionLabel.textProperty().bind(viewModel.captionTextProperty());
        viewModel.progressProperty().subscribe(progress -> {
            // A new session starts again from 0 at once; within a session the bar glides forward.
            if (progress.doubleValue() < sessionProgress.getProgress()) {
                Motion.setProgress(sessionProgress, progress.doubleValue());
            } else {
                Motion.animateProgress(sessionProgress, progress.doubleValue());
            }
        });

        newLabel.visibleProperty().bind(viewModel.newCardProperty());
        newLabel.managedProperty().bind(newLabel.visibleProperty());

        promptView.setOnLink(this::openLink);
        answerView.setOnLink(this::openLink);
        viewModel.promptProperty().subscribe(promptView::setMarkdown);
        viewModel.answerProperty().subscribe(answerView::setMarkdown);

        bindInterval(againInterval, againButton, "review.grade.again", Grade.AGAIN);
        bindInterval(hardInterval, hardButton, "review.grade.hard", Grade.HARD);
        bindInterval(goodInterval, goodButton, "review.grade.good", Grade.GOOD);
        bindInterval(easyInterval, easyButton, "review.grade.easy", Grade.EASY);
    }

    private void bindInterval(Label caption, Button button, String nameKey, Grade grade) {
        ReadOnlyStringProperty interval = viewModel.intervalTextProperty(grade);
        caption.textProperty().bind(interval);
        interval.subscribe(text -> button.setAccessibleText(messages.get(nameKey) + ", " + text));
    }

    private void initializeSummary() {
        cardsValue.textProperty().bind(viewModel.cardsTextProperty());
        againValue.textProperty().bind(viewModel.againTextProperty());
        accuracyValue.textProperty().bind(viewModel.accuracyTextProperty());
        timeValue.textProperty().bind(viewModel.timeTextProperty());
        logLabel.textProperty().bind(viewModel.logTextProperty());
        logLabel.visibleProperty().bind(viewModel.logTextProperty().isNotEmpty());
        logLabel.managedProperty().bind(logLabel.visibleProperty());
        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        logButton.visibleProperty().bind(viewModel.canLogTimeProperty());
        logButton.managedProperty().bind(logButton.visibleProperty());
    }

    // ---------- page lifecycle ----------

    @Override
    public void shown() {
        ReviewScope requested = navigator.takeReviewScope().orElse(null);
        if (requested != null) {
            viewModel.show(requested);
        } else {
            viewModel.refresh();
        }
    }

    // ---------- phases and keys ----------

    /** Shows the pane of the phase and puts the focus where the next key press is expected. */
    private void onPhase(Phase phase) {
        show(startPane, phase == Phase.START);
        show(sessionPane, phase == Phase.QUESTION || phase == Phase.ANSWER);
        show(summaryPane, phase == Phase.SUMMARY);
        show(showAnswerButton, phase == Phase.QUESTION);
        show(gradeBox, phase == Phase.ANSWER);
        show(answerSeparator, phase == Phase.ANSWER);
        show(answerView, phase == Phase.ANSWER);
        if (phase == Phase.QUESTION) {
            Motion.fadeIn(reviewCard);
        }
        Platform.runLater(() -> focusFor(phase));
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void focusFor(Phase phase) {
        if (root.getScene() == null) {
            return;
        }
        switch (phase) {
            case START -> (viewModel.nothingToReviewProperty().get() ? root : startButton).requestFocus();
            case QUESTION -> showAnswerButton.requestFocus();
            case ANSWER -> sessionPane.requestFocus();
            case SUMMARY -> (logButton.isVisible() ? logButton : backButton).requestFocus();
        }
    }

    private static final Map<KeyCode, Grade> GRADE_KEYS = Map.ofEntries(
            Map.entry(KeyCode.DIGIT1, Grade.AGAIN), Map.entry(KeyCode.NUMPAD1, Grade.AGAIN),
            Map.entry(KeyCode.DIGIT2, Grade.HARD), Map.entry(KeyCode.NUMPAD2, Grade.HARD),
            Map.entry(KeyCode.DIGIT3, Grade.GOOD), Map.entry(KeyCode.NUMPAD3, Grade.GOOD),
            Map.entry(KeyCode.DIGIT4, Grade.EASY), Map.entry(KeyCode.NUMPAD4, Grade.EASY));

    private void onKey(KeyEvent key) {
        if (key.isControlDown() || key.isAltDown() || key.isMetaDown() || key.isShiftDown()) {
            return; // Ctrl+1…5 switch pages; leave every combination to the window
        }
        KeyCode code = key.getCode();
        switch (viewModel.phaseProperty().get()) {
            case START -> {
                if (code == KeyCode.ENTER && !scopeBox.isShowing() && !viewModel.nothingToReviewProperty().get()) {
                    start();
                    key.consume();
                }
            }
            case QUESTION -> {
                // Space or Enter on another button (End session) keeps its own meaning.
                boolean onOtherButton = root.getScene() != null
                        && root.getScene().getFocusOwner() instanceof Button focused && focused != showAnswerButton;
                if ((code == KeyCode.SPACE || code == KeyCode.ENTER) && !onOtherButton) {
                    reveal();
                    key.consume();
                }
            }
            case ANSWER -> {
                Grade grade = GRADE_KEYS.get(code);
                if (grade != null) {
                    viewModel.grade(grade);
                    key.consume();
                }
            }
            case SUMMARY -> { }
        }
    }

    // ---------- actions ----------

    @FXML
    private void start() {
        viewModel.start();
    }

    @FXML
    private void reveal() {
        viewModel.reveal();
    }

    @FXML
    private void again() {
        viewModel.grade(Grade.AGAIN);
    }

    @FXML
    private void hard() {
        viewModel.grade(Grade.HARD);
    }

    @FXML
    private void good() {
        viewModel.grade(Grade.GOOD);
    }

    @FXML
    private void easy() {
        viewModel.grade(Grade.EASY);
    }

    @FXML
    private void endSession() {
        viewModel.endSession();
    }

    @FXML
    private void logTime() {
        viewModel.logAsStudyTime();
    }

    @FXML
    private void back() {
        viewModel.backToStart();
    }

    /** Only web and mail links leave the app. */
    private void openLink(String destination) {
        if (destination != null && destination.matches("(?i)(https?://|mailto:).+")) {
            hostServices.showDocument(destination);
        }
    }
}

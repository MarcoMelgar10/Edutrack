package com.edutrack.desktop.ui.dialog;

import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.application.measure.MeasureService;
import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.record.RecordView;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.record.AssessmentDetails;
import com.edutrack.domain.record.AssessmentId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultDialogViewModelTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    private final LearningRecordService records = mock(LearningRecordService.class);
    private final MeasureService measures = mock(MeasureService.class);
    private final ResultDialogViewModel viewModel = new ResultDialogViewModel(records, measures, clock, new Messages());
    private final RecordOwner owner = RecordOwner.ofGoal(GoalId.newId());
    private final RecordView refreshed =
            new RecordView(owner, "", null, List.of(), List.of(), List.of(), RecordSummary.EMPTY);

    private void sentOnCreate() {
        when(records.addResult(any(), any())).thenReturn(refreshed);
        viewModel.create(owner);
        viewModel.resultTitleProperty().set("IELTS mock test");
    }

    private AssessmentDetails submitAndCapture() {
        viewModel.submit();
        ArgumentCaptor<AssessmentDetails> sent = ArgumentCaptor.forClass(AssessmentDetails.class);
        verify(records).addResult(eq(owner), sent.capture());
        return sent.getValue();
    }

    @Test
    void startsInCreateModeDatedToday() {
        viewModel.create(owner);

        assertThat(viewModel.title()).isEqualTo("New result");
        assertThat(viewModel.saveLabel()).isEqualTo("Add result");
        assertThat(viewModel.takenOnProperty().get()).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    @Test
    void needsATitleAndAScore() {
        viewModel.create(owner);
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.resultTitleProperty().set("IELTS mock test");
        assertThat(viewModel.validProperty().get()).as("title only").isFalse();

        viewModel.scoreProperty().set("6.5");
        assertThat(viewModel.validProperty().get()).isTrue();

        viewModel.resultTitleProperty().set("  ");
        assertThat(viewModel.validProperty().get()).as("blank title").isFalse();
    }

    @Test
    void scoresAcceptACommaOrADot() {
        sentOnCreate();
        viewModel.scoreProperty().set("6,5");
        viewModel.maxScoreProperty().set("9.0");

        AssessmentDetails sent = submitAndCapture();

        assertThat(sent.title()).isEqualTo("IELTS mock test");
        assertThat(sent.score()).isEqualByComparingTo("6.5");
        assertThat(sent.maxScore()).isEqualByComparingTo("9");
        assertThat(sent.takenOn()).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    @Test
    void theMaximumIsOptional() {
        sentOnCreate();
        viewModel.scoreProperty().set("85");
        viewModel.maxScoreProperty().set("   ");

        assertThat(viewModel.scoreValidProperty().get()).isTrue();
        assertThat(viewModel.validProperty().get()).isTrue();
        assertThat(submitAndCapture().maxScore()).isNull();
    }

    @Test
    void textThatIsNotANumberIsInvalid() {
        viewModel.create(owner);
        viewModel.resultTitleProperty().set("IELTS mock test");

        for (String typed : List.of("abc", "6.5.1", "-1", "1e3", "6 5")) {
            viewModel.scoreProperty().set(typed);
            assertThat(viewModel.scoreValidProperty().get()).as("score " + typed).isFalse();
            assertThat(viewModel.validProperty().get()).as("score " + typed).isFalse();
        }

        viewModel.scoreProperty().set("6.5");
        viewModel.maxScoreProperty().set("nine");
        assertThat(viewModel.scoreValidProperty().get()).as("max nine").isFalse();
        assertThat(viewModel.validProperty().get()).isFalse();
    }

    @Test
    void submitRefusesAnInvalidScoreWithTheHint() {
        viewModel.create(owner);
        viewModel.resultTitleProperty().set("Mock");
        viewModel.scoreProperty().set("abc");

        assertThatThrownBy(viewModel::submit).isInstanceOf(DomainValidationException.class)
                .hasMessage("Enter scores as numbers, e.g. 6.5");
        verify(records, never()).addResult(any(), any());
    }

    @Test
    void theNoteIsSentAsTyped() {
        sentOnCreate();
        viewModel.scoreProperty().set("7");
        viewModel.noteProperty().set("Certificate ID 123");

        assertThat(submitAndCapture().note()).isEqualTo("Certificate ID 123");
    }

    @Test
    void editPrefillsEveryFieldAndSubmitsAnUpdate() {
        AssessmentId id = AssessmentId.newId();
        when(records.updateResult(any(), any(), any())).thenReturn(refreshed);
        viewModel.edit(owner, new RecordView.ResultView(id, "Final exam", new BigDecimal("6.50"), new BigDecimal("9"),
                LocalDate.of(2026, 10, 12), "Retake in spring", new Progress(new BigDecimal("72.22"))));

        assertThat(viewModel.title()).isEqualTo("Edit result");
        assertThat(viewModel.saveLabel()).isEqualTo("Save changes");
        assertThat(viewModel.resultTitleProperty().get()).isEqualTo("Final exam");
        assertThat(viewModel.scoreProperty().get()).isEqualTo("6.5");
        assertThat(viewModel.maxScoreProperty().get()).isEqualTo("9");
        assertThat(viewModel.takenOnProperty().get()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(viewModel.noteProperty().get()).isEqualTo("Retake in spring");
        assertThat(viewModel.validProperty().get()).isTrue();

        viewModel.scoreProperty().set("7");
        viewModel.submit();

        ArgumentCaptor<AssessmentDetails> sent = ArgumentCaptor.forClass(AssessmentDetails.class);
        verify(records).updateResult(eq(owner), eq(id), sent.capture());
        assertThat(sent.getValue().score()).isEqualByComparingTo("7");
        assertThat(sent.getValue().takenOn()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    void editShowsMissingValuesAsEmptyFields() {
        viewModel.edit(owner, new RecordView.ResultView(AssessmentId.newId(), "Certificate", new BigDecimal("85"),
                null, LocalDate.of(2026, 10, 12), null, null));

        assertThat(viewModel.maxScoreProperty().get()).isEmpty();
        assertThat(viewModel.noteProperty().get()).isEmpty();
    }

    @Test
    void aMissingDateIsLeftToTheDomainToReject() {
        when(records.addResult(any(), any())).thenThrow(new DomainValidationException("Date is required."));
        viewModel.create(owner);
        viewModel.resultTitleProperty().set("Mock");
        viewModel.scoreProperty().set("6");
        viewModel.takenOnProperty().set(null);

        assertThatThrownBy(viewModel::submit).isInstanceOf(DomainValidationException.class)
                .hasMessage("Date is required.");
    }
}

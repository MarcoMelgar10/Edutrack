package com.edutrack.desktop.ui.dialog;

import com.edutrack.domain.record.RecordSummary;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.common.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteConsequencesTest {

    @Test
    void aStepWithNothingRecordedHasNoConsequence() {
        assertThat(DeleteConsequences.ofStep(RecordSummary.EMPTY)).isNull();
    }

    @Test
    void aStepNamesEverythingItsRecordHolds() {
        RecordSummary record = new RecordSummary(true, 2, 1, 3, 1);

        assertThat(DeleteConsequences.ofStep(record))
                .isEqualTo("Its notes, 2 resources, 3 exercises and 1 result will be deleted too.");
    }

    @Test
    void singleItemsReadAsOneWord() {
        assertThat(DeleteConsequences.ofStep(new RecordSummary(true, 0, 0, 0, 0)))
                .isEqualTo("Its notes will be deleted too.");
        assertThat(DeleteConsequences.ofStep(new RecordSummary(false, 1, 0, 0, 0)))
                .isEqualTo("Its 1 resource will be deleted too.");
    }

    @Test
    void anAreaNamesItsGoals() {
        assertThat(DeleteConsequences.ofArea(0)).isNull();
        assertThat(DeleteConsequences.ofArea(1)).isEqualTo("Its 1 goal and its roadmap will be deleted too.");
        assertThat(DeleteConsequences.ofArea(3)).isEqualTo("Its 3 goals and their roadmaps will be deleted too.");
    }

    @Test
    void theSentenceIsTranslated() {
        I18n.use(Language.SPANISH);
        try {
            assertThat(DeleteConsequences.ofStep(new RecordSummary(true, 2, 0, 0, 1)))
                    .isEqualTo("También se eliminará: las notas, 2 recursos y 1 resultado.");
        } finally {
            I18n.use(Language.ENGLISH);
        }
    }

    @Test
    void aGoalNamesItsStepsAndEverythingRecorded() {
        RecordSummary steps = new RecordSummary(true, 4, 2, 3, 5);
        RecordSummary goal = new RecordSummary(false, 0, 0, 0, 1);

        assertThat(DeleteConsequences.ofGoal(9, steps.plus(goal)))
                .isEqualTo("Its 9 roadmap steps, notes, 4 resources, 3 exercises and 6 results will be deleted too.");
        assertThat(DeleteConsequences.ofGoal(1, RecordSummary.EMPTY))
                .isEqualTo("Its 1 roadmap step will be deleted too.");
        assertThat(DeleteConsequences.ofGoal(0, RecordSummary.EMPTY)).isNull();
    }
}

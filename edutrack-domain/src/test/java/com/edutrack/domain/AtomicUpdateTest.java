package com.edutrack.domain;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A rejected update must leave the object exactly as it was. */
class AtomicUpdateTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    @Test
    void rejectedAreaUpdateChangesNothing() {
        Area area = Area.create("English", "Old", AreaIcon.GLOBE, 0, NOW);

        assertThatThrownBy(() -> area.update("Languages", "New", null)).isInstanceOf(DomainValidationException.class);

        assertThat(area.name()).isEqualTo("English");
        assertThat(area.description()).isEqualTo("Old");
    }

    @Test
    void rejectedGoalUpdateChangesNothing() {
        Goal goal = Goal.create(AreaId.newId(), "IELTS 7.0", "Old", LocalDate.of(2027, 3, 1), 0, NOW);

        assertThatThrownBy(() -> goal.update("IELTS 8.0", "x".repeat(2001), null))
                .isInstanceOf(DomainValidationException.class);

        assertThat(goal.title()).isEqualTo("IELTS 7.0");
        assertThat(goal.deadline()).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    void rejectedStepUpdateChangesNothing() {
        Goal goal = Goal.create(AreaId.newId(), "Goal", null, null, 0, NOW);
        RoadmapStep step = goal.addStep(new StepDetails("Grammar", null, BigDecimal.TEN),
                StepStatus.NOT_STARTED, NOW);

        assertThatThrownBy(() -> goal.updateStep(step.id(),
                new StepDetails("Listening", "New", new BigDecimal("-1"))))
                .isInstanceOf(DomainValidationException.class);

        assertThat(step.title()).isEqualTo("Grammar");
        assertThat(step.description()).isNull();
        assertThat(step.estimatedHours()).isEqualByComparingTo("10");
    }
}

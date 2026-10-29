package com.edutrack.domain.area;

import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AreaTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private static Area area(String name, String description, AreaIcon icon, int position) {
        return Area.create(name, description, icon, position, NOW);
    }

    private static Area area(String name) {
        return area(name, null, AreaIcon.BOOK, 0);
    }

    @Test
    void createKeepsTheGivenValues() {
        Area area = area("English", "Grammar", AreaIcon.GLOBE, 2);

        assertThat(area.name()).isEqualTo("English");
        assertThat(area.description()).isEqualTo("Grammar");
        assertThat(area.icon()).isEqualTo(AreaIcon.GLOBE);
        assertThat(area.position()).isEqualTo(2);
        assertThat(area.createdAt()).isEqualTo(NOW);
    }

    @Test
    void nameIsRequired() {
        assertThatThrownBy(() -> area(null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Name is required.");
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> area("   "))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Name is required.");
    }

    @Test
    void nameIsStripped() {
        assertThat(area("  English  ").name()).isEqualTo("English");
    }

    @Test
    void nameOf80CharactersIsAccepted() {
        assertThat(area("a".repeat(80)).name()).hasSize(80);
    }

    @Test
    void nameOf81CharactersIsRejected() {
        assertThatThrownBy(() -> area("a".repeat(81)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Name must be at most 80 characters.");
    }

    @Test
    void iconIsRequired() {
        assertThatThrownBy(() -> area("English", null, null, 0))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Icon is required.");
    }

    @Test
    void blankDescriptionBecomesNull() {
        assertThat(area("English", "   ", AreaIcon.GLOBE, 0).description()).isNull();
    }

    @Test
    void negativePositionIsRejected() {
        assertThatThrownBy(() -> area("English", null, AreaIcon.GLOBE, -1))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Position cannot be negative.");
    }

    @Test
    void updateReplacesNameDescriptionAndIcon() {
        Area area = area("English", "Old", AreaIcon.GLOBE, 0);

        area.update("Technologies", "New", AreaIcon.CODE);

        assertThat(area.name()).isEqualTo("Technologies");
        assertThat(area.description()).isEqualTo("New");
        assertThat(area.icon()).isEqualTo(AreaIcon.CODE);
    }

    @Test
    void areasWithTheSameIdAreEqual() {
        AreaId id = AreaId.newId();
        Area first = Area.restore(id, "English", null, AreaIcon.GLOBE, 0, NOW);
        Area renamed = Area.restore(id, "Technologies", null, AreaIcon.CODE, 3, NOW);

        assertThat(first).isEqualTo(renamed).hasSameHashCodeAs(renamed);
    }

    @Test
    void areasWithDifferentIdsAreNotEqual() {
        assertThat(area("English")).isNotEqualTo(area("English"));
    }
}

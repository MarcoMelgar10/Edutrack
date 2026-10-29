package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class})
class JpaAreaRepositoryAdapterIT {

    /** Microsecond precision, which is what PostgreSQL stores, so round-trips compare exactly. */
    private static final Instant NOW = Instant.parse("2026-03-01T09:30:15.123456Z");

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndLoadsAllFieldsOfAnArea() {
        Area area = Area.create("English", "Speak and write with confidence", AreaIcon.GLOBE, 3, NOW);

        Area saved = areas.save(area);
        flushAndClear();
        Area loaded = areas.findById(area.id()).orElseThrow();

        assertThat(saved).usingRecursiveComparison().isEqualTo(area);
        assertThat(loaded).usingRecursiveComparison().isEqualTo(area);
        assertThat(loaded.name()).isEqualTo("English");
        assertThat(loaded.description()).isEqualTo("Speak and write with confidence");
        assertThat(loaded.icon()).isEqualTo(AreaIcon.GLOBE);
        assertThat(loaded.position()).isEqualTo(3);
        assertThat(loaded.createdAt()).isEqualTo(NOW);
    }

    @Test
    void savesAnAreaWithoutDescription() {
        Area area = Area.create("Technologies", null, AreaIcon.CODE, 0, NOW);

        areas.save(area);
        flushAndClear();
        Area loaded = areas.findById(area.id()).orElseThrow();

        assertThat(loaded.description()).isNull();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(area);
    }

    @Test
    void storesEveryIconByName() {
        int position = 0;
        Set<AreaId> created = new HashSet<>();
        for (AreaIcon icon : AreaIcon.values()) {
            created.add(areas.save(Area.create("Area " + icon, null, icon, position++, NOW)).id());
        }
        flushAndClear();

        List<AreaIcon> stored = areas.findAll().stream()
                .filter(area -> created.contains(area.id()))
                .map(Area::icon)
                .toList();

        assertThat(stored).containsExactly(AreaIcon.values());
    }

    @Test
    void findByIdIsEmptyForUnknownArea() {
        assertThat(areas.findById(AreaId.newId())).isEmpty();
    }

    @Test
    void findAllOrdersByPositionThenCreationTime() {
        Area third = areas.save(Area.create("Third", null, AreaIcon.BOOK, 2, NOW));
        Area first = areas.save(Area.create("First", null, AreaIcon.CODE, 0, NOW));
        Area secondLater = areas.save(Area.create("Second B", null, AreaIcon.CPU, 1, NOW.plusSeconds(10)));
        Area secondEarlier = areas.save(Area.create("Second A", null, AreaIcon.USERS, 1, NOW));
        flushAndClear();

        Set<AreaId> created = Set.of(first.id(), secondEarlier.id(), secondLater.id(), third.id());
        List<AreaId> ordered = areas.findAll().stream().map(Area::id).filter(created::contains).toList();

        assertThat(ordered).containsExactly(first.id(), secondEarlier.id(), secondLater.id(), third.id());
    }

    @Test
    void existsByNameIgnoresCaseAndSurroundingSpaces() {
        Area english = areas.save(Area.create("English", null, AreaIcon.GLOBE, 0, NOW));
        areas.save(Area.create("Consulting", null, AreaIcon.BRIEFCASE, 1, NOW));
        flushAndClear();

        assertThat(areas.existsByName("English", null)).isTrue();
        assertThat(areas.existsByName("english", null)).isTrue();
        assertThat(areas.existsByName("  ENGLISH  ", null)).isTrue();
        assertThat(areas.existsByName("Spanish", null)).isFalse();

        // Excluding an area lets it keep its own name, but not take another area's name.
        assertThat(areas.existsByName("english", english.id())).isFalse();
        assertThat(areas.existsByName("consulting", english.id())).isTrue();
        assertThat(areas.existsByName("english", AreaId.newId())).isTrue();
    }

    @Test
    void savingAnExistingAreaUpdatesItInsteadOfAddingOne() {
        Area area = areas.save(Area.create("English", "Old", AreaIcon.GLOBE, 0, NOW));
        flushAndClear();

        Area reloaded = areas.findById(area.id()).orElseThrow();
        reloaded.update("English B2", null, AreaIcon.AWARD);
        reloaded.moveTo(5);
        areas.save(reloaded);
        flushAndClear();

        Area updated = areas.findById(area.id()).orElseThrow();
        assertThat(updated).usingRecursiveComparison().isEqualTo(reloaded);
        assertThat(updated.createdAt()).isEqualTo(NOW);
        assertThat(rowCount("area", area.id().value())).isEqualTo(1);
    }

    @Test
    void savingTheSameNewAreaTwiceKeepsOneRow() {
        Area area = Area.create("English", null, AreaIcon.GLOBE, 0, NOW);

        areas.save(area);
        areas.save(area);
        flushAndClear();

        assertThat(rowCount("area", area.id().value())).isEqualTo(1);
    }

    @Test
    void deleteRemovesTheArea() {
        Area area = areas.save(Area.create("English", null, AreaIcon.GLOBE, 0, NOW));
        Area other = areas.save(Area.create("Consulting", null, AreaIcon.BRIEFCASE, 1, NOW));
        flushAndClear();

        areas.delete(area.id());
        flushAndClear();

        assertThat(areas.findById(area.id())).isEmpty();
        assertThat(areas.findById(other.id())).isPresent();
    }

    @Test
    void deletingAMissingAreaDoesNothing() {
        areas.delete(AreaId.newId());
        flushAndClear();
    }

    @Test
    void deletingAnAreaAlsoDeletesItsGoalsAndSteps() {
        Area area = areas.save(Area.create("English", null, AreaIcon.GLOBE, 0, NOW));
        Goal goal = Goal.create(area.id(), "Reach B2", null, LocalDate.of(2026, 12, 31), 0, NOW);
        goal.addStep(new StepDetails("Grammar", null, null), StepStatus.NOT_STARTED, NOW);
        goal.addStep(new StepDetails("Listening", null, null), StepStatus.DONE, NOW);
        goals.save(goal);
        flushAndClear();
        assertThat(goals.findById(goal.id())).isPresent();
        assertThat(stepCount(goal.id().value())).isEqualTo(2);

        areas.delete(area.id());
        flushAndClear();

        assertThat(areas.findById(area.id())).isEmpty();
        assertThat(goals.findById(goal.id())).isEmpty();
        assertThat(goals.findByArea(area.id())).isEmpty();
        assertThat(stepCount(goal.id().value())).isZero();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private long rowCount(String table, UUID id) {
        Number count = (Number) entityManager.getEntityManager()
                .createNativeQuery("select count(*) from " + table + " where id = :id")
                .setParameter("id", id)
                .getSingleResult();
        return count.longValue();
    }

    private long stepCount(UUID goalId) {
        Number count = (Number) entityManager.getEntityManager()
                .createNativeQuery("select count(*) from roadmap_step where goal_id = :id")
                .setParameter("id", goalId)
                .getSingleResult();
        return count.longValue();
    }
}

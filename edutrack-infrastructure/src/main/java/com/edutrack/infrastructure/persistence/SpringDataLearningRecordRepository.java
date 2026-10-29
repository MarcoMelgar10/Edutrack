package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataLearningRecordRepository extends JpaRepository<LearningRecordJpaEntity, UUID> {

    Optional<LearningRecordJpaEntity> findByGoalId(UUID goalId);

    Optional<LearningRecordJpaEntity> findByStepId(UUID stepId);

    /** One row per step that has a record, with the counts the roadmap timeline shows. */
    @Query(value = """
            select r.step_id as stepId,
                   (r.notes ~ '\\S') as hasNotes,
                   (select count(*) from learning_resource x where x.record_id = r.id) as resourceCount,
                   (select count(*) from learning_exercise e where e.record_id = r.id and e.done) as exercisesDone,
                   (select count(*) from learning_exercise e where e.record_id = r.id) as exercisesTotal,
                   (select count(*) from learning_result t where t.record_id = r.id) as resultCount
            from learning_record r
            where r.step_id in (:stepIds)
            """, nativeQuery = true)
    List<SummaryRow> summariesForSteps(@Param("stepIds") Collection<UUID> stepIds);

    /** The results filed under a measure, across all records, with the owner of the record each one lives in. */
    @Query(value = """
            select r.goal_id as goalId,
                   r.step_id as stepId,
                   t.id as id,
                   t.title as title,
                   t.part as part,
                   t.score as score,
                   t.taken_on as takenOn,
                   t.position as position
            from learning_result t
            join learning_record r on r.id = t.record_id
            where t.measure_id = :measureId
            order by t.taken_on, t.position
            """, nativeQuery = true)
    List<MeasuredRow> resultsForMeasure(@Param("measureId") UUID measureId);

    interface MeasuredRow {

        UUID getGoalId();

        UUID getStepId();

        UUID getId();

        String getTitle();

        String getPart();

        BigDecimal getScore();

        LocalDate getTakenOn();

        int getPosition();
    }

    interface SummaryRow {

        UUID getStepId();

        boolean getHasNotes();

        long getResourceCount();

        long getExercisesDone();

        long getExercisesTotal();

        long getResultCount();
    }
}

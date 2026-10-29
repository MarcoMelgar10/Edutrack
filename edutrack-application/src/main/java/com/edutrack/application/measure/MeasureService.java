package com.edutrack.application.measure;

import com.edutrack.application.NotFoundException;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.measure.Measure;
import com.edutrack.domain.measure.MeasureDetails;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.measure.MeasureRepository;
import com.edutrack.domain.record.LearningRecordRepository;
import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyRepository;

import java.time.Clock;
import java.util.List;

/**
 * Use cases for the measures a goal tracks (an exam band, a speed, an error count). The values
 * themselves are results recorded in learning records against the measure; this service defines the
 * measure and reads its series back. Every change returns the refreshed {@link MeasureView}.
 */
public class MeasureService {

    private final MeasureRepository measures;
    private final LearningRecordRepository records;
    private final CompetencyRepository competencies;
    private final GoalRepository goals;
    private final Clock clock;

    public MeasureService(MeasureRepository measures, LearningRecordRepository records,
                          CompetencyRepository competencies, GoalRepository goals, Clock clock) {
        this.measures = measures;
        this.records = records;
        this.competencies = competencies;
        this.goals = goals;
        this.clock = clock;
    }

    /** The goal's measures in display order, each with its series. */
    public List<MeasureView> forGoal(GoalId goalId) {
        return measures.findByGoal(goalId).stream().map(this::view).toList();
    }

    public MeasureView get(MeasureId id) {
        return view(load(id));
    }

    public MeasureView create(GoalId goalId, MeasureDetails details) {
        if (goals.findById(goalId).isEmpty()) {
            throw new NotFoundException("Goal", goalId);
        }
        ensureCompetencyBelongsTo(goalId, details);
        int position = measures.findByGoal(goalId).stream().mapToInt(Measure::position).max().orElse(-1) + 1;
        return view(measures.save(Measure.create(goalId, details, position, clock.instant())));
    }

    public MeasureView update(MeasureId id, MeasureDetails details) {
        Measure measure = load(id);
        ensureCompetencyBelongsTo(measure.goalId(), details);
        measure.update(details);
        return view(measures.save(measure));
    }

    /** The results recorded against the measure stay in their records, just no longer linked to anything. */
    public void delete(MeasureId id) {
        measures.delete(id);
    }

    private void ensureCompetencyBelongsTo(GoalId goalId, MeasureDetails details) {
        if (details.competencyId() == null) {
            return;
        }
        Competency competency = competencies.findById(details.competencyId())
                .orElseThrow(() -> new NotFoundException("Competency", details.competencyId()));
        if (!competency.goalId().equals(goalId)) {
            throw new NotFoundException("Competency", details.competencyId());
        }
    }

    private MeasureView view(Measure measure) {
        return MeasureView.of(measure, records.resultsForMeasure(measure.id()));
    }

    private Measure load(MeasureId id) {
        return measures.findById(id).orElseThrow(() -> new NotFoundException("Measure", id));
    }
}

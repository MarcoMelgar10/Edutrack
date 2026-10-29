package com.edutrack.application.support;

import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.record.Assessment;
import com.edutrack.domain.record.LearningRecord;
import com.edutrack.domain.record.LearningRecordRepository;
import com.edutrack.domain.record.MeasuredResult;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryLearningRecordRepository implements LearningRecordRepository {

    private final Map<RecordOwner, LearningRecord> records = new LinkedHashMap<>();
    private int saveCount;

    @Override
    public Optional<LearningRecord> findByOwner(RecordOwner owner) {
        return Optional.ofNullable(records.get(owner));
    }

    @Override
    public LearningRecord save(LearningRecord record) {
        saveCount++;
        records.put(record.owner(), record);
        return record;
    }

    @Override
    public Map<StepId, RecordSummary> summariesForSteps(Collection<StepId> stepIds) {
        Map<StepId, RecordSummary> summaries = new HashMap<>();
        for (StepId stepId : stepIds) {
            LearningRecord record = records.get(RecordOwner.ofStep(stepId));
            if (record != null) {
                summaries.put(stepId, record.summary());
            }
        }
        return summaries;
    }

    @Override
    public List<MeasuredResult> resultsForMeasure(MeasureId measureId) {
        List<MeasuredResult> results = new ArrayList<>();
        for (LearningRecord record : records.values()) {
            List<Assessment> assessments = record.assessments();
            for (int position = 0; position < assessments.size(); position++) {
                Assessment assessment = assessments.get(position);
                if (measureId.equals(assessment.measureId())) {
                    results.add(new MeasuredResult(record.owner(), assessment.id(), assessment.title(),
                            assessment.part(), assessment.score(), assessment.takenOn(), position));
                }
            }
        }
        results.sort(Comparator.comparing(MeasuredResult::takenOn).thenComparingInt(MeasuredResult::position));
        return results;
    }

    public int saveCount() {
        return saveCount;
    }

    public int size() {
        return records.size();
    }
}

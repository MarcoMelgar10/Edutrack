package com.edutrack.desktop.ui.dialog;

import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.domain.record.RecordSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * The sentence a delete confirmation shows about what goes with the deleted item, so nothing
 * recorded disappears without the learner being told: "Its notes, 2 resources and 1 result will be deleted too."
 */
public final class DeleteConsequences {

    private DeleteConsequences() {
    }

    /** For an area: its goals and their roadmaps; {@code null} when it has no goals. */
    public static String ofArea(int goals) {
        return goals == 0 ? null : I18n.plural("consequence.area", goals);
    }

    /** For a step: what its learning record holds; {@code null} when there is nothing. */
    public static String ofStep(RecordSummary record) {
        return sentence(parts(0, record));
    }

    /** For a goal: its steps, plus everything recorded on them and on the goal itself. */
    public static String ofGoal(int steps, RecordSummary recorded) {
        return sentence(parts(steps, recorded));
    }

    private static List<String> parts(int steps, RecordSummary record) {
        List<String> parts = new ArrayList<>();
        if (steps > 0) {
            parts.add(I18n.plural("count.roadmapSteps", steps));
        }
        if (record.hasNotes()) {
            parts.add(I18n.get("consequence.notes"));
        }
        if (record.resourceCount() > 0) {
            parts.add(I18n.plural("count.resources", record.resourceCount()));
        }
        if (record.exercisesTotal() > 0) {
            parts.add(I18n.plural("count.exercises", record.exercisesTotal()));
        }
        if (record.resultCount() > 0) {
            parts.add(I18n.plural("count.results", record.resultCount()));
        }
        return parts;
    }

    private static String sentence(List<String> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        String list = parts.size() == 1 ? parts.getFirst()
                : I18n.get("consequence.join", String.join(", ", parts.subList(0, parts.size() - 1)), parts.getLast());
        return I18n.get("consequence.sentence", list);
    }
}

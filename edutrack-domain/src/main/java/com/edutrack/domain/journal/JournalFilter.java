package com.edutrack.domain.journal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;

/** Which journal entries to list; every criterion is optional and they combine with "and". */
public record JournalFilter(AreaId areaId, GoalId goalId, EntryKind kind) {

    public static final JournalFilter ALL = new JournalFilter(null, null, null);
}

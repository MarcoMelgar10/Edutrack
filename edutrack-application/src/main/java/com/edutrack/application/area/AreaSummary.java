package com.edutrack.application.area;

import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.progress.Progress;

/** What a tab or an Overview row shows about an area. */
public record AreaSummary(AreaId id, String name, String description, AreaIcon icon, int goalCount, Progress progress) {
}

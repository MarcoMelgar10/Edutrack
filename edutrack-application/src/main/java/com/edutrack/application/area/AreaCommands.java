package com.edutrack.application.area;

import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;

/** Input for the area use cases, as typed in the Area dialog. Validated by the domain. */
public final class AreaCommands {

    private AreaCommands() {
    }

    public record CreateArea(String name, String description, AreaIcon icon) {
    }

    public record UpdateArea(AreaId id, String name, String description, AreaIcon icon) {
    }
}

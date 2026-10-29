package com.edutrack.domain.skill;

import java.time.LocalDate;
import java.util.Objects;

/** The level a competency was set to on a given day; the history of these is what the level chart plots. */
public record LevelChange(LocalDate on, int level) {

    public LevelChange {
        Objects.requireNonNull(on, "on");
    }
}

package com.edutrack.domain.area;

import com.edutrack.domain.shared.Validation;

import java.time.Instant;
import java.util.Objects;

/**
 * A field of learning, such as English, Technologies or Consulting. Areas group goals and
 * appear as tabs, ordered by {@link #position()}. Names are unique (case-insensitive); the
 * application layer checks that, because it needs the repository.
 */
public final class Area {

    public static final int NAME_MAX_LENGTH = 80;

    private final AreaId id;
    private final Instant createdAt;
    private String name;
    private String description;
    private AreaIcon icon;
    private int position;

    private Area(AreaId id, String name, String description, AreaIcon icon, int position, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        update(name, description, icon);
        moveTo(position);
    }

    public static Area create(String name, String description, AreaIcon icon, int position, Instant now) {
        return new Area(AreaId.newId(), name, description, icon, position, now);
    }

    /** Rebuilds an area from storage. */
    public static Area restore(AreaId id, String name, String description, AreaIcon icon, int position, Instant createdAt) {
        return new Area(id, name, description, icon, position, createdAt);
    }

    /** All-or-nothing: every value is validated before any field changes. */
    public void update(String name, String description, AreaIcon icon) {
        String validName = Validation.requiredText(name, "Name", NAME_MAX_LENGTH);
        String validDescription = Validation.optionalText(description, "Description", Validation.DESCRIPTION_MAX_LENGTH);
        AreaIcon validIcon = Validation.required(icon, "Icon");
        this.name = validName;
        this.description = validDescription;
        this.icon = validIcon;
    }

    public void moveTo(int position) {
        this.position = Validation.position(position);
    }

    public AreaId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public AreaIcon icon() {
        return icon;
    }

    public int position() {
        return position;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Area area && id.equals(area.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Area[" + name + "]";
    }
}

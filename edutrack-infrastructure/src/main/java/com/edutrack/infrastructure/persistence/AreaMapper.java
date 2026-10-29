package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaId;

final class AreaMapper {

    private AreaMapper() {
    }

    static Area toDomain(AreaJpaEntity entity) {
        return Area.restore(
                new AreaId(entity.getId()),
                entity.getName(),
                entity.getDescription(),
                entity.getIcon(),
                entity.getPosition(),
                entity.getCreatedAt());
    }

    /** Copies every field except the id and the version, which belong to the persistence layer. */
    static void copyToEntity(Area area, AreaJpaEntity entity) {
        entity.setName(area.name());
        entity.setDescription(area.description());
        entity.setIcon(area.icon());
        entity.setPosition(area.position());
        entity.setCreatedAt(area.createdAt());
    }
}

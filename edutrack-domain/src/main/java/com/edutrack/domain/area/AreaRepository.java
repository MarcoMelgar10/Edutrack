package com.edutrack.domain.area;

import java.util.List;
import java.util.Optional;

/** Port for storing areas. Implemented by the infrastructure layer. */
public interface AreaRepository {

    /** All areas, ordered by position. */
    List<Area> findAll();

    Optional<Area> findById(AreaId id);

    /** Whether another area already uses this name, ignoring case and surrounding spaces. */
    boolean existsByName(String name, AreaId excluding);

    Area save(Area area);

    /** Deletes the area together with its goals and their steps. */
    void delete(AreaId id);
}

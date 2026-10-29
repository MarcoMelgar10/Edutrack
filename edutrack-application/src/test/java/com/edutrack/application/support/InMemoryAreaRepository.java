package com.edutrack.application.support;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.area.AreaRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryAreaRepository implements AreaRepository {

    private final Map<AreaId, Area> areas = new LinkedHashMap<>();

    @Override
    public List<Area> findAll() {
        return areas.values().stream().sorted(Comparator.comparingInt(Area::position)).toList();
    }

    @Override
    public Optional<Area> findById(AreaId id) {
        return Optional.ofNullable(areas.get(id));
    }

    @Override
    public boolean existsByName(String name, AreaId excluding) {
        return areas.values().stream()
                .filter(area -> !area.id().equals(excluding))
                .anyMatch(area -> area.name().strip().equalsIgnoreCase(name.strip()));
    }

    @Override
    public Area save(Area area) {
        areas.put(area.id(), area);
        return area;
    }

    @Override
    public void delete(AreaId id) {
        areas.remove(id);
    }
}

package com.edutrack.application.area;

import com.edutrack.application.NotFoundException;
import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.ProgressCalculator;
import com.edutrack.domain.shared.DomainValidationException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Use cases for learning areas. Framework-free; wired as a bean by the desktop module. */
public class AreaService {

    /** Offered on first launch, when there are no areas yet. */
    static final List<AreaCommands.CreateArea> SUGGESTED_AREAS = List.of(
            new AreaCommands.CreateArea("English",
                    "Grammar, vocabulary, listening, speaking and writing.", AreaIcon.GLOBE),
            new AreaCommands.CreateArea("Technologies",
                    "Programming languages, frameworks, databases and tools.", AreaIcon.CODE),
            new AreaCommands.CreateArea("Consulting",
                    "Consulting profile: communication, analysis and business skills.", AreaIcon.BRIEFCASE));

    private final AreaRepository areas;
    private final GoalRepository goals;
    private final Clock clock;

    public AreaService(AreaRepository areas, GoalRepository goals, Clock clock) {
        this.areas = areas;
        this.goals = goals;
        this.clock = clock;
    }

    /** All areas in tab order, each with its goal count and progress. */
    public List<AreaSummary> listAreas() {
        return areas.findAll().stream().map(this::summarize).toList();
    }

    public AreaSummary getArea(AreaId id) {
        return summarize(load(id));
    }

    public AreaSummary createArea(AreaCommands.CreateArea command) {
        ensureNameIsFree(command.name(), null);
        int position = areas.findAll().stream().mapToInt(Area::position).max().orElse(-1) + 1;
        Area area = Area.create(command.name(), command.description(), command.icon(), position, clock.instant());
        return summarize(areas.save(area));
    }

    public AreaSummary updateArea(AreaCommands.UpdateArea command) {
        Area area = load(command.id());
        ensureNameIsFree(command.name(), command.id());
        area.update(command.name(), command.description(), command.icon());
        return summarize(areas.save(area));
    }

    /** Swaps the area with its neighbour in the sidebar; moving past either end does nothing. */
    public List<AreaSummary> moveArea(AreaId id, MoveDirection direction) {
        List<Area> ordered = new ArrayList<>(areas.findAll());
        int from = ordered.indexOf(load(id));
        int to = direction == MoveDirection.UP ? from - 1 : from + 1;
        if (to >= 0 && to < ordered.size()) {
            Collections.swap(ordered, from, to);
            for (int position = 0; position < ordered.size(); position++) {
                Area area = ordered.get(position);
                if (area.position() != position) {
                    area.moveTo(position);
                    areas.save(area);
                }
            }
        }
        return listAreas();
    }

    /** Deletes the area with all its goals and roadmap steps. */
    public void deleteArea(AreaId id) {
        areas.delete(id);
    }

    /** Creates English, Technologies and Consulting, skipping any name that already exists. */
    public List<AreaSummary> addSuggestedAreas() {
        for (AreaCommands.CreateArea suggestion : SUGGESTED_AREAS) {
            if (!areas.existsByName(suggestion.name(), null)) {
                createArea(suggestion);
            }
        }
        return listAreas();
    }

    private void ensureNameIsFree(String name, AreaId excluding) {
        if (name != null && !name.isBlank() && areas.existsByName(name.strip(), excluding)) {
            throw new DomainValidationException("area-name-taken",
                    "An area named \"" + name.strip() + "\" already exists.", name.strip());
        }
    }

    private Area load(AreaId id) {
        return areas.findById(id).orElseThrow(() -> new NotFoundException("Area", id));
    }

    private AreaSummary summarize(Area area) {
        List<Goal> areaGoals = goals.findByArea(area.id());
        return new AreaSummary(area.id(), area.name(), area.description(), area.icon(), areaGoals.size(),
                ProgressCalculator.ofGoals(areaGoals.stream().map(Goal::effort).toList()));
    }
}

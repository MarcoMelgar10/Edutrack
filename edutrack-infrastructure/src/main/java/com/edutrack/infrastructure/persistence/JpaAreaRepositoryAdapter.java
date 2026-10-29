package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.area.AreaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JpaAreaRepositoryAdapter implements AreaRepository {

    private final SpringDataAreaRepository repository;

    JpaAreaRepositoryAdapter(SpringDataAreaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Area> findAll() {
        return repository.findAllByOrderByPositionAscCreatedAtAsc().stream()
                .map(AreaMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Area> findById(AreaId id) {
        return repository.findById(id.value()).map(AreaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name, AreaId excluding) {
        String stripped = Objects.requireNonNull(name, "name").strip();
        return excluding == null
                ? repository.existsByNameIgnoreCase(stripped)
                : repository.existsByNameIgnoreCaseAndIdNot(stripped, excluding.value());
    }

    @Override
    @Transactional
    public Area save(Area area) {
        AreaJpaEntity entity = repository.findById(area.id().value())
                .orElseGet(() -> new AreaJpaEntity(area.id().value()));
        AreaMapper.copyToEntity(area, entity);
        return AreaMapper.toDomain(repository.saveAndFlush(entity));
    }

    /** The database cascade removes the area's goals and their steps. Deleting a missing area does nothing. */
    @Override
    @Transactional
    public void delete(AreaId id) {
        repository.deleteById(id.value());
    }
}

package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.review.Card;
import com.edutrack.domain.review.CardId;
import com.edutrack.domain.review.CardRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaCardRepositoryAdapter implements CardRepository {

    private final SpringDataCardRepository repository;

    JpaCardRepositoryAdapter(SpringDataCardRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Card save(Card card) {
        CardJpaEntity entity = repository.findWithSchedulesById(card.id().value())
                .orElseGet(() -> new CardJpaEntity(card.id().value()));
        CardMapper.copyToEntity(card, entity);
        return CardMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Card> findById(CardId id) {
        return repository.findWithSchedulesById(id.value()).map(CardMapper::toDomain);
    }

    /** Deleting a missing card does nothing; the database removes its schedules and review history. */
    @Override
    @Transactional
    public void delete(CardId id) {
        repository.deleteById(id.value());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Card> findByGoal(GoalId goalId) {
        return repository.findByGoalIdOrderByCreatedAtAscIdAsc(goalId.value()).stream().map(CardMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Card> findByStep(StepId stepId) {
        return repository.findByStepIdOrderByCreatedAtAscIdAsc(stepId.value()).stream().map(CardMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Card> findDueOrNew(LocalDate day) {
        return repository.findDueOrNew(day).stream().map(CardMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByGoal(GoalId goalId) {
        return repository.countByGoalId(goalId.value());
    }
}

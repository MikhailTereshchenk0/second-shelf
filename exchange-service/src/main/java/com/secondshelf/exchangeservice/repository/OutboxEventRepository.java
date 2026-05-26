package com.secondshelf.exchangeservice.repository;

import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OutboxEvent> findForUpdateByEventId(UUID eventId);

    List<OutboxEvent> findTop100ByStatusOrderByFailedAtDescCreatedAtDesc(OutboxEventStatus status);

    List<OutboxEvent> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
            Collection<OutboxEventStatus> statuses,
            LocalDateTime nextAttemptAt
    );

    Optional<OutboxEvent> findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
            Collection<OutboxEventStatus> statuses,
            LocalDateTime nextAttemptAt
    );

    long countByStatus(OutboxEventStatus status);

    long countByStatusIn(Collection<OutboxEventStatus> statuses);

    long countByStatusInAndNextAttemptAtLessThanEqual(Collection<OutboxEventStatus> statuses, LocalDateTime nextAttemptAt);

    boolean existsByAggregateTypeAndAggregateIdAndEventType(String aggregateType, String aggregateId, String eventType);
}

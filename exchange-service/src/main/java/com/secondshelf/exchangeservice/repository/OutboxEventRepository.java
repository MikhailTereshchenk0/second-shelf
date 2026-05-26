package com.secondshelf.exchangeservice.repository;

import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(UUID eventId);

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    Optional<OutboxEvent> findTopByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    long countByStatus(OutboxEventStatus status);

    boolean existsByAggregateTypeAndAggregateIdAndEventType(String aggregateType, String aggregateId, String eventType);
}

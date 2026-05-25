package com.secondshelf.exchangeservice.observability;

import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Component("exchangeOutbox")
public class ExchangeOutboxHealthIndicator implements HealthIndicator {

    private final OutboxEventRepository outboxEventRepository;
    private final boolean publisherEnabled;

    public ExchangeOutboxHealthIndicator(OutboxEventRepository outboxEventRepository,
                                         @Value("${exchange.outbox.publisher.enabled:true}") boolean publisherEnabled) {
        this.outboxEventRepository = outboxEventRepository;
        this.publisherEnabled = publisherEnabled;
    }

    @Override
    public Health health() {
        long pendingEvents = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
        long terminalFailedEvents = outboxEventRepository.countByStatus(OutboxEventStatus.TERMINAL_FAILED);
        Optional<OutboxEvent> oldestPendingEvent = outboxEventRepository
                .findTopByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        Health.Builder builder;
        if (!publisherEnabled) {
            builder = Health.unknown();
        } else if (terminalFailedEvents > 0) {
            builder = Health.down();
        } else {
            builder = Health.up();
        }

        builder.withDetail("publisherEnabled", publisherEnabled)
                .withDetail("pendingEvents", pendingEvents)
                .withDetail("terminalFailedEvents", terminalFailedEvents);

        oldestPendingEvent.ifPresent(event -> addOldestPendingDetails(builder, event));
        return builder.build();
    }

    private void addOldestPendingDetails(Health.Builder builder, OutboxEvent event) {
        builder.withDetail("oldestPendingEventId", String.valueOf(event.getEventId()));
        if (event.getCreatedAt() == null) {
            return;
        }

        long ageSeconds = Math.max(0L, Duration.between(event.getCreatedAt(), LocalDateTime.now()).getSeconds());
        builder.withDetail("oldestPendingEventCreatedAt", event.getCreatedAt())
                .withDetail("oldestPendingEventAgeSeconds", ageSeconds);
    }
}

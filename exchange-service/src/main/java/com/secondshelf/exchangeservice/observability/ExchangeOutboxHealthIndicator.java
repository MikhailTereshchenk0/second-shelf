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
import java.util.EnumSet;
import java.util.Optional;

@Component("exchangeOutbox")
public class ExchangeOutboxHealthIndicator implements HealthIndicator {

    private static final EnumSet<OutboxEventStatus> PENDING_STATUSES = EnumSet.of(
            OutboxEventStatus.PENDING,
            OutboxEventStatus.RETRYABLE_FAILED
    );

    private final OutboxEventRepository outboxEventRepository;
    private final boolean publisherEnabled;

    public ExchangeOutboxHealthIndicator(OutboxEventRepository outboxEventRepository,
                                         @Value("${exchange.outbox.publisher.enabled:true}") boolean publisherEnabled) {
        this.outboxEventRepository = outboxEventRepository;
        this.publisherEnabled = publisherEnabled;
    }

    @Override
    public Health health() {
        LocalDateTime now = LocalDateTime.now();
        long pendingEvents = outboxEventRepository.countByStatusIn(PENDING_STATUSES);
        long duePendingEvents = outboxEventRepository.countByStatusInAndNextAttemptAtLessThanEqual(PENDING_STATUSES, now);
        long terminalFailedEvents = outboxEventRepository.countByStatus(OutboxEventStatus.TERMINAL_FAILED);
        Optional<OutboxEvent> oldestDuePendingEvent = outboxEventRepository
                .findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(PENDING_STATUSES, now);

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
                .withDetail("duePendingEvents", duePendingEvents)
                .withDetail("terminalFailedEvents", terminalFailedEvents);

        oldestDuePendingEvent.ifPresent(event -> addOldestDuePendingDetails(builder, event, now));
        return builder.build();
    }

    private void addOldestDuePendingDetails(Health.Builder builder, OutboxEvent event, LocalDateTime now) {
        builder.withDetail("oldestDuePendingEventId", String.valueOf(event.getEventId()))
                .withDetail("oldestDuePendingEventStatus", event.getStatus());
        if (event.getCreatedAt() == null) {
            return;
        }

        long ageSeconds = Math.max(0L, Duration.between(event.getCreatedAt(), now).getSeconds());
        builder.withDetail("oldestDuePendingEventCreatedAt", event.getCreatedAt())
                .withDetail("oldestDuePendingEventNextAttemptAt", event.getNextAttemptAt())
                .withDetail("oldestDuePendingEventAgeSeconds", ageSeconds);
    }
}

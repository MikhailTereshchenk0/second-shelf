package com.secondshelf.exchangeservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.exchangeservice.config.ExchangeRabbitProperties;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.observability.CorrelationId;
import com.secondshelf.exchangeservice.observability.ExchangeAsyncMetrics;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "exchange.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class ExchangeOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ExchangeRabbitProperties exchangeRabbitProperties;
    private final TransactionOperations transactionOperations;
    private final ObjectMapper objectMapper;
    private final ExchangeAsyncMetrics exchangeAsyncMetrics;

    @Scheduled(fixedDelayString = "${exchange.outbox.publisher.fixed-delay-ms:5000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        pendingEvents.forEach(this::publishPendingEventSafely);
    }

    private void publishPendingEventSafely(OutboxEvent event) {
        ExchangeEventPayload payload = null;
        try {
            payload = readPayload(event);
            try (CorrelationId.Scope ignored = CorrelationId.openScope(payload.getCorrelationId())) {
                rabbitTemplate.send(exchangeRabbitProperties.getExchange(), event.getEventType(), buildMessage(event, payload));
                markPublished(event.getId());
                exchangeAsyncMetrics.incrementPublished(event.getEventType());
                log.info(
                        "Published exchange outbox event id={}, eventId={}, eventType={}",
                        event.getId(),
                        event.getEventId(),
                        event.getEventType()
                );
            }
        } catch (RuntimeException ex) {
            try (CorrelationId.Scope ignored = CorrelationId.openScope(payload != null ? payload.getCorrelationId() : null)) {
                handlePublishFailure(event, ex);
            }
        }
    }

    private Message buildMessage(OutboxEvent event, ExchangeEventPayload payload) {
        return MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(event.getEventId().toString())
                .setHeader("eventId", event.getEventId().toString())
                .setHeader("eventType", event.getEventType())
                .setHeader(CorrelationId.HEADER_NAME, CorrelationId.resolve(payload.getCorrelationId()))
                .build();
    }

    private ExchangeEventPayload readPayload(OutboxEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), ExchangeEventPayload.class);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to deserialize exchange outbox payload for event id=" + event.getId(),
                    ex
            );
        }
    }

    private void markPublished(Long outboxEventId) {
        withTransaction(() -> outboxEventRepository.findById(outboxEventId).ifPresent(event -> {
            if (event.getStatus() == OutboxEventStatus.PENDING) {
                event.markPublished();
                outboxEventRepository.save(event);
            }
        }));
    }

    private void handlePublishFailure(OutboxEvent event, RuntimeException ex) {
        exchangeAsyncMetrics.incrementPublishError(event.getEventType());
        log.warn(
                "Failed to publish exchange outbox event id={}, eventId={}, eventType={}",
                event.getId(),
                event.getEventId(),
                event.getEventType(),
                ex
        );

        try {
            withTransaction(() -> outboxEventRepository.findById(event.getId()).ifPresent(storedEvent -> {
                if (storedEvent.getStatus() == OutboxEventStatus.PENDING) {
                    storedEvent.incrementAttempts();
                    outboxEventRepository.save(storedEvent);
                }
            }));
        } catch (RuntimeException updateException) {
            log.error(
                    "Failed to update attempts count for exchange outbox event id={}",
                    event.getId(),
                    updateException
            );
        }
    }

    private void withTransaction(Runnable action) {
        transactionOperations.execute(status -> {
            action.run();
            return null;
        });
    }
}

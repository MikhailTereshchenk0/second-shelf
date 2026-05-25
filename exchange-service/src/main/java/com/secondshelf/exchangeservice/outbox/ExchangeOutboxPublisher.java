package com.secondshelf.exchangeservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.exchangeservice.config.ExchangeRabbitProperties;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.observability.CorrelationId;
import com.secondshelf.exchangeservice.observability.ExchangeAsyncMetrics;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@DependsOn("rabbitAdmin")
@ConditionalOnProperty(name = "exchange.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class ExchangeOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ExchangeRabbitProperties exchangeRabbitProperties;
    private final TransactionOperations transactionOperations;
    private final ObjectMapper objectMapper;
    private final ExchangeAsyncMetrics exchangeAsyncMetrics;
    private final long confirmTimeoutMs;
    private final int maxAttempts;

    public ExchangeOutboxPublisher(OutboxEventRepository outboxEventRepository,
                                   RabbitTemplate rabbitTemplate,
                                   ExchangeRabbitProperties exchangeRabbitProperties,
                                   TransactionOperations transactionOperations,
                                   ObjectMapper objectMapper,
                                   ExchangeAsyncMetrics exchangeAsyncMetrics,
                                   @Value("${exchange.outbox.publisher.confirm-timeout-ms:10000}")
                                   long confirmTimeoutMs,
                                   @Value("${exchange.outbox.publisher.max-attempts:5}")
                                   int maxAttempts) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeRabbitProperties = exchangeRabbitProperties;
        this.transactionOperations = transactionOperations;
        this.objectMapper = objectMapper;
        this.exchangeAsyncMetrics = exchangeAsyncMetrics;
        this.confirmTimeoutMs = confirmTimeoutMs;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

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
                log.info(
                        "Publishing exchange outbox event id={}, eventId={}, eventType={}, attempt={}, maxAttempts={}",
                        event.getId(),
                        event.getEventId(),
                        event.getEventType(),
                        event.getAttemptsCount() + 1,
                        maxAttempts
                );
                CorrelationData correlationData = new CorrelationData(event.getEventId().toString());
                rabbitTemplate.send(
                        exchangeRabbitProperties.getExchange(),
                        event.getEventType(),
                        buildMessage(event, payload),
                        correlationData
                );
                awaitBrokerConfirmation(event, correlationData);
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

    private void awaitBrokerConfirmation(OutboxEvent event, CorrelationData correlationData) {
        CorrelationData.Confirm confirm;
        try {
            confirm = correlationData.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for broker confirm for outbox event id=" + event.getId(),
                    ex
            );
        } catch (TimeoutException ex) {
            throw new IllegalStateException(
                    "Timed out waiting for broker confirm for outbox event id=" + event.getId(),
                    ex
            );
        } catch (ExecutionException ex) {
            throw new IllegalStateException(
                    "Failed while waiting for broker confirm for outbox event id=" + event.getId(),
                    ex
            );
        }

        if (confirm == null) {
            throw new IllegalStateException(
                    "Broker confirm is missing for outbox event id=" + event.getId()
            );
        }

        if (!confirm.isAck()) {
            throw new IllegalStateException(
                    "Broker nacked outbox event id=" + event.getId()
                            + (confirm.getReason() != null ? ", reason=" + confirm.getReason() : "")
            );
        }

        ReturnedMessage returnedMessage = correlationData.getReturned();
        if (returnedMessage != null) {
            throw new IllegalStateException(
                    "Broker returned unroutable outbox event id=" + event.getId()
                            + ", replyCode=" + returnedMessage.getReplyCode()
                            + ", replyText=" + returnedMessage.getReplyText()
                            + ", exchange=" + returnedMessage.getExchange()
                            + ", routingKey=" + returnedMessage.getRoutingKey()
            );
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
        String failureMessage = resolveFailureMessage(ex);
        log.warn(
                "Failed to publish exchange outbox event id={}, eventId={}, eventType={}, nextAttempt={}, maxAttempts={}",
                event.getId(),
                event.getEventId(),
                event.getEventType(),
                event.getAttemptsCount() + 1,
                maxAttempts,
                ex
        );

        try {
            OutboxEvent updatedEvent = updateFailureState(event.getId(), failureMessage);
            if (updatedEvent == null) {
                return;
            }

            if (updatedEvent.getStatus() == OutboxEventStatus.TERMINAL_FAILED) {
                exchangeAsyncMetrics.incrementTerminalFailed(event.getEventType());
                log.error(
                        "Marked exchange outbox event as terminally failed id={}, eventId={}, eventType={}, attempts={}, maxAttempts={}, lastError={}",
                        updatedEvent.getId(),
                        updatedEvent.getEventId(),
                        updatedEvent.getEventType(),
                        updatedEvent.getAttemptsCount(),
                        maxAttempts,
                        updatedEvent.getLastError()
                );
                return;
            }

            exchangeAsyncMetrics.incrementRetryScheduled(event.getEventType());
            log.warn(
                    "Scheduled retry for exchange outbox event id={}, eventId={}, eventType={}, attempts={}, maxAttempts={}",
                    updatedEvent.getId(),
                    updatedEvent.getEventId(),
                    updatedEvent.getEventType(),
                    updatedEvent.getAttemptsCount(),
                    maxAttempts
            );
        } catch (RuntimeException updateException) {
            log.error(
                    "Failed to update publish failure state for exchange outbox event id={}",
                    event.getId(),
                    updateException
            );
        }
    }

    private OutboxEvent updateFailureState(Long outboxEventId, String failureMessage) {
        AtomicReference<OutboxEvent> updatedEvent = new AtomicReference<>();
        withTransaction(() -> outboxEventRepository.findById(outboxEventId).ifPresent(storedEvent -> {
            if (storedEvent.getStatus() != OutboxEventStatus.PENDING) {
                return;
            }

            storedEvent.recordPublishFailure(failureMessage, maxAttempts);
            outboxEventRepository.save(storedEvent);
            updatedEvent.set(storedEvent);
        }));
        return updatedEvent.get();
    }

    private String resolveFailureMessage(RuntimeException ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }

        String typeName = current.getClass().getSimpleName();
        if (typeName == null || typeName.isBlank()) {
            typeName = current.getClass().getName();
        }

        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return typeName;
        }
        return typeName + ": " + message;
    }

    private void withTransaction(Runnable action) {
        transactionOperations.execute(status -> {
            action.run();
            return null;
        });
    }
}

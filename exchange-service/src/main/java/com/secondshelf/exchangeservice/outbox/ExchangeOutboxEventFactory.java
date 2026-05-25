package com.secondshelf.exchangeservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.observability.CorrelationId;
import com.secondshelf.exchangeservice.observability.ExchangeAsyncMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeOutboxEventFactory {

    private static final String EXCHANGE_REQUEST_AGGREGATE_TYPE = "EXCHANGE_REQUEST";
    private static final int SCHEMA_VERSION = 2;

    private final ObjectMapper objectMapper;
    private final ExchangeAsyncMetrics exchangeAsyncMetrics;

    public OutboxEvent create(ExchangeEventType eventType, ExchangeRequest exchangeRequest, ExchangeEventContext eventContext) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(exchangeRequest, "exchangeRequest must not be null");
        Objects.requireNonNull(exchangeRequest.getId(), "Exchange request must contain id before outbox event creation.");

        try {
            UUID eventId = UUID.randomUUID();
            LocalDateTime occurredAt = LocalDateTime.now();
            String correlationId = CorrelationId.currentOrGenerate();
            ExchangeEventPayload payload = ExchangeEventPayload.builder()
                    .schemaVersion(SCHEMA_VERSION)
                    .eventId(eventId)
                    .correlationId(correlationId)
                    .eventType(eventType.getValue())
                    .occurredAt(occurredAt)
                    .exchangeRequestId(exchangeRequest.getId())
                    .initiatorUserId(eventContext != null ? eventContext.getInitiatorUserId() : null)
                    .initiatorUsername(eventContext != null ? eventContext.getInitiatorUsername() : null)
                    .requesterId(exchangeRequest.getRequesterId())
                    .ownerId(exchangeRequest.getOwnerId())
                    .requestedBookId(exchangeRequest.getRequestedBookId())
                    .requestedBookTitle(exchangeRequest.getRequestedBookTitle())
                    .requestedBookAuthor(exchangeRequest.getRequestedBookAuthor())
                    .offeredBookId(exchangeRequest.getOfferedBookId())
                    .offeredBookTitle(exchangeRequest.getOfferedBookTitle())
                    .offeredBookAuthor(exchangeRequest.getOfferedBookAuthor())
                    .requestMessage(exchangeRequest.getMessage())
                    .completedByUserId(eventContext != null ? eventContext.getCompletedByUserId() : null)
                    .status(exchangeRequest.getStatus())
                    .ownerCompletionConfirmedAt(exchangeRequest.getOwnerCompletionConfirmedAt())
                    .requesterCompletionConfirmedAt(exchangeRequest.getRequesterCompletionConfirmedAt())
                    .build();

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(eventId)
                    .aggregateType(EXCHANGE_REQUEST_AGGREGATE_TYPE)
                    .aggregateId(String.valueOf(exchangeRequest.getId()))
                    .eventType(eventType.getValue())
                    .payload(objectMapper.writeValueAsString(payload))
                    .createdAt(occurredAt)
                    .status(OutboxEventStatus.PENDING)
                    .attemptsCount(0)
                    .build();
            log.info(
                    "Created exchange outbox event eventId={}, eventType={}, exchangeRequestId={}",
                    eventId,
                    eventType.getValue(),
                    exchangeRequest.getId()
            );
            exchangeAsyncMetrics.incrementCreated(eventType.getValue());
            return outboxEvent;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize exchange event payload.", ex);
        }
    }
}

package com.secondshelf.exchangeservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExchangeOutboxEventFactory {

    private static final String EXCHANGE_REQUEST_AGGREGATE_TYPE = "EXCHANGE_REQUEST";

    private final ObjectMapper objectMapper;

    public OutboxEvent create(ExchangeEventType eventType, ExchangeRequest exchangeRequest) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(exchangeRequest, "exchangeRequest must not be null");
        Objects.requireNonNull(exchangeRequest.getId(), "Exchange request must contain id before outbox event creation.");

        try {
            UUID eventId = UUID.randomUUID();
            LocalDateTime occurredAt = LocalDateTime.now();
            ExchangeEventPayload payload = ExchangeEventPayload.builder()
                    .eventId(eventId)
                    .eventType(eventType.getValue())
                    .occurredAt(occurredAt)
                    .exchangeRequestId(exchangeRequest.getId())
                    .requesterId(exchangeRequest.getRequesterId())
                    .ownerId(exchangeRequest.getOwnerId())
                    .requestedBookId(exchangeRequest.getRequestedBookId())
                    .offeredBookId(exchangeRequest.getOfferedBookId())
                    .status(exchangeRequest.getStatus())
                    .build();

            return OutboxEvent.builder()
                    .eventId(eventId)
                    .aggregateType(EXCHANGE_REQUEST_AGGREGATE_TYPE)
                    .aggregateId(String.valueOf(exchangeRequest.getId()))
                    .eventType(eventType.getValue())
                    .payload(objectMapper.writeValueAsString(payload))
                    .createdAt(occurredAt)
                    .status(OutboxEventStatus.PENDING)
                    .attemptsCount(0)
                    .build();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize exchange event payload.", ex);
        }
    }
}

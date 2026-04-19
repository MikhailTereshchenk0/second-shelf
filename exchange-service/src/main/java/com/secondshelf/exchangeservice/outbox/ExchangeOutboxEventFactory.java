package com.secondshelf.exchangeservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExchangeOutboxEventFactory {

    private static final String EXCHANGE_AGGREGATE_TYPE = "EXCHANGE";

    private final ObjectMapper objectMapper;

    public OutboxEvent create(ExchangeEventType eventType, ExchangeEventPayload payload) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(payload.getExchangeId(), "Exchange event payload must contain exchangeId.");

        try {
            return OutboxEvent.builder()
                    .eventId(UUID.randomUUID())
                    .aggregateType(EXCHANGE_AGGREGATE_TYPE)
                    .aggregateId(String.valueOf(payload.getExchangeId()))
                    .eventType(eventType.name())
                    .payload(objectMapper.writeValueAsString(payload))
                    .status(OutboxEventStatus.PENDING)
                    .attemptsCount(0)
                    .build();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize exchange event payload.", ex);
        }
    }
}

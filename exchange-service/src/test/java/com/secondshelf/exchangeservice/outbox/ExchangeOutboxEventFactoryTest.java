package com.secondshelf.exchangeservice.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeOutboxEventFactoryTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private final ExchangeOutboxEventFactory factory = new ExchangeOutboxEventFactory(objectMapper);

    @Test
    void createShouldPreparePendingOutboxEventForExchangeAggregate() throws Exception {
        // arrange
        ExchangeEventPayload payload = ExchangeEventPayload.builder()
                .exchangeId(42L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(77L)
                .status(ExchangeStatus.ACCEPTED)
                .occurredAt(LocalDateTime.of(2026, 4, 19, 20, 0))
                .build();

        // act
        OutboxEvent event = factory.create(ExchangeEventType.EXCHANGE_STATUS_CHANGED, payload);

        // assert
        assertNull(event.getId());
        assertNotNull(event.getEventId());
        assertEquals("EXCHANGE", event.getAggregateType());
        assertEquals("42", event.getAggregateId());
        assertEquals("EXCHANGE_STATUS_CHANGED", event.getEventType());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAttemptsCount());
        assertNull(event.getPublishedAt());
        assertNull(event.getCreatedAt());

        JsonNode payloadJson = objectMapper.readTree(event.getPayload());
        assertEquals(42L, payloadJson.get("exchangeId").asLong());
        assertEquals(100L, payloadJson.get("requestedBookId").asLong());
        assertEquals(200L, payloadJson.get("offeredBookId").asLong());
        assertEquals("ACCEPTED", payloadJson.get("status").asText());
    }

    @Test
    void outboxEventShouldTrackAttemptsAndPublishedState() {
        // arrange
        OutboxEvent event = OutboxEvent.builder()
                .status(OutboxEventStatus.PENDING)
                .attemptsCount(0)
                .build();

        // act
        event.incrementAttempts();
        event.markPublished();

        // assert
        assertEquals(1, event.getAttemptsCount());
        assertEquals(OutboxEventStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
    }
}

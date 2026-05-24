package com.secondshelf.exchangeservice.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.observability.CorrelationId;
import com.secondshelf.exchangeservice.observability.ExchangeAsyncMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeOutboxEventFactoryTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ExchangeOutboxEventFactory factory = new ExchangeOutboxEventFactory(
            objectMapper,
            new ExchangeAsyncMetrics(meterRegistry)
    );

    @Test
    void createShouldPreparePendingOutboxEventForExchangeAggregate() throws Exception {
        // arrange
        ExchangeRequest exchangeRequest = ExchangeRequest.builder()
                .id(42L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(77L)
                .status(ExchangeStatus.ACCEPTED)
                .build();

        // act
        OutboxEvent event;
        try (CorrelationId.Scope ignored = CorrelationId.openScope("corr-exchange-123")) {
            event = factory.create(ExchangeEventType.EXCHANGE_REQUEST_ACCEPTED, exchangeRequest);
        }

        // assert
        assertNull(event.getId());
        assertNotNull(event.getEventId());
        assertEquals("EXCHANGE_REQUEST", event.getAggregateType());
        assertEquals("42", event.getAggregateId());
        assertEquals("exchange.request.accepted", event.getEventType());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAttemptsCount());
        assertNull(event.getPublishedAt());
        assertNotNull(event.getCreatedAt());

        JsonNode payloadJson = objectMapper.readTree(event.getPayload());
        assertEquals(event.getEventId().toString(), payloadJson.get("eventId").asText());
        assertEquals("corr-exchange-123", payloadJson.get("correlationId").asText());
        assertEquals("exchange.request.accepted", payloadJson.get("eventType").asText());
        assertEquals(42L, payloadJson.get("exchangeRequestId").asLong());
        assertEquals(100L, payloadJson.get("requestedBookId").asLong());
        assertEquals(200L, payloadJson.get("offeredBookId").asLong());
        assertEquals(77L, payloadJson.get("requesterId").asLong());
        assertEquals(55L, payloadJson.get("ownerId").asLong());
        assertEquals("ACCEPTED", payloadJson.get("status").asText());
        assertTrue(payloadJson.hasNonNull("occurredAt"));
        assertEquals(
                1.0,
                meterRegistry.get("exchange.outbox.events.created")
                        .tag("event_type", "exchange.request.accepted")
                        .counter()
                        .count()
        );
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

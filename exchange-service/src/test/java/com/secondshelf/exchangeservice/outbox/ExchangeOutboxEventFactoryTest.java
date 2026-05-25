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
                .requestedBookTitle("The Left Hand of Darkness")
                .requestedBookAuthor("Ursula K. Le Guin")
                .offeredBookId(200L)
                .offeredBookTitle("Dune")
                .offeredBookAuthor("Frank Herbert")
                .ownerId(55L)
                .requesterId(77L)
                .status(ExchangeStatus.ACCEPTED)
                .message("Happy to swap this weekend.")
                .build();

        // act
        OutboxEvent event;
        try (CorrelationId.Scope ignored = CorrelationId.openScope("corr-exchange-123")) {
            event = factory.create(
                    ExchangeEventType.EXCHANGE_REQUEST_ACCEPTED,
                    exchangeRequest,
                    ExchangeEventContext.builder()
                            .initiatorUserId(55L)
                            .initiatorUsername("owner")
                            .build()
            );
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
        assertNull(event.getFailedAt());
        assertNull(event.getLastError());
        assertNotNull(event.getCreatedAt());

        JsonNode payloadJson = objectMapper.readTree(event.getPayload());
        assertEquals(2, payloadJson.get("schemaVersion").asInt());
        assertEquals(event.getEventId().toString(), payloadJson.get("eventId").asText());
        assertEquals("corr-exchange-123", payloadJson.get("correlationId").asText());
        assertEquals("exchange.request.accepted", payloadJson.get("eventType").asText());
        assertEquals(42L, payloadJson.get("exchangeRequestId").asLong());
        assertEquals(55L, payloadJson.get("initiatorUserId").asLong());
        assertEquals("owner", payloadJson.get("initiatorUsername").asText());
        assertEquals(100L, payloadJson.get("requestedBookId").asLong());
        assertEquals("The Left Hand of Darkness", payloadJson.get("requestedBookTitle").asText());
        assertEquals("Ursula K. Le Guin", payloadJson.get("requestedBookAuthor").asText());
        assertEquals(200L, payloadJson.get("offeredBookId").asLong());
        assertEquals("Dune", payloadJson.get("offeredBookTitle").asText());
        assertEquals("Frank Herbert", payloadJson.get("offeredBookAuthor").asText());
        assertEquals(77L, payloadJson.get("requesterId").asLong());
        assertEquals(55L, payloadJson.get("ownerId").asLong());
        assertEquals("Happy to swap this weekend.", payloadJson.get("requestMessage").asText());
        assertEquals("ACCEPTED", payloadJson.get("status").asText());
        assertTrue(payloadJson.get("completedByUserId").isNull());
        assertTrue(payloadJson.get("ownerCompletionConfirmedAt").isNull());
        assertTrue(payloadJson.get("requesterCompletionConfirmedAt").isNull());
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
        event.recordPublishFailure("Temporary RabbitMQ outage", 3);
        event.markPublished();

        // assert
        assertEquals(1, event.getAttemptsCount());
        assertEquals(OutboxEventStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getFailedAt());
        assertNull(event.getLastError());
    }

    @Test
    void outboxEventShouldRemainPendingUntilMarkedPublished() {
        OutboxEvent event = OutboxEvent.builder()
                .status(OutboxEventStatus.PENDING)
                .attemptsCount(0)
                .build();

        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertNull(event.getPublishedAt());
    }

    @Test
    void outboxEventShouldBecomeTerminallyFailedWhenAttemptsAreExhausted() {
        OutboxEvent event = OutboxEvent.builder()
                .status(OutboxEventStatus.PENDING)
                .attemptsCount(2)
                .build();

        event.recordPublishFailure("RabbitMQ unavailable", 3);

        assertEquals(3, event.getAttemptsCount());
        assertEquals(OutboxEventStatus.TERMINAL_FAILED, event.getStatus());
        assertNotNull(event.getFailedAt());
        assertEquals("RabbitMQ unavailable", event.getLastError());
    }
}

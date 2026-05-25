package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import com.secondshelf.notificationservice.observability.CorrelationId;
import com.secondshelf.notificationservice.observability.NotificationAsyncMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExchangeEventConsumerTest {

    @Mock
    private ExchangeEventNotificationService exchangeEventNotificationService;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void consumeExchangeEventShouldDelegateToService() {
        ExchangeEventPayload payload = sampleEvent();
        ExchangeEventConsumer exchangeEventConsumer = createConsumer();

        exchangeEventConsumer.consumeExchangeEvent(payload, "corr-consumer-header-123", false);

        verify(exchangeEventNotificationService).process(payload);
        assertEquals(
                1.0,
                meterRegistry.get("notification.exchange.events.received")
                        .tag("event_type", "exchange.request.created")
                        .counter()
                        .count()
        );
    }

    @Test
    void consumeExchangeEventShouldPopulateMdcFromHeaderAndClearAfterwards() {
        ExchangeEventPayload payload = sampleEvent();
        ExchangeEventConsumer exchangeEventConsumer = createConsumer();
        doAnswer(invocation -> {
            assertEquals("corr-consumer-header-123", MDC.get(CorrelationId.MDC_KEY));
            return null;
        }).when(exchangeEventNotificationService).process(payload);

        exchangeEventConsumer.consumeExchangeEvent(payload, "corr-consumer-header-123", false);

        assertNull(MDC.get(CorrelationId.MDC_KEY));
    }

    @Test
    void consumeExchangeEventShouldPopulateMdcFromPayloadWhenHeaderIsMissing() {
        ExchangeEventPayload payload = sampleEvent();
        ExchangeEventConsumer exchangeEventConsumer = createConsumer();
        doAnswer(invocation -> {
            assertEquals("corr-consumer-payload-123", MDC.get(CorrelationId.MDC_KEY));
            return null;
        }).when(exchangeEventNotificationService).process(payload);

        exchangeEventConsumer.consumeExchangeEvent(payload, null, false);

        assertNull(MDC.get(CorrelationId.MDC_KEY));
    }

    @Test
    void consumeExchangeEventShouldRejectNonRetryableBadRequest() {
        ExchangeEventPayload payload = sampleEvent();
        ExchangeEventConsumer exchangeEventConsumer = createConsumer();
        doThrow(new NotificationBadRequestException("INVALID_EXCHANGE_EVENT", "Exchange event is invalid."))
                .when(exchangeEventNotificationService).process(payload);

        AmqpRejectAndDontRequeueException exception = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> exchangeEventConsumer.consumeExchangeEvent(payload, null, false)
        );

        assertInstanceOf(NotificationBadRequestException.class, exception.getCause());
        assertEquals("Exchange event is invalid.", exception.getMessage());
        assertEquals(
                1.0,
                meterRegistry.get("notification.exchange.events.dead_lettered")
                        .tag("event_type", "exchange.request.created")
                        .tag("reason", "invalid")
                        .counter()
                        .count()
        );
    }

    @Test
    void consumeExchangeEventShouldRequeueRetryableErrorsOnFirstDelivery() {
        ExchangeEventPayload payload = sampleEvent();
        ExchangeEventConsumer exchangeEventConsumer = createConsumer();
        RuntimeException failure = new RuntimeException("Temporary database error");
        doThrow(failure).when(exchangeEventNotificationService).process(payload);

        ImmediateRequeueAmqpException exception = assertThrows(
                ImmediateRequeueAmqpException.class,
                () -> exchangeEventConsumer.consumeExchangeEvent(payload, null, false)
        );

        assertSame(failure, exception.getCause());
        assertEquals(
                1.0,
                meterRegistry.get("notification.exchange.events.retried")
                        .tag("event_type", "exchange.request.created")
                        .counter()
                        .count()
        );
    }

    @Test
    void consumeExchangeEventShouldDeadLetterRetryableErrorsAfterRedelivery() {
        ExchangeEventPayload payload = sampleEvent();
        ExchangeEventConsumer exchangeEventConsumer = createConsumer();
        RuntimeException failure = new RuntimeException("Temporary database error");
        doThrow(failure).when(exchangeEventNotificationService).process(payload);

        AmqpRejectAndDontRequeueException exception = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> exchangeEventConsumer.consumeExchangeEvent(payload, null, true)
        );

        assertSame(failure, exception.getCause());
        assertEquals("Retries exhausted for exchange event.", exception.getMessage());
        assertEquals(
                1.0,
                meterRegistry.get("notification.exchange.events.dead_lettered")
                        .tag("event_type", "exchange.request.created")
                        .tag("reason", "retries_exhausted")
                        .counter()
                        .count()
        );
    }

    private ExchangeEventConsumer createConsumer() {
        return new ExchangeEventConsumer(
                exchangeEventNotificationService,
                new NotificationAsyncMetrics(meterRegistry)
        );
    }

    private ExchangeEventPayload sampleEvent() {
        return ExchangeEventPayload.builder()
                .eventId(UUID.randomUUID())
                .correlationId("corr-consumer-payload-123")
                .eventType("exchange.request.created")
                .occurredAt(LocalDateTime.of(2026, 5, 18, 22, 30))
                .exchangeRequestId(101L)
                .requesterId(42L)
                .ownerId(55L)
                .requestedBookId(1001L)
                .offeredBookId(2002L)
                .status("PENDING")
                .build();
    }
}

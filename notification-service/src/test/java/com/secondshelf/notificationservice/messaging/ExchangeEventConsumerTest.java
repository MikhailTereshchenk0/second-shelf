package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExchangeEventConsumerTest {

    @Mock
    private ExchangeEventNotificationService exchangeEventNotificationService;

    @InjectMocks
    private ExchangeEventConsumer exchangeEventConsumer;

    @Test
    void consumeExchangeEventShouldDelegateToService() {
        // arrange
        ExchangeEventPayload payload = sampleEvent();

        // act
        exchangeEventConsumer.consumeExchangeEvent(payload);

        // assert
        verify(exchangeEventNotificationService).process(payload);
    }

    @Test
    void consumeExchangeEventShouldRejectNonRetryableBadRequest() {
        // arrange
        ExchangeEventPayload payload = sampleEvent();
        doThrow(new NotificationBadRequestException("INVALID_EXCHANGE_EVENT", "Exchange event is invalid."))
                .when(exchangeEventNotificationService).process(payload);

        // act
        AmqpRejectAndDontRequeueException exception = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> exchangeEventConsumer.consumeExchangeEvent(payload)
        );

        // assert
        assertInstanceOf(NotificationBadRequestException.class, exception.getCause());
        assertEquals("Exchange event is invalid.", exception.getMessage());
    }

    @Test
    void consumeExchangeEventShouldPropagateRetryableErrors() {
        // arrange
        ExchangeEventPayload payload = sampleEvent();
        RuntimeException failure = new RuntimeException("Temporary database error");
        doThrow(failure).when(exchangeEventNotificationService).process(payload);

        // act
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> exchangeEventConsumer.consumeExchangeEvent(payload)
        );

        // assert
        assertSame(failure, exception);
    }

    private ExchangeEventPayload sampleEvent() {
        return ExchangeEventPayload.builder()
                .eventId(UUID.randomUUID())
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

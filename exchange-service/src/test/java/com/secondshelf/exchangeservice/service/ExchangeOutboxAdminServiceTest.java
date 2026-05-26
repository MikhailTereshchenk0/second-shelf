package com.secondshelf.exchangeservice.service;

import com.secondshelf.exchangeservice.dto.OutboxRetryResponse;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.exception.ExchangeConflictException;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeOutboxAdminServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void retryTerminalFailedEventShouldRequeueForPublisher() {
        UUID eventId = UUID.randomUUID();
        LocalDateTime beforeRetry = LocalDateTime.now();
        OutboxEvent event = OutboxEvent.builder()
                .id(1L)
                .eventId(eventId)
                .aggregateType("EXCHANGE_REQUEST")
                .aggregateId("42")
                .eventType("exchange.request.completed")
                .payload("{}")
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .failedAt(LocalDateTime.now().minusMinutes(2))
                .firstFailedAt(LocalDateTime.now().minusMinutes(5))
                .status(OutboxEventStatus.TERMINAL_FAILED)
                .attemptsCount(5)
                .manualRetryCount(1)
                .lastError("AmqpException: RabbitMQ is unavailable")
                .build();

        when(outboxEventRepository.findForUpdateByEventId(eventId)).thenReturn(Optional.of(event));
        when(outboxEventRepository.save(event)).thenReturn(event);

        ExchangeOutboxAdminService service = new ExchangeOutboxAdminService(outboxEventRepository);
        OutboxRetryResponse response = service.retryTerminalFailedEvent(eventId);

        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertNull(event.getFailedAt());
        assertNotNull(event.getNextAttemptAt());
        assertTrue(!event.getNextAttemptAt().isBefore(beforeRetry));
        assertEquals(2, event.getManualRetryCount());
        assertEquals(event.getNextAttemptAt(), event.getManualRetriedAt());
        assertEquals(OutboxEventStatus.PENDING, response.getEvent().getStatus());
        verify(outboxEventRepository).save(event);
    }

    @Test
    void retryTerminalFailedEventShouldRejectNonTerminalEvent() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .eventId(eventId)
                .status(OutboxEventStatus.PENDING)
                .build();

        when(outboxEventRepository.findForUpdateByEventId(eventId)).thenReturn(Optional.of(event));

        ExchangeOutboxAdminService service = new ExchangeOutboxAdminService(outboxEventRepository);

        assertThrows(ExchangeConflictException.class, () -> service.retryTerminalFailedEvent(eventId));
        verify(outboxEventRepository, never()).save(event);
    }
}

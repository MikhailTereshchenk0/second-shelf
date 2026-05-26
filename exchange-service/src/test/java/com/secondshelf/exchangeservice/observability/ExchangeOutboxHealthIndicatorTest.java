package com.secondshelf.exchangeservice.observability;

import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeOutboxHealthIndicatorTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void healthShouldBeUpWhenPublisherIsEnabledAndNoTerminalFailuresExist() {
        OutboxEvent oldestPendingEvent = OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .createdAt(LocalDateTime.now().minusSeconds(30))
                .nextAttemptAt(LocalDateTime.now().minusSeconds(5))
                .status(OutboxEventStatus.PENDING)
                .build();
        when(outboxEventRepository.countByStatusIn(any())).thenReturn(2L);
        when(outboxEventRepository.countByStatusInAndNextAttemptAtLessThanEqual(any(), any(LocalDateTime.class))).thenReturn(1L);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.TERMINAL_FAILED)).thenReturn(0L);
        when(outboxEventRepository.findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
                any(),
                any(LocalDateTime.class)
        ))
                .thenReturn(Optional.of(oldestPendingEvent));

        Health health = new ExchangeOutboxHealthIndicator(outboxEventRepository, true).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(true, health.getDetails().get("publisherEnabled"));
        assertEquals(2L, health.getDetails().get("pendingEvents"));
        assertEquals(1L, health.getDetails().get("duePendingEvents"));
        assertEquals(0L, health.getDetails().get("terminalFailedEvents"));
        assertEquals(oldestPendingEvent.getEventId().toString(), health.getDetails().get("oldestDuePendingEventId"));
    }

    @Test
    void healthShouldBeDownWhenTerminalFailuresExist() {
        when(outboxEventRepository.countByStatusIn(any())).thenReturn(1L);
        when(outboxEventRepository.countByStatusInAndNextAttemptAtLessThanEqual(any(), any(LocalDateTime.class))).thenReturn(0L);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.TERMINAL_FAILED)).thenReturn(1L);
        when(outboxEventRepository.findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
                any(),
                any(LocalDateTime.class)
        ))
                .thenReturn(Optional.empty());

        Health health = new ExchangeOutboxHealthIndicator(outboxEventRepository, true).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(1L, health.getDetails().get("terminalFailedEvents"));
    }

    @Test
    void healthShouldBeUnknownWhenPublisherIsDisabled() {
        when(outboxEventRepository.countByStatusIn(any())).thenReturn(0L);
        when(outboxEventRepository.countByStatusInAndNextAttemptAtLessThanEqual(any(), any(LocalDateTime.class))).thenReturn(0L);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.TERMINAL_FAILED)).thenReturn(0L);
        when(outboxEventRepository.findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
                any(),
                any(LocalDateTime.class)
        ))
                .thenReturn(Optional.empty());

        Health health = new ExchangeOutboxHealthIndicator(outboxEventRepository, false).health();

        assertEquals(Status.UNKNOWN, health.getStatus());
        assertEquals(false, health.getDetails().get("publisherEnabled"));
    }
}

package com.secondshelf.exchangeservice.observability;

import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeOutboxMetricsBinderTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void bindToShouldRegisterCurrentOutboxGauges() {
        when(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(4L);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.TERMINAL_FAILED)).thenReturn(1L);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        new ExchangeOutboxMetricsBinder(outboxEventRepository).bindTo(meterRegistry);

        assertEquals(4.0, meterRegistry.get("exchange.outbox.events.pending.current").gauge().value());
        assertEquals(1.0, meterRegistry.get("exchange.outbox.events.terminal_failed.current").gauge().value());
    }
}

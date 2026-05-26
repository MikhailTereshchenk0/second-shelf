package com.secondshelf.exchangeservice.observability;

import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;

@Component
@RequiredArgsConstructor
public class ExchangeOutboxMetricsBinder implements MeterBinder {

    private static final EnumSet<OutboxEventStatus> PENDING_STATUSES = EnumSet.of(
            OutboxEventStatus.PENDING,
            OutboxEventStatus.RETRYABLE_FAILED
    );

    private final OutboxEventRepository outboxEventRepository;

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        Gauge.builder(
                        "exchange.outbox.events.pending.current",
                        outboxEventRepository,
                        repository -> repository.countByStatusIn(PENDING_STATUSES)
                )
                .description("Current number of pending and retryable failed exchange outbox events.")
                .register(meterRegistry);

        Gauge.builder(
                        "exchange.outbox.events.pending_due.current",
                        outboxEventRepository,
                        repository -> repository.countByStatusInAndNextAttemptAtLessThanEqual(PENDING_STATUSES, LocalDateTime.now())
                )
                .description("Current number of due pending and retryable failed exchange outbox events.")
                .register(meterRegistry);

        Gauge.builder(
                        "exchange.outbox.events.pending_total.current",
                        outboxEventRepository,
                        repository -> repository.countByStatusIn(PENDING_STATUSES)
                )
                .description("Current total number of pending and retryable failed exchange outbox events.")
                .register(meterRegistry);

        Gauge.builder(
                        "exchange.outbox.events.terminal_failed.current",
                        outboxEventRepository,
                        repository -> repository.countByStatus(OutboxEventStatus.TERMINAL_FAILED)
                )
                .description("Current number of terminally failed exchange outbox events.")
                .register(meterRegistry);
    }
}

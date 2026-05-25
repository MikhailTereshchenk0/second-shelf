package com.secondshelf.exchangeservice.observability;

import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeOutboxMetricsBinder implements MeterBinder {

    private final OutboxEventRepository outboxEventRepository;

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        Gauge.builder(
                        "exchange.outbox.events.pending.current",
                        outboxEventRepository,
                        repository -> repository.countByStatus(OutboxEventStatus.PENDING)
                )
                .description("Current number of pending exchange outbox events.")
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

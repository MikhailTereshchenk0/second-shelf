package com.secondshelf.exchangeservice.observability;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeAsyncMetrics {

    private final MeterRegistry meterRegistry;

    public void incrementCreated(String eventType) {
        meterRegistry.counter("exchange.outbox.events.created", "event_type", safe(eventType)).increment();
    }

    public void incrementPublished(String eventType) {
        meterRegistry.counter("exchange.outbox.events.published", "event_type", safe(eventType)).increment();
    }

    public void incrementPublishError(String eventType) {
        meterRegistry.counter("exchange.outbox.publish.errors", "event_type", safe(eventType)).increment();
    }

    public void incrementRetryScheduled(String eventType) {
        meterRegistry.counter("exchange.outbox.publish.retries", "event_type", safe(eventType)).increment();
    }

    public void incrementTerminalFailed(String eventType) {
        meterRegistry.counter("exchange.outbox.events.terminal_failed", "event_type", safe(eventType)).increment();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}

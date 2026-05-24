package com.secondshelf.notificationservice.observability;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationAsyncMetrics {

    private final MeterRegistry meterRegistry;

    public void incrementProcessed(String eventType) {
        meterRegistry.counter("notification.exchange.events.processed", "event_type", safe(eventType)).increment();
    }

    public void incrementIgnored(String eventType, String reason) {
        meterRegistry.counter(
                "notification.exchange.events.ignored",
                "event_type", safe(eventType),
                "reason", safe(reason)
        ).increment();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}

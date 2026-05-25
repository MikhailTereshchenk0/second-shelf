package com.secondshelf.notificationservice.observability;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationAsyncMetrics {

    private final MeterRegistry meterRegistry;

    public void incrementReceived(String eventType) {
        meterRegistry.counter("notification.exchange.events.received", "event_type", safe(eventType)).increment();
    }

    public void incrementProcessed(String eventType) {
        meterRegistry.counter("notification.exchange.events.processed", "event_type", safe(eventType)).increment();
    }

    public void incrementNotificationsCreated(String eventType, int notificationsCreated) {
        if (notificationsCreated <= 0) {
            return;
        }

        meterRegistry.counter("notification.exchange.notifications.created", "event_type", safe(eventType))
                .increment(notificationsCreated);
    }

    public void incrementIgnored(String eventType, String reason) {
        meterRegistry.counter(
                "notification.exchange.events.ignored",
                "event_type", safe(eventType),
                "reason", safe(reason)
        ).increment();
    }

    public void incrementRetried(String eventType) {
        meterRegistry.counter("notification.exchange.events.retried", "event_type", safe(eventType)).increment();
    }

    public void incrementDeadLettered(String eventType, String reason) {
        meterRegistry.counter(
                "notification.exchange.events.dead_lettered",
                "event_type", safe(eventType),
                "reason", safe(reason)
        ).increment();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}

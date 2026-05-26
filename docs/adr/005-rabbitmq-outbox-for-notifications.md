# 005. RabbitMQ Outbox For Notifications

## Context

Exchange workflow actions create user-facing notifications. The repository currently uses RabbitMQ to deliver exchange domain events from `exchange-service` to `notification-service`.

The critical failure case is a successful exchange state change followed by a failed message publish. Without a durable handoff, notifications could be lost.

## Decision

Use the transactional outbox pattern in `exchange-service`.

`exchange-service` writes an `outbox_events` row in the same database transaction as the exchange state change. A scheduled publisher reads pending events, publishes persistent JSON messages to RabbitMQ, waits for broker confirms, handles unroutable returns, increments attempts on failure, and marks exhausted rows as `TERMINAL_FAILED`.

`notification-service` consumes from `notification.exchange-events`, stores processed event ids for idempotency, persists in-app notifications, and sends invalid or exhausted messages to DLQ. Admin endpoints support terminal outbox retry and DLQ redrive.

## Consequences

- Exchange state changes and notification intent are committed atomically inside `exchange_db`.
- RabbitMQ outages do not normally break the user-facing exchange operation; pending outbox events can publish after recovery.
- Operators must monitor pending outbox count, `TERMINAL_FAILED`, Rabbit listener health, and DLQ growth.
- Notification delivery is at-least-once; consumers must remain idempotent.

## Alternatives considered

- **Publish directly inside request handlers.** Simpler but can lose events when the database commit and broker publish diverge.
- **Synchronous notification creation through HTTP.** Tighter coupling and poorer resilience when `notification-service` is unavailable.
- **Kafka instead of RabbitMQ.** Reasonable for larger event streams, but RabbitMQ is sufficient for command-like notification delivery in this project.

## Status

Accepted.

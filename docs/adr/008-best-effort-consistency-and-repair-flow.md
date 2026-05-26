# 008. Best-Effort Consistency And Repair Flow

## Context

`exchange-service` owns the exchange request lifecycle, but final exchange completion and accepted-cancel compensation require book state transitions in `book-service`.

The repository does not use distributed transactions. Partial failures can occur if one book transition succeeds and the next one fails, or if cancellation compensation cannot release both books.

## Decision

Use best-effort synchronous coordination with explicit repair state.

`exchange-service` calls internal `book-service` endpoints for reserve, release, and exchanged transitions. If a distributed transition partially fails in cases that can leave books inconsistent, the exchange is moved to `REPAIR_REQUIRED` with repair metadata. Normal participant actions are blocked until an admin repair operation retries the necessary book transitions and moves the exchange to the intended state.

Outbox retry and notification DLQ redrive are handled as operational recovery flows rather than hidden automatic self-healing.

## Consequences

- The implementation avoids distributed transaction infrastructure while still surfacing inconsistent states explicitly.
- Users are protected from continuing workflows while an exchange needs repair.
- Operators have deterministic admin endpoints for repair, terminal outbox retry, and notification DLQ redrive.
- Production readiness requires monitoring, runbooks, and audit trails for repair and redrive operations.

## Alternatives considered

- **Two-phase commit across services.** Stronger consistency but heavy operational complexity and tight coupling.
- **Fully asynchronous saga.** More scalable but would require larger workflow orchestration, more states, and more compensation logic.
- **Ignore partial failures.** Simpler but can leave books and exchange requests permanently inconsistent without visibility.

## Status

Accepted.

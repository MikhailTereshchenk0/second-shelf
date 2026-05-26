# 001. Microservice Architecture

## Context

Second Shelf is implemented as a multi-module Spring Boot backend for a book exchange platform. The current repository contains `api-gateway`, `auth-service`, `user-service`, `book-service`, `exchange-service`, `notification-service`, and shared `observability-common` utilities.

The business domain has clear ownership boundaries:

- authentication and refresh sessions belong to `auth-service`;
- profiles, roles, account status, and password hashes belong to `user-service`;
- catalog and owner book management belong to `book-service`;
- exchange request lifecycle belongs to `exchange-service`;
- persisted in-app notifications belong to `notification-service`.

## Decision

Keep the backend as a microservice-oriented Maven reactor with separate Spring Boot services per domain. Runtime integration uses synchronous HTTP for user-facing APIs and service-to-service operations, plus RabbitMQ for exchange-derived notifications.

The `api-gateway` is the recommended frontend and production entry point. Domain services remain independently runnable for local debugging, isolated testing, and service-level Swagger access when enabled.

## Consequences

- Each service can evolve around its own domain model and persistence schema.
- Security controls must be applied consistently across services: JWT validation, error shape, correlation id handling, audit logging, and production secret validation.
- Cross-service workflows require explicit consistency handling, especially exchange completion and cancellation flows that coordinate with `book-service`.
- The Maven reactor and Docker Compose files must keep service versions, shared dependencies, and startup order aligned.

## Alternatives considered

- **Modular monolith.** Simpler transactions and deployment, but weaker isolation between auth, profile, catalog, exchange, and notification data. This would also reduce the value of service-specific security and reliability work already present in the repository.
- **Single backend plus external notification worker.** Less operational overhead than full microservices, but it would still need async delivery, outbox, and separate ownership of notifications.
- **Event-driven services only.** More decoupled, but too complex for current synchronous user workflows such as exchange validation and book reservation.

## Status

Accepted.

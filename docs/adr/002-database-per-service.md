# 002. Database Per Service

## Context

Second Shelf stores domain data in PostgreSQL. The current Compose initialization creates five logical databases:

- `auth_db`;
- `users_db`;
- `books_db`;
- `exchange_db`;
- `notification_db`.

There are no cross-service foreign keys. Services reference users, books, exchanges, and notifications through identifiers and snapshots rather than shared relational constraints.

## Decision

Use a database-per-service model. Each domain service owns its local schema and is the only application component expected to write to it:

- `auth-service` owns refresh token sessions in `auth_db`;
- `user-service` owns profiles, roles, account status, and password hashes in `users_db`;
- `book-service` owns books, visibility, status, and `ownerId` in `books_db`;
- `exchange-service` owns exchange requests and outbox events in `exchange_db`;
- `notification-service` owns notifications and processed event ids in `notification_db`.

## Consequences

- Personal data is minimized by domain: `auth-service` stores refresh metadata and `userId`, not full profile data.
- A compromised service database has a narrower data scope than a shared schema.
- Cross-domain operations cannot rely on database joins or foreign keys; services must validate through APIs and persisted snapshots.
- Deployment infrastructure should use separate database credentials and network ACLs per service where possible, even when a single PostgreSQL server hosts the logical databases.
- Backup and restore procedures must cover all five databases and consider consistency between `exchange_db`, `books_db`, and `notification_db`.

## Alternatives considered

- **Single shared database.** Easier querying and transactions, but creates broader blast radius and weakens domain ownership.
- **Shared database with separate schemas.** Better than a single schema, but still encourages cross-service coupling and shared database credentials.
- **Polyglot persistence.** Not needed for the current domain; PostgreSQL is sufficient for transactional data and RabbitMQ handles async delivery.

## Status

Accepted.

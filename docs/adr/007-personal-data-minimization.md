# 007. Personal Data Minimization

## Context

Second Shelf processes ordinary personal data: username, email, profile fields, phone number, internal user ids, exchange messages, and notification content. It also processes authentication data such as passwords at input time, password hashes, JWTs, refresh tokens, and internal service tokens.

The implementation avoids making every service a full personal data owner. For example, `auth-service` stores refresh session metadata and `userId`, while `user-service` remains the source of profile data.

## Decision

Apply personal data minimization by keeping data in the service that needs it and storing snapshots only when they are required for business continuity or async delivery.

Current examples:

- `auth-service` stores refresh token hashes and session metadata, not full profiles;
- `book-service` stores `ownerId`, not owner email or profile fields;
- `exchange-service` stores participant ids, optional request message, and book/user snapshots needed for exchange history and notification payloads, but resolves phone numbers from `user-service` only for allowed HTTP responses and does not persist them;
- `notification-service` persists derived in-app notification text for the recipient and processed event ids for idempotency.

## Consequences

- A service breach exposes less unrelated personal data.
- Async notifications do not need `notification-service` to call `user-service` or `book-service` to build text.
- Snapshots and notification text can contain personal data, so retention policy and centralized log controls remain required.
- Data minimization does not eliminate the need for legal basis, access control, backups, incident response, and attestation-ready infrastructure.

## Alternatives considered

- **Replicate full user profile data into each service.** Easier display logic, but violates minimization and increases breach impact.
- **Resolve every display field synchronously at read time.** Less duplicated data, but fragile for notifications and historical exchange records.
- **Store only numeric ids everywhere.** Minimizes data, but produces poor notifications and loses useful historical context if books or profiles later change.

## Status

Accepted.

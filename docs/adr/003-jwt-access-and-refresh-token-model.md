# 003. JWT Access And Refresh Token Model

## Context

Public user APIs need stateless authentication across `user-service`, `book-service`, `exchange-service`, and `notification-service`. The repository also supports session renewal and logout through `auth-service`.

The current implementation issues HMAC-signed JWT access tokens and opaque refresh tokens generated with `SecureRandom`. Refresh tokens are stored only as HMAC-SHA-256 hashes with a server-side pepper in `auth_db.refresh_tokens`.

## Decision

Use short-lived JWT access tokens for API authorization and opaque refresh tokens for session renewal.

`auth-service` owns login, registration, refresh, logout, logout-all, and refresh token storage. Refresh rotates the token pair: the previous refresh token is revoked, a new token in the same family is stored, and a new access token is created from current claims loaded from `user-service`.

Reuse of an already revoked refresh token is treated as suspicious. The token family is marked for audit and active refresh tokens in that family are revoked.

## Consequences

- Downstream services can validate access tokens locally without an introspection call on every request.
- Refresh token compromise has reduced impact because only hashes are stored and reuse can revoke the whole token family.
- Blocking a user prevents new login and refresh because `auth-service` consults `user-service`, but already issued access JWTs remain valid until expiration.
- `JWT_SECRET` and `AUTH_REFRESH_TOKEN_PEPPER` are high-value secrets and must be generated, stored, rotated, and audited through deployment infrastructure.

## Alternatives considered

- **Server-side sessions only.** Easier immediate revocation, but introduces shared session storage and service coupling.
- **JWT without refresh tokens.** Simpler, but worse user experience and no controlled session renewal/logout model.
- **JWT introspection on every request.** Stronger centralized control, but adds latency and runtime dependency on `auth-service` for all services.

## Status

Accepted.

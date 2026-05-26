# 004. Internal Token Protection

## Context

Some APIs are not intended for frontend clients:

- `auth-service` calls `user-service` endpoints for profile creation, authentication, and claims loading;
- `exchange-service` calls `book-service` endpoints to inspect and transition book state.

These routes are exposed under `/internal/**` in provider services and are hidden from Swagger through `@Hidden`.

## Decision

Protect internal APIs with the `X-Internal-Token` header and a shared `INTERNAL_TOKEN` secret. Provider services grant an internal authority only when the header value matches the configured token.

The token check is an application-level defense. Production deployment must additionally keep internal routes unreachable from the public internet through gateway-only exposure, network segmentation, firewall rules, and secret management.

## Consequences

- Internal endpoints are distinct from public JWT-protected user APIs.
- Local development remains simple because services can communicate through HTTP with a shared environment variable.
- Compromise of `INTERNAL_TOKEN` affects trusted service-to-service operations, so rotation and storage in a secret manager are required outside local development.
- The model does not provide per-service mutual identity; mTLS or signed service tokens can be considered later.

## Alternatives considered

- **No internal authentication, network-only isolation.** Too weak for accidental exposure and local misconfiguration.
- **mTLS between services.** Stronger identity but heavier certificate lifecycle for the current project scope.
- **OAuth2 client credentials for services.** More standardized, but introduces an authorization server flow that is not present in this repository.

## Status

Accepted.

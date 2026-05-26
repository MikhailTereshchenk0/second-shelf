# 006. API Gateway For Frontend Entrypoint

## Context

The repository now contains `api-gateway`, a Spring Cloud Gateway module on port `8088`. It routes frontend paths to domain services, centralizes CORS with `FRONTEND_ALLOWED_ORIGINS`, preserves `Authorization`, and creates or forwards `X-Correlation-Id`.

Local Compose still publishes individual service ports for debugging. Production-like Compose publishes only the gateway and keeps domain services, PostgreSQL, and RabbitMQ on the internal network.

## Decision

Use `api-gateway` as the recommended frontend and production entry point. Public clients should call gateway routes rather than direct service ports.

JWT validation remains in downstream services for the current implementation. The gateway is responsible for routing, CORS, and correlation id propagation, not for centralized authorization.

## Consequences

- Frontend integration has a single base URL and one CORS policy.
- Gateway-only exposure supports network segmentation and limits accidental publication of service ports.
- Downstream services still need their own security filters because the gateway does not validate JWTs.
- Swagger, Actuator, RabbitMQ, and database endpoints must remain restricted by deployment configuration in production.

## Alternatives considered

- **Direct frontend calls to all services.** Useful for local debugging, but creates fragmented CORS, larger public surface, and more complex frontend configuration.
- **External reverse proxy only.** Works for routing, but would not keep route configuration and correlation id behavior in the application repository.
- **Gateway with centralized JWT validation.** A possible future improvement, but the current downstream validation model is already implemented and tested.

## Status

Accepted.

# HTTP API Contracts

> Per-route HTTP contract reference for the spring-ai-ascend platform v1 API.
> Version: 1.0.0-W0 | Last refreshed: 2026-05-13 (post-seventh third-pass)

The full OpenAPI specification is at [openapi-v1.yaml](openapi-v1.yaml). This document provides the human-readable contract reference, mandatory header conventions, and status code semantics.

---

## Header conventions

| Header | Format | Scope | Exempt paths |
|--------|--------|-------|--------------|
| X-Tenant-Id | UUID (RFC 4122, 36 chars) | Required on all mutable routes | /v1/health, /actuator/**, GET-only routes that are operator probes |
| Idempotency-Key | UUID (RFC 4122, 36 chars) | Required on all POST/PUT/PATCH routes | /v1/health, /actuator/**, GET routes |

Header validation is performed by the filter chain:

- `TenantContextFilter` (order 20): validates `X-Tenant-Id` is a well-formed UUID; returns 400 on malformed input; returns 403 if the tenant claim in the JWT does not match the header value (W1+).
- `IdempotencyHeaderFilter` (order 30): validates `Idempotency-Key` is a well-formed UUID; returns 400 on malformed input.

Exempt paths never require these headers. They are always accessible without authentication in dev posture, and without tenant binding in all postures.

---

## Status code conventions

| Code | Meaning |
|------|---------|
| 200 | Success; response body present |
| 201 | Created; response body contains the created resource |
| 202 | Accepted; run is in progress; poll GET /v1/runs/{id} |
| 400 | Bad request; malformed header (X-Tenant-Id or Idempotency-Key), invalid request body, or schema validation failure |
| 403 | Security deny; Spring Security `AuthorizationManager` denial; tenant/JWT cross-check failure; or missing required header in research/prod |
| 404 | Resource not found; or resource exists but belongs to a different tenant (tenant isolation) |
| 409 | Conflict; duplicate run within an idempotency scope (run already in progress) |
| 410 | Gone; endpoint removed or deprecated |
| 429 | Rate limit exceeded or tenant token budget exhausted |
| 500 | Internal server error; not expected in normal operation; emits `springai_ascend_filter_errors_total` |
| 503 | Service unavailable; dependency health check failed |

All error responses use the `ContractError` JSON envelope:

```json
{
  "code": "MISSING_TENANT_HEADER",
  "message": "X-Tenant-Id header is required for this route",
  "traceId": "<opentelemetry-trace-id>"
}
```

---

## Route contracts

### GET /v1/health

| Attribute | Value |
|-----------|-------|
| Stability | stable (W0) |
| Wave | W0 |
| Required headers | none |
| Auth | exempt in all postures |
| Response schema | HealthResponse (see openapi-v1.yaml) |
| HealthEndpointIT | GREEN at commit 97b0827 |

Response body (200 OK):

```json
{
  "status": "UP",
  "sha": "<git-sha>",
  "db_ping_ns": 12345,
  "ts": "2026-05-10T08:00:00Z"
}
```

This route is the W0 operator probe. Kubernetes-native liveness and readiness split (`/actuator/health/liveness`, `/actuator/health/readiness`) is deferred to W2 — see `architecture-status.yaml` capability `health_endpoint_liveness_split` (L0).

---

### POST /v1/runs (shipped; W1)

| Attribute | Value |
|-----------|-------|
| Stability | shipped |
| Wave | W1 |
| Required headers | X-Tenant-Id (UUID), Idempotency-Key (UUID) |
| Auth | JWT required in research/prod (W1) |
| Response schema | 202 + TaskCursor (Cursor Flow per Rule R-F; see `openapi-v1.yaml`) |
| Implementation | `agent-service/src/main/java/.../web/runs/RunController.java#createRun` (line 66) |

Creates a new agent run for the authenticated tenant. The endpoint returns immediately with `202 Accepted` + a TaskCursor; the client polls the cursor or subscribes via SSE/Webhook (Rule R-F Cursor Flow Mandate). The run is assigned a UUID run id and starts in PENDING status. The Idempotency-Key is scoped per tenant; the same key submitted twice returns the first response. rc12 K-ζ marked this route shipped (previously stale `planned;W1` per rc11 review P2-1).

---

### GET /v1/runs/{id} (shipped; W1)

| Attribute | Value |
|-----------|-------|
| Stability | shipped |
| Wave | W1 |
| Required headers | X-Tenant-Id (UUID) |
| Auth | JWT required in research/prod (W1) |
| Response schema | RunResponse |
| Implementation | `agent-service/src/main/java/.../web/runs/RunController.java#getRun` (line 123) |

Returns the current state of a run. Returns 404 if the run does not exist or belongs to a different tenant. rc12 K-ζ marked this route shipped (previously stale `planned;W1`).

---

### POST /v1/runs/{id}/cancel (shipped; W1)

| Attribute | Value |
|-----------|-------|
| Stability | shipped |
| Wave | W1 |
| Required headers | X-Tenant-Id (UUID), Idempotency-Key (UUID) |
| Auth | JWT required in research/prod (W1) |
| Response schema | RunResponse |
| Implementation | `agent-service/src/main/java/.../web/runs/RunController.java#cancelRun` (line 141); enforced by Rule R-J.b tenant re-authorization |

Cancels a live run. Returns 200 + terminal RunResponse if successful. Returns 403 `tenant_mismatch` if the request tenant differs from `Run.tenantId` (Rule R-J.b re-authorization). Returns 404 if the run does not exist. Returns 409 `illegal_state_transition` if the run is already in a terminal stage. Idempotent terminal→terminal same-status returns 200 (per Rule R-J.b kernel). rc12 K-ζ marked this route shipped (previously stale `planned;W1`).

Note: this shipped HTTP cancel edge is separate from the `RunLifecycle` SPI design-only contract — that SPI remains deferred to W2 for resume/retry/cancel orchestration; the shipped W1 HTTP cancel is independently re-authorized by Rule R-J.b at the edge. See `architecture-status.yaml#run_lifecycle_spi.allowed_claim` for the boundary.

---

## Actuator routes (stable; W0)

| Route | Description |
|-------|-------------|
| GET /actuator/health | Spring Boot Actuator health; includes DB and dependency checks |
| GET /actuator/health/liveness | Kubernetes liveness probe |
| GET /actuator/health/readiness | Kubernetes readiness probe |
| GET /actuator/prometheus | Prometheus metrics scrape endpoint |

These routes are exempt from tenant and idempotency header requirements. They should be restricted to internal network access in research/prod posture.

---

## Related documents

- [openapi-v1.yaml](openapi-v1.yaml) for the full machine-readable OpenAPI specification
- [contract-catalog.md](contract-catalog.md) for the full contract inventory
- [agent-service/ARCHITECTURE.md](../../agent-service/ARCHITECTURE.md) for filter chain and controller design

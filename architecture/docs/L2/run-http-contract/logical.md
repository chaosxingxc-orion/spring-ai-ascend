---
level: L2
view: logical
feature: run-http-contract
status: scaffold
relates_to:
  - "architecture/docs/L1/agent-service/logical.md"
  - "architecture/docs/L1/agent-service/features/access-layer.md"
  - "docs/contracts/openapi-v1.yaml"
authority: "ADR-0040 (W1 HTTP contract reconciliation) + ADR-0070 (Cursor Flow) + ADR-0068 (Layered 4+1)"
---

# `run-http-contract` — Logical View (wire contract)

> **Scaffold placeholder.** This file is the L2 detail sink for the
> `POST /v1/runs` **wire contract**. The binding wire authority is
> [`../../../../docs/contracts/openapi-v1.yaml`](../../../../docs/contracts/openapi-v1.yaml)
> operation `createRun`; everything below is a readable expansion of that
> operation, NOT a second source of truth. The full prose migration out of the
> L1 / L0 surfaces (the run-lifecycle status behaviour that the layer-purity
> verdict flagged as L2 detail) lands in a later reconcile wave. No field name,
> status code, or operation id below is minted here — each is cited from the
> OpenAPI source.

## 1. Operation surface

| Verb + route | OpenAPI `operationId` | Success | Cursor / body |
|---|---|---|---|
| `POST /v1/runs` | `createRun` | `202 Accepted` | returns `TaskCursor` (Cursor Flow per ADR-0070; the request never blocks). |
| `GET /v1/runs/{runId}` | `getRun` | `200 OK` | returns `RunResponse`. |
| `POST /v1/runs/{runId}/cancel` | `cancelRun` | `200 OK` | state transition (DELETE intentionally NOT used for run resources). |

Required headers on every non-health route (source: OpenAPI `parameters`):

- `X-Tenant-Id` (`TenantIdHeader`) — caller-asserted tenant UUID, cross-checked
  against the JWT `tenant_id` claim.
- `Idempotency-Key` (`IdempotencyKeyHeader`) — UUID-shaped key, required on
  mutating verbs (`POST` / `PUT` / `PATCH`).
- `Authorization: Bearer <JWT>` (`bearerAuth`) — validated against the
  configured JWKS endpoint.

## 2. `POST /v1/runs` request body — `CreateRunRequest`

Source: `openapi-v1.yaml#/components/schemas/CreateRunRequest`.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `capabilityName` | string | required, `minLength: 1`, `maxLength: 128` | Identifier of the capability/agent to invoke. W1 surface; future waves add tool config, budget envelopes, parent run id. |

## 3. `POST /v1/runs` success response — `TaskCursor` (202)

Source: `openapi-v1.yaml#/components/schemas/TaskCursor`.

| Field | Type | Required | Notes |
|---|---|---|---|
| `runId` | string (uuid) | yes | Server-allocated run identifier; stable for the run's lifetime. |
| `status` | enum | yes | One of `PENDING\|RUNNING\|SUSPENDED\|SUCCEEDED\|FAILED\|CANCELLED\|EXPIRED`; always `PENDING` at cursor issuance. |
| `cursor_url` | string (uri) | yes | Absolute URL the client polls (`GET`) or subscribes to (SSE). |
| `webhook_url` | string (uri) | no | Optional callback URL for blocking checkpoints (human-in-the-loop). |

`GET /v1/runs/{runId}` returns `RunResponse` (`runId`, `status`,
`capabilityName`, `createdAt`, `updatedAt`) — source
`openapi-v1.yaml#/components/schemas/RunResponse`.

## 4. Run-lifecycle HTTP status-code matrix

This is the wire-level status behaviour that the layer-purity verdict ruled
belongs at L2 (not in L0 §4 or the L1 logical/process prose). Source: the
`responses` blocks of `createRun`, `getRun`, `cancelRun` in `openapi-v1.yaml`,
plus the `code` enumeration in `ErrorEnvelope`.

| Status | `POST /v1/runs` | `GET /v1/runs/{runId}` | `POST .../cancel` | `error.code` |
|---|---|---|---|---|
| 200 | — | run state (`RunResponse`) | cancel applied / idempotent same-status terminal | — |
| 202 | run accepted (`TaskCursor`, PENDING) | — | — | — |
| 400 | malformed body / missing tenant context | `runId` not a UUID | invalid request | `invalid_request` |
| 401 | missing / invalid Bearer token | same | same | (auth) |
| 403 | `tenant_mismatch` / `jwt_missing_tenant_claim` | — | — | `tenant_mismatch` |
| 404 | — | unknown `runId` OR cross-tenant access | unknown / cross-tenant (tenant-scope-as-not-found) | `not_found` |
| 409 | `idempotency_conflict` / `idempotency_body_drift` | — | `illegal_state_transition` (target is SUCCEEDED / FAILED / EXPIRED) | `idempotency_conflict`, `idempotency_body_drift`, `illegal_state_transition` |
| 422 | `invalid_run_spec` (Bean Validation failure) | — | — | `invalid_run_spec` |

`ErrorEnvelope` shape (source `openapi-v1.yaml#/components/schemas/ErrorEnvelope`):
`{ error: { code: string, message: string, details?: object } }`.

## 5. Error envelope code vocabulary

Machine-readable, stable codes carried in `error.code` (source: OpenAPI
`ErrorEnvelope.code` description + the per-response descriptions above):

`tenant_mismatch` · `jwt_missing_tenant_claim` · `idempotency_conflict` ·
`idempotency_body_drift` · `illegal_state_transition` · `not_found` ·
`invalid_run_spec` · `invalid_request`.

## 6. Cross-references

- Idempotency body-lifetime sequence (claim hash, drift, replay):
  [`process.md`](process.md).
- Structural parent (where the ingress edge lives at L1):
  [`../../L1/agent-service/features/access-layer.md`](../../L1/agent-service/features/access-layer.md)
  (AS-L1-F01, AS-L1-F05) +
  [`../../L1/agent-service/logical.md`](../../L1/agent-service/logical.md) §3
  (RunStatus state machine — the `status` enum above).
- Binding wire authority:
  [`../../../../docs/contracts/openapi-v1.yaml`](../../../../docs/contracts/openapi-v1.yaml).
- Sink index: [`README.md`](README.md).

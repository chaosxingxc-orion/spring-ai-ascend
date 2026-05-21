# L1 Architecture Design Guidance for spring-ai-ascend

> Audience: spring-ai-ascend architecture team  
> Date: 2026-05-13  
> Scope: L1 module and service architecture after the L0 architecture release  
> Language: English-only architecture guidance  

## 1. Purpose

The L0 architecture release establishes the system boundary, target architecture, W0 shipped subset, deferred wave contracts, and truth gates for `spring-ai-ascend`. L1 architecture must now turn those system-level decisions into executable, module-level Spring architecture.

For this repository, L1 should not be treated as a maturity label. `AGENTS.md` states that the older L0-L4 maturity model has been replaced by the binary `shipped:` truth model in `docs/governance/architecture-status.yaml`. Therefore, this guidance uses "L1" to mean:

> Module-level architecture that converts L0 decisions into Spring Boot composition, HTTP contracts, persistence contracts, posture behavior, tests, and evidence.

L1 architecture is successful when an implementation team can build W1 capabilities without re-litigating boundaries, inventing hidden fallback paths, or making prose claims that exceed the shipped code.

## 2. Current Baseline

The current root architecture separates:

- Target architecture: the W1-W4 product contract.
- W0 shipped subset: the capabilities that currently run and have evidence.

The W0 shipped subset includes the health endpoint, posture-aware tenant and idempotency filters, orchestration SPIs, `Run` entity, run-state DFA, in-memory dev-posture executors, `ResilienceContract`, `GraphMemoryRepository` SPI scaffold, and contract-truth tests.

The following capabilities are explicitly staged for later waves and must not be described as shipped until their implementation and tests exist:

- LLM gateway
- Tool registry
- Outbox publisher
- Durable Postgres checkpointer
- ActionGuard
- Temporal workflow implementation
- Tenant GUC and RLS enforcement
- Streaming run handoff

L1 architecture must preserve this distinction.

## 3. Assumptions, Confusions, and Tradeoffs

### Assumptions

- L0 has accepted the platform's system boundary and wave model.
- W1 should focus on executable Spring contracts rather than broad runtime expansion.
- `agent-platform` and `agent-runtime` remain separate architectural concerns.
- `architecture-status.yaml` remains the source of truth for shipped claims.

### Potential Confusions

- "L1" must not mean "everything just below L0 is now implemented."
- "Spring module" must not mean "every package becomes Spring-aware."
- "Compatible" means same signature and same semantics, not only a similar class or method name.
- "Persisted" means survives restart, not only stored in an in-memory map.

### Tradeoffs

- L1 should be strict enough to prevent drift, but narrow enough to avoid premature W2-W4 implementation.
- Shared modules should be introduced only when they remove a real dependency problem.
- Spring Boot should own composition, while runtime domain contracts should stay plain Java where L0 already requires SPI purity.

## 4. Root-Cause Block for L1 Work

Observed issue: after L0, the main risk is not missing architecture prose; it is divergence between module implementation, Spring wiring, tests, and shipped claims.

Execution path: root `ARCHITECTURE.md` defines system-level target and W0 shipped behavior; module architecture files define local ownership; `architecture-status.yaml` records capability truth; implementation and tests must match all three.

Root cause statement: architecture drift happens when a future-wave capability is described in present tense or wired partially through Spring without implementation, tests, and `shipped:` evidence.

Evidence: the repository already guards this class of defect through active architecture truth rules, module boundary documents, and `architecture-status.yaml` shipped rows.

## 5. L1 Design Charter

Every L1 architecture artifact must answer the following questions:

1. What does this module own?
2. What does this module explicitly not own?
3. What public contracts does it expose?
4. Which Spring beans exist?
5. Which configuration class constructs each bean?
6. Which resources are durable, request-scoped, run-scoped, or in-memory only?
7. What changes across `dev`, `research`, and `prod`?
8. What tests prove the behavior?
9. Which `architecture-status.yaml` row owns the shipped claim?
10. Which wave owns deferred behavior?

If an L1 document cannot answer these questions, it is not ready for implementation.

## 6. Spring Module Rules

### 6.1 Spring Boot Is the Composition Root

Spring configuration classes should construct adapters and infrastructure resources. Runtime SPIs and domain entities should remain plain Java unless an ADR explicitly promotes them into a Spring adapter layer.

Recommended package roles:

- `web`: controllers, request DTOs, response DTOs, exception mappers.
- `auth`: JWT validation and authentication filter configuration.
- `tenant`: HTTP-edge tenant extraction and request binding.
- `idempotency`: request idempotency, deduplication, claim and replay semantics.
- `persistence`: Spring-managed repositories and database adapters.
- `config`: `@ConfigurationProperties`, validation, boot guards.
- `runtime/*/spi`: plain Java contracts, no Spring imports.
- `runtime/*/inmemory`: dev-posture reference implementations only.

### 6.2 One Construction Path Per Resource

Each shared-state resource must have one builder or factory owner:

- JWT decoder and JWKS cache
- `IdempotencyStore`
- database repositories
- checkpointer implementations
- resilience registries
- future LLM clients
- future tool registries
- future outbox publisher

Inline fallback construction is forbidden. Do not use patterns such as `x == null ? new DefaultX()` or `Optional.orElse(new DefaultX())` in production code paths.

### 6.3 Configuration Properties Must Be Consumed

Every configuration field must have a downstream consumer or be removed. L1 documents should list the owning configuration prefix, required fields by posture, validation constraints, and startup failure behavior.

Recommended style:

- Bind config with `@ConfigurationProperties`.
- Validate config with constructor binding and Bean Validation.
- Fail during startup for missing `research` or `prod` requirements.
- Avoid reading raw environment variables at call sites.

### 6.4 Runtime SPIs Remain Framework-Clean

SPI packages under `com.huawei.ascend.runtime.*.spi.*` should continue to import only `java.*` unless a future ADR changes the rule. Spring, Micrometer, Reactor, persistence, and platform types belong in adapters, not SPIs.

## 7. Module Boundary Recommendations

### 7.1 agent-platform

`agent-platform` is the northbound facade. L1 architecture should keep it responsible for:

- HTTP routing
- request validation
- JWT validation
- tenant request binding
- idempotency entry behavior
- OpenAPI contract exposure
- edge-level observability

It must not own:

- LLM calls
- tool-calling loops
- run state-machine rules
- suspend and resume semantics
- graph or agent-loop execution
- memory repository semantics

W1 platform focus should be:

- JWT validation with RS256 and JWKS configuration.
- `tenant_id` claim cross-check against `X-Tenant-Id`.
- `IdempotencyStore` wiring with tenant-scoped claim/replay.
- consistent error envelope and status-code mapping.
- OpenAPI snapshot update.

### 7.2 agent-runtime

`agent-runtime` is the cognitive runtime kernel. L1 architecture should keep it responsible for:

- `Run` lifecycle semantics
- run-state DFA validation
- orchestration SPIs
- suspend and resume contracts
- checkpointer contracts
- run repository contracts
- resilience contract routing
- future runtime adapters

It must not import HTTP-edge request context such as `TenantContextHolder`.

W1 runtime focus should be limited. Unless a real W1 API handoff requires runtime changes, avoid expanding into LLM gateway, outbox, tool registry, ActionGuard, or Temporal packages during L1/W1 work.

### 7.3 Shared Contracts

Introduce `agent-platform-contracts` only when a real platform-runtime handoff needs shared DTOs or value types. Do not create it as speculative architecture scaffolding.

If introduced, it should contain only stable boundary contracts:

- request and response records
- enum/value objects needed by both modules
- validation annotations only if they do not force runtime SPI impurity

It should not contain:

- Spring controllers
- runtime executor implementations
- persistence adapters
- LLM provider code

## 8. HTTP API Contract Standards

L1 should define W1 HTTP APIs before implementation.

Recommended run API shape:

```text
POST /v1/runs
GET  /v1/runs/{runId}
POST /v1/runs/{runId}/cancel
```

Design rules:

- Use plural resource names.
- Use verbs only for non-CRUD actions such as `cancel`.
- Use `POST /cancel`, not `DELETE`, for cancellation.
- Initial run status must be `PENDING`, not `CREATED`.
- Every mutating request must require `Idempotency-Key`.
- Every tenant-bound request must carry `X-Tenant-Id`.
- W1 must cross-check JWT `tenant_id` against `X-Tenant-Id`.

Recommended status codes:

| Case | Status |
|---|---:|
| malformed JSON or invalid UUID | 400 |
| missing or invalid JWT | 401 |
| JWT tenant mismatch | 403 |
| resource not found within tenant scope | 404 |
| duplicate in-flight idempotency claim | 409 |
| semantically invalid run request | 422 |
| quota or capacity limit | 429 |
| temporary dependency outage | 503 |

Error responses should be structured and stable:

```json
{
  "error": {
    "code": "tenant_mismatch",
    "message": "Request tenant does not match authenticated tenant.",
    "details": []
  }
}
```

Do not return `200` with an embedded failure object.

## 9. Tenant and Security Standards

### 9.1 Tenant Identity

W1 tenant identity should be resolved as follows:

1. `X-Tenant-Id` remains required.
2. JWT must include `tenant_id`.
3. Platform compares header tenant and JWT tenant.
4. Mismatch returns `403`.
5. Runtime receives tenant identity through explicit contracts, not through `TenantContextHolder`.

### 9.2 JWT Validation

W1 JWT validation should define:

- accepted algorithm: RS256
- JWKS URL configuration
- issuer validation
- audience validation
- expiration and not-before validation
- clock skew
- JWKS cache TTL
- failure metrics
- posture behavior for missing configuration

`dev` may allow a documented local mode if explicitly configured. `research` and `prod` must fail closed.

### 9.3 Secrets

Secrets must not appear in source, test snapshots, logs, architecture documents, or example commands. L1 config docs should name required environment variables without giving real values.

### 9.4 Authorization

Authentication alone is insufficient. Every tenant-scoped lookup must be scoped by tenant in the repository method or query. A resource that exists for another tenant must behave as not found unless policy explicitly says otherwise.

## 10. Idempotency Standards

W1 should promote idempotency from header validation to durable tenant-scoped deduplication.

Required contract:

- key scope: `(tenant_id, idempotency_key)`
- mutating methods: `POST`, `PUT`, `PATCH`
- first request claims the key
- concurrent duplicate returns `409`
- completed duplicate replays the stored response when replay is supported
- failed request behavior is explicit
- stored record includes request hash to detect key reuse with different body
- all behavior is posture-aware

Recommended persistence shape:

```text
idempotency_dedup
  tenant_id
  idempotency_key
  request_hash
  status
  response_status
  response_body_ref
  created_at
  completed_at
  expires_at
```

L1 must define transaction boundaries before implementation. The idempotency claim and downstream state mutation should be designed so duplicate requests cannot create duplicate side effects.

## 11. Persistence Standards

L1 persistence design should specify:

- table ownership
- primary keys
- tenant columns
- unique constraints
- transaction boundaries
- isolation assumptions
- migration ownership
- repository method semantics
- pagination behavior for unbounded queries
- posture behavior when durable storage is absent

Rules:

- No unbounded repository methods for potentially large result sets.
- No string-concatenated SQL.
- Every tenant-owned table must include `tenant_id`.
- W2 RLS/GUC design must not be claimed as W1 shipped behavior.
- In-memory stores are acceptable only for dev or W0 reference behavior when explicitly gated.

## 12. Observability Standards

Every L1 capability should define logs and metrics at design time.

Minimum signals:

- JWT validation failure count
- tenant mismatch count
- idempotency missing key count
- idempotency conflict count
- idempotency replay count
- posture boot failure count
- repository failure count
- run cancellation request count when run API lands

Metric rules:

- custom metrics use the `springai_ascend_*` prefix
- tenant tagging must follow the ADR-0023 direction when W1 tenant metrics are introduced
- avoid high-cardinality labels such as raw run IDs, idempotency keys, JWT subjects, or request bodies

Log rules:

- no secrets
- no raw JWTs
- no full request bodies by default
- include correlation fields such as tenant and request ID where safe
- failed security decisions log at `WARNING` or higher

## 13. Testing Standards

Every L1 capability should define all three layers before code is written.

### Layer 1: Unit

Unit tests should cover:

- pure validation
- state transition behavior
- request hash behavior
- config validation
- repository contract edge cases using fakes only when the dependency is external

### Layer 2: Integration

Integration tests should use real Spring wiring for the subsystem under test:

- real filter chain for auth and tenant behavior
- real `IdempotencyStore` implementation when W1 store lands
- real configuration binding
- real database container when persistence behavior is under test

Do not mock the subsystem being tested and still call the test integration.

### Layer 3: E2E or Contract

E2E or contract tests should assert through public interfaces:

- HTTP status code
- response body
- OpenAPI snapshot
- persistence-visible outcome where appropriate
- emitted metric or log signal for critical failure paths

## 14. Architecture Truth Standards

Every shipped L1 capability must update evidence in the same change:

- implementation path
- tests list
- `allowed_claim`
- module architecture file when module behavior changes
- root architecture only when system boundary or cross-module contract changes
- ADR when a durable architecture decision is made

Forbidden claim patterns:

- present-tense prose for deferred W2-W4 behavior
- "implemented" without tests
- "enforced by X" when X does not enforce the named property
- "compatible" when only a name matches
- "persistent" for in-memory state
- "tenant isolated" without tenant-scoped enforcement

## 15. Recommended W1 Sequence

The architecture team should sequence W1 as follows:

1. Freeze the W1 HTTP contract for authentication, tenant identity, idempotency, and initial run handoff.
2. Define posture bootstrapping and required configuration behavior.
3. Implement JWT validation and tenant claim cross-check in `agent-platform`.
4. Implement tenant-scoped durable idempotency claim/replay.
5. Add or defer `agent-platform-contracts` based on real handoff needs.
6. Update OpenAPI snapshot and contract tests.
7. Add integration tests for `dev` and `research` posture behavior.
8. Update `architecture-status.yaml` only for capabilities with implementation and green tests.
9. Run architecture-sync and module test gates.
10. Publish a W1 delivery note that distinguishes shipped behavior from W2-W4 contracts.

## 16. L1 Review Checklist

Before approving an L1 architecture design, reviewers should verify:

- The module owner is clear.
- The out-of-scope list is explicit.
- No future-wave capability is described as shipped.
- Spring bean construction has one owner.
- Configuration properties are validated and consumed.
- Tenant identity flow is explicit.
- Idempotency behavior is tenant-scoped.
- Persistence survives restart when claimed.
- Error status codes are stable.
- Metrics and logs exist for failure paths.
- Tests cover unit, integration, and public contract layers.
- `architecture-status.yaml` truth matches implementation.
- The design does not weaken existing Rule 20, Rule 21, or Rule 25 constraints.

## 17. Bottom Line

L0 defines what `spring-ai-ascend` is allowed to become. L1 defines how each Spring module is allowed to become it.

For the architecture team, the practical rule is simple:

> Do not add Spring wiring, shared contracts, persistence, or HTTP surface unless ownership, posture behavior, tenant scope, idempotency behavior, tests, and shipped evidence are defined together.

That discipline is what will keep W1 from becoming a collection of plausible Spring classes that silently violate the L0 contract.

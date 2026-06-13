---
level: L1
view: scenarios
module: agent-service
status: active
freeze_id: null
covers_views: [scenarios]
spans_levels: [L1]
authority: "ADR-0078 (agent-service consolidation) + ADR-0068 (Layered 4+1) + ADR-0059 (Code-as-Contract) + ADR-0100 (rc22 5-component decomposition + Run≤Task≤Session≤Memory lifecycle) + ADR-0136..0139 (rc53 vocabulary reconciliation + 5-layer L1 ratification + Fast/Slow Path narrowed semantics) + ADR-0140..0145 (rc55 Engine Adapter split + Internal Event Queue design_only + Run aggregate single owner + review-log demotion + Layer↔Package matrix + sealed RunEvent hierarchy) + ADR-0155 (PR 92 v1.2 absorption — 6 boundary-decision reversals + 14 inter-module contracts + 5 new SPIs)"
---

# agent-service — L1 architecture (2026-05-26 rc55 canonical materialization)

> **STATUS — agent-runtime pure rebuild (ADR-0159):** `agent-service` is now a serviceization-facade **skeleton**; its former runtime is consolidated into **`agent-runtime`**. Wherever this document references `EngineRegistry` / `ExecutorAdapter` / `EngineEnvelope` / `EngineMatchingException` / `EngineHookSurface` / `HookPoint` / `RuntimeMiddleware` / `HookDispatcher` / `resolve(envelope)`, that engine/hook design is **RETIRED / design_only / historical** — no such Java type exists. The current shipped dispatch lives in `agent-runtime`: `EngineDispatcher` -> `AgentRuntimeHandler` (routed by `agentId` via `AgentRuntimeHandlerRegistry`; unknown `agentId` -> terminal `AGENT_ID_INVALID`; single `control` write authority; egress gated by `control`), verified by `EngineDispatcherTest` / `EngineClosedLoopIntegrationTest`. See Rule R-M and `architecture/docs/L1/agent-runtime/`.

> Owner: AgentService team | Wave: W0..W3 | Maturity: shipped (post-Phase-C consolidation of agent-platform + agent-runtime + rc22 5-component decomposition + rc53 5-layer L1 4+1 ratification + rc55 canonical 4+1 materialization under `docs/architecture/l0/l1/agent-service/`)
> Last refreshed: 2026-05-26 (rc55 — canonical 4+1 source moved to `docs/architecture/l0/l1/agent-service/` per ADR-0143; this file narrowed to scenarios-view-only with shipped-state grounding)
> Governing rule: Rule R-C — Code-as-Contract (formerly Rule 28; ADR-0059 + ADR-0086 namespace ratchet).
> Every constraint below maps to at least one row in `docs/governance/enforcers.yaml`.

> **ADR-0159 status (2026-06-03):** `agent-service` is re-founded as the **enterprise serviceization façade skeleton** (registration/discovery deferred to a dedicated ADR). The HTTP edge + cognitive runtime kernel documented below were **consolidated into `agent-runtime`** (package root `com.huawei.ascend.runtime.*`); the Run lifecycle kernel is now a design target there. This file is **retained as the runtime L1 design pending physical migration to `architecture/docs/L1/agent-runtime/`** — every `agent-service/src/...` path and `service.{platform,runtime}.*` package referenced below describes code **relocated to `agent-runtime` per ADR-0159**. agent-runtime owns it now; agent-service is the façade.

## 0.5 Canonical L1 4+1 View Source (rc55 materialization — ADR-0143)

The canonical 4+1 view of this module (Scenarios + Logical + Process + Development + Physical) lives as 5 per-view files under `docs/L1/agent-service/`, per [ADR-0143](../docs/adr/0143-review-log-demotion-l1-canonical-move.yaml):

- **Index:** [`docs/L1/agent-service/README.md`](../docs/L1/agent-service/README.md)
- **Scenarios View:** [`docs/L1/agent-service/scenarios.md`](../docs/L1/agent-service/scenarios.md) — S1-S5 canonical scenarios
- **Logical View:** [`docs/L1/agent-service/logical.md`](../docs/L1/agent-service/logical.md) — 5-layer diagram (with ADR-0140 5a/5b Engine Adapter split + ADR-0142 Run aggregate single-owner + ADR-0141 Internal Event Queue design_only sub-section), ER, state machines, SuspendSignal flow, RunEvent hierarchy per ADR-0145, vocabulary glossary
- **Process View:** [`docs/L1/agent-service/process.md`](../docs/L1/agent-service/process.md) — sequence diagrams P1-P6 (including the cancel-race-loser sequence O3)
- **Physical View:** [`docs/L1/agent-service/physical.md`](../docs/L1/agent-service/physical.md) — 5-plane deployment + RLS + 3-track bus + sandbox
- **Development View:** [`docs/L1/agent-service/development.md`](../docs/L1/agent-service/development.md) — package tree (cross-walked vs filesystem) + Layer↔Package matrix per [ADR-0144](../docs/adr/0144-layer-vs-package-matrix.yaml) + 5 L2 Boundary Contracts (Rule G-1.1.c)
- **SPI Appendix:** [`docs/L1/agent-service/spi-appendix.md`](../docs/L1/agent-service/spi-appendix.md) — 9 active SPIs with 4-way parity (Rule G-1.1.b)

**Historical note:** the rc53 review file [`docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md`](../../../../docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md) (+ `.cn.md` sibling) was the original authoring surface for the §§14-20 view content. Per ADR-0143 it is now a **historical authoring record** (freeze-marked); the canonical 4+1 source is the per-view files above. Where the rc53 review file and the canonical per-view files disagree, the canonical files win.

The §1+ prose below is **shipped-state grounding** for the agent-service module — purpose, shipped components, dependencies, posture defaults, tests, wave plan, risks. It is NOT a substitute for the 4+1 views above; the 4+1 views are the canonical architectural surface, and this file is the canonical *implementation-grounding* surface that cross-links to them.

## 1. Purpose

Historically (pre-ADR-0159), `agent-service` was the **consolidated edge + kernel module**: it owned the HTTP edge — accepting requests, binding them to a tenant, validating idempotency keys, validating JWT, and serving `/v1/runs` (subpackage `com.huawei.ascend.service.platform.*`, formerly `com.huawei.ascend.platform.*`) — AND the **cognitive runtime kernel** that drives LLMs through tool-calling loops, runs the Run state machine, and dispatches engine envelopes (subpackage `com.huawei.ascend.service.runtime.*`, formerly `com.huawei.ascend.runtime.*`), all within a single deployable. Per **ADR-0159** that entire runtime — edge + kernel — is **consolidated into `agent-runtime`** (`com.huawei.ascend.runtime.*`), and `agent-service` is re-founded as the serviceization façade skeleton; the description below documents that relocated runtime design pending its physical migration to `architecture/docs/L1/agent-runtime/`. The HTTP edge **trusts the kernel only via the kernel's published SPI surface** (Run repository, Orchestrator, RunRepository), preserving the original platform↛runtime layering as a sub-package invariant enforced by Rule R-C.e (formerly Rule 21, ADR-0078). See `docs/adr/0078-agent-service-consolidation.yaml` for the merger rationale (supersedes ADR-0055, extends ADR-0066, relates_to ADR-0026).

## 2. Shipped components

> Path convention: every Java path below is rooted at `agent-service/src/main/java/com/huawei/ascend/service/{platform,runtime}/...` **except where explicitly noted as living in `agent-bus` (neutral orchestration/engine SPI: `bus.spi.engine`, re-homed per ADR-0158; and S2C transport SPI: `bus.spi.s2c`, relocated from the dissolved agent-runtime-core per ADR-0088) or `agent-runtime` (engine adapter SPI: `engine.spi`, plus the `InProcessEnginePort` realization per ADR-0158)**. The engine SPI surface and the S2C SPI types were extracted to their own modules at the rc5 wave (2026-05-18) per ADR-0079; the transient kernel-shim module `agent-runtime-core` was dissolved in rc13 (2026-05-20) per ADR-0088 and its sources redistributed to semantic-home modules; ADR-0158 then re-homed the neutral orchestration/engine SPI to `agent-bus` as `bus.spi.engine` — see §2.B `runtime / orchestration` below. Test paths mirror the layout under `src/test/java/`.

### 2.A Platform-side concerns (subpackage `service.platform.*`)

#### platform / web -- HTTP front door (W0)

`HealthController` serves `GET /v1/health` → 200 + JSON body. This is
the only route live in W0. `/v3/api-docs` is exposed at W0 for contract
verification (gate: `OpenApiContractIT`). Swagger UI (HTML) is blocked
until W1. Virtual-thread dispatcher enabled via
`spring.threads.virtual.enabled=true`.

#### platform / tenant -- Per-request tenant binding (W0)

`TenantContextFilter` reads the `X-Tenant-Id` header (UUID shape),
builds a `TenantContext`, stores it in `TenantContextHolder` (request-
scoped ThreadLocal), and propagates `tenant_id` into the Logback MDC
for log correlation. No JWT claim is read at W0; no Postgres GUC is
set.

W1 will add JWT `tenant_id` claim cross-check against the existing
`X-Tenant-Id` header value (per ADR-0040); `X-Tenant-Id` remains
required at W1. W2 will add `SET LOCAL app.tenant_id` GUC and enable
RLS policies on tenant tables. See ADR-0027, ADR-0040, ADR-0023.

#### platform / idempotency -- Durable claim/replay (L1, ADR-0057)

`IdempotencyHeaderFilter` intercepts POST/PUT/PATCH requests, validates
the `Idempotency-Key` header as UUID, wraps the request in
`ContentCachingRequestWrapper`, hashes `method:path:body` (SHA-256 →
base64url), and calls `IdempotencyStore.claimOrFind(tenantId, key,
requestHash)`. Collisions return 409 `idempotency_conflict` (same hash)
or 409 `idempotency_body_drift` (different hash) via
`ErrorEnvelopeWriter`.

`IdempotencyStore` is a **platform-internal extension interface** (historical placement under `service.platform.idempotency`; predates Rule R-D.d's `.spi` package convention — not counted as an SPI in `contract-catalog.md §2`). Two impls wired by `IdempotencyStoreAutoConfiguration`:

- `JdbcIdempotencyStore` (default when DataSource present) — INSERT …
  ON CONFLICT (tenant_id, idempotency_key) DO NOTHING; SELECT on
  collision. The dedup table — PRIMARY KEY composite plus a CHECK
  constraint on `status` (CLAIMED|COMPLETED|FAILED) — is a design
  target; its Flyway migration ships with the agent-service
  implementation wave (no migration exists in the repo today).
- `InMemoryIdempotencyStore` — `ConcurrentHashMap`. Registered ONLY
  when `app.posture=dev` AND `app.idempotency.allow-in-memory=true`.

`IdempotencyProperties` (`@ConfigurationProperties("app.idempotency")`)
exposes `ttl` (default PT24H) and `allowInMemory` (default false).

Status transitions (CLAIMED → COMPLETED/FAILED) and response replay are
W2 work; L1 returns 409 for any duplicate and recovers via
`expires_at` TTL.

Enforcer rows: E12 (durability), E14 (body-drift),
E22 (allow-in-memory matrix); the schema-layer dedup enforcer lands
together with the migration.

#### platform / auth -- JWT validation (L1, ADR-0056)

`AuthProperties` (`@ConfigurationProperties("app.auth")`) holds issuer,
jwks-uri, audience, clock-skew (default PT60S), jwks-cache-ttl
(default PT5M), and dev-local-mode (default false). Cross-field
`@AssertTrue` rejects `dev-local-mode=true` together with a configured
`jwks-uri`.

`JwtDecoderConfig` wires exactly one `JwtDecoder` (Rule D-8, formerly Rule 6): JWKS-backed
when `app.auth.issuer` is set, dev-local-mode-backed when the flag is
set and posture is dev (`PostureBootGuard` rejects the combo
elsewhere). Validator chain: RS256 signature + `JwtTimestampValidator`
+ issuer + audience, wrapped in `CountingValidator` that emits
`springai_ascend_auth_failure_total{reason,source}`.

`WebSecurityConfig` is stateless, permits `/v1/health`,
`/actuator/{health,info,prometheus}`, `/v3/api-docs(/**)`, and requires
authentication everywhere else when a `JwtDecoder` bean is present.
Falls back to `denyAll` when no decoder is wired (preserves W0
zero-config dev behaviour; `PostureBootGuard` enforces fail-closed in
research/prod).

Enforcer rows: E9 (validation matrix), E11 (dev-local-mode posture
guard).

#### platform / tenant -- Header validation + JWT claim cross-check (L1, ADR-0056 §3)

`TenantContextFilter` (W0, unchanged) reads `X-Tenant-Id`, validates
UUID shape, populates `TenantContextHolder` (request-scoped
ThreadLocal) and Logback MDC.

`JwtTenantClaimCrossCheck` (L1, order 15 — after Spring Security's
`BearerTokenAuthenticationFilter`, before `TenantContextFilter` at 20)
compares the JWT `tenant_id` claim with the `X-Tenant-Id` header.
Mismatch → 403 `tenant_mismatch`; claim missing with header present →
403 `jwt_missing_tenant_claim`. Counters:
`springai_ascend_tenant_mismatch_total`,
`springai_ascend_jwt_missing_tenant_claim_total`.

Rule R-C.e (formerly Rule 21) retargeted at Phase C (ADR-0078): runtime sub-package main
sources MUST NOT import any class under
`com.huawei.ascend.service.platform..` (`ServiceRuntimeMustNotDependOnServicePlatformTest`,
enforcer E2). This is the same invariant that previously sat across
module boundaries (`agent-runtime ↛ agent-platform`); after Phase C it
sits across sub-package boundaries within `agent-service`.

Enforcer rows: E10 (cross-check), E2 (purity).

#### platform / posture -- Boot-time fail-closed gate (L1, ADR-0058)

`PostureBootGuard` listens on `ApplicationReadyEvent` and aborts startup
in research/prod when any of the required-config matrix entries is
missing: `AuthProperties.hasJwksConfig()`, DataSource bean,
JdbcIdempotencyStore bean, MeterRegistry bean; OR if
`InMemoryIdempotencyStore` is registered; OR if
`app.auth.dev-local-mode=true` outside posture=dev. Failures emit
`springai_ascend_posture_boot_failure_total{posture,reason}` then
throw `IllegalStateException` from the listener (which propagates and
aborts the application context).

`@RequiredConfig` annotation lands as documentation for the future
scanner; current matrix is encoded directly in the guard class.

Enforcer rows: E11, E21, E22.

#### platform / web / runs -- W1 HTTP run API (L1, plan §6)

`RunController` under `/v1/runs`:

- `POST /v1/runs` (consumes JSON `CreateRunRequest`) → 201 with status
  `PENDING` (initial state pinned by `RunStatusEnumTest`, enforcer E5;
  no `CREATED` state exists).
- `GET /v1/runs/{runId}` → 200 with current state; 404 `not_found` for
  unknown runs OR cross-tenant access (architect guidance §9.4
  "tenant-scope-as-not-found").
- `POST /v1/runs/{runId}/cancel` → 200 with `CANCELLED`; idempotent
  for already-cancelled runs; 409 `illegal_state_transition` for
  `SUCCEEDED`/`FAILED`/`EXPIRED` (enforcer E24). Cancellation is POST,
  never DELETE (enforcer E6).

`RunHttpExceptionMapper` (`@ControllerAdvice`) maps
`MethodArgumentNotValidException` → 422 `invalid_run_spec`,
`HttpMessageNotReadableException` → 400 `invalid_request`,
`IllegalArgumentException` → 400, uncaught `RuntimeException` →
500 `internal_error`. Every response uses `ErrorEnvelope`:
`{error:{code,message,details}}` — stable shape, enforcer E8.

`RunControllerAutoConfiguration` wires `InMemoryRunRegistry` (from the
runtime sub-package `service.runtime.orchestration.inmemory`) as the
`RunRepository` when `app.posture=dev` and no other repository bean
exists. Research/prod require a durable repository (W2); until then
`PostureBootGuard` aborts startup.

Enforcer rows: E5, E6, E7, E8, E24.

#### platform / observability -- Tenant tagging, forbidden-tag scrub, Telemetry Vertical filter (L1 + L1.x)

`TenantTagMeterFilter` (L1) registers a `MeterFilter` that strips
high-cardinality tag keys (`run_id`, `idempotency_key`, `jwt_sub`,
`body`) from every `springai_ascend_*` metric at registration time.
Non-namespace metrics (jvm.*, etc.) are left untouched.

`TraceExtractFilter` (L1.x — Telemetry Vertical, ADR-0061 / §4 #55)
runs at order 10 (before `JwtTenantClaimCrossCheck` at 15 and
`TenantContextFilter` at 20). It parses the W3C version-00
`traceparent` header on every inbound request; if absent or malformed
it originates a fresh `trace_id` (32-char hex) + `span_id` (16-char
hex). MDC is populated with `trace_id`, `span_id`, `parent_span_id`
during the request scope (cleared in `finally`). On every outbound
response the filter emits `traceresponse: 00-<trace_id>-<span_id>-01`
so the W3 client SDK (ADR-0063) can correlate. Counters:
`springai_ascend_trace_originated_total{posture, source=client|server}`,
`springai_ascend_traceparent_invalid_total{posture}`. No OpenTelemetry
SDK dependency at L1.x — pure-Java parsing and id minting.

Filter chain order (L1.x):

1. `TraceExtractFilter` (order 10) — Telemetry Vertical (NEW).
2. `JwtTenantClaimCrossCheck` (order 15) — JWT vs `X-Tenant-Id` cross-check.
3. `TenantContextFilter` (order 20) — UUID binding + MDC tenant_id.
4. `IdempotencyHeaderFilter` (default Spring Security ordering after 20)
   — `Idempotency-Key` validation + claim/replay.

`run_id` is populated in MDC by `RunController` at the spot where a
Run is materialised (not in a filter — the run is created inside the
controller, after the filter chain). This is a deliberate L1.x design
choice; W2 may move it to a request-scoped bean if a second
Run-materialising controller appears.

Enforcer rows: E18 (metric prefix), E19 (forbidden tag scrubber),
E38 (Telemetry Vertical first-class), E40 (traceparent at edge),
E41 (MDC field-shape).

#### platform / architecture -- Layering enforcers (L1, ADR-0055 → ADR-0078)

ArchUnit tests under
`agent-service/src/test/java/com/huawei/ascend/service/platform/architecture/`:

- `HttpEdgeMustNotImportMemorySpiTest` (E4) — HTTP edge cannot reach
  the runtime memory SPI.
- `ServicePlatformImportsOnlyServiceRuntimePublicApiTest` (E34, Phase K
  retargeted at Phase C) — `service.platform` may only import
  `service.runtime` public surface (`runs.*`, `orchestration.spi.*`,
  `posture.*`, plus `InMemoryRunRegistry`); other `service.runtime`
  packages stay internal.
- `RepositoryPaginationTest` (E16) — repository methods returning
  Collection/Page must declare Pageable. Armed for W1 persistence
  growth; vacuous at L1.
- `NoStringConcatSqlTest` (E17) — no String + SQL concatenation under
  `persistence/` or `idempotency/jdbc/`.
- `MetricNamingTest` (E18) — every Micrometer builder("…") starts with
  `springai_ascend_`.
- `RunStatusEnumTest` (E5) — pins the seven-status enum; no CREATED.
- `ErrorEnvelopeContractTest` (E8) — JSON shape exactly
  `{error:{code,message,details}}`.

### 2.B Runtime-side concerns (subpackage `service.runtime.*`)

#### runtime / orchestration -- Cognitive runtime SPI + reference impls (W0; neutral orchestration/engine SPI **owned by `agent-bus` as `bus.spi.engine` per ADR-0158 (transport-agnostic EnginePort boundary)**)

The cognitive runtime kernel's SPI contracts live in **two** modules after the
ADR-0079 (T2.B2) engine-extraction wave (2026-05-18) and the ADR-0158 EnginePort re-home:

- **`agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/`**
  (re-homed per ADR-0158) — neutral kernel SPI types `EnginePort`, `Orchestrator`,
  `RunContext`, `ExecutionContext`, `SuspendSignal`, `Checkpointer`, `TraceContext`,
  `RunMode`, and the sealed `ExecutorDefinition` hierarchy (`GraphDefinition` |
  `AgentLoopDefinition`).
- **`agent-runtime/src/main/java/com/huawei/ascend/engine/spi/`**
  (extracted per ADR-0079) — executor adapters `GraphExecutor`,
  `AgentLoopExecutor`, the unified `ExecutorAdapter`, `EngineHookSurface`, and
  `EngineMatchingException`.

`agent-service` owns the **posture-gated reference adapters** (not the SPI roots),
under
`agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/inmemory/`:
`SyncOrchestrator`, `SequentialGraphExecutor`,
`IterativeAgentLoopExecutor`, `InMemoryCheckpointer`,
`InMemoryRunRegistry`. These are **posture-gated** in-memory reference
implementations that fail-closed in research/prod via
`AppPostureGate`.

`NoopTraceContext` (L1.x — Telemetry Vertical, ADR-0061) provides the
default `TraceContext` impl when no OpenTelemetry SDK is on the
classpath.

#### runtime / runs -- Run entity + state machine (W0; **owned by `agent-service` post-ADR-0088 dissolution**)

After the rc13 ADR-0088 dissolution (2026-05-20), the Run entity, state machine,
and `RunRepository` SPI live directly in `agent-service` (relocated from the
transient agent-runtime-core module that ADR-0079 had briefly hosted them):

- **`agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/`** —
  `Run` (immutable record), `RunStatus` (formal DFA, 7 values: PENDING,
  RUNNING, SUSPENDED, CANCELLED, SUCCEEDED, FAILED, EXPIRED), `RunMode`
  discriminator, `RunStateMachine` (validates every `withStatus(newStatus)`
  transition; illegal transitions throw `IllegalStateException` per Rule R-C.d, formerly Rule 20).
- **`agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/spi/`** —
  `RunRepository` SPI (interface only; pure Java per Rule R-D, formerly Rule 32).

After rc13 / ADR-0088 dissolution, `agent-service` is the canonical owner of `runs/` kernel types. Its only orchestration-adapter contribution
on the runs axis is the posture-gated `InMemoryRunRegistry` reference adapter
under `agent-service/.../runtime/orchestration/inmemory/` listed above.

#### runtime / resilience -- Operation-routing SPI (W0, **`.spi` package home per ADR-0080**)

The `ResilienceContract` published SPI surface lives at
`agent-service/src/main/java/com/huawei/ascend/service/runtime/resilience/spi/`
(package `com.huawei.ascend.service.runtime.resilience.spi`): `ResilienceContract`,
`ResiliencePolicy`, `SkillResolution`, `SuspendReason`, `SkillCapacityRegistry`.
Implementations stay in the parent package
`agent-service/src/main/java/com/huawei/ascend/service/runtime/resilience/`:
`DefaultSkillResilienceContract`, `YamlResilienceContract`,
`YamlSkillCapacityRegistry`.

The runtime `ResilienceContract.resolve(tenant, skill)` consults
`docs/governance/skill-capacity.yaml` (Rule R-K.b, formerly Rule 41.b);
over-cap callers receive a rejected decision envelope
`SkillResolution.reject(SuspendReason.RateLimited)` per Rule R-K shipped
surface. Translating that decision into `RunStatus.SUSPENDED` is deferred
to Rule R-K.c (W2 scheduler admission). Chronos Hydration interlock per
Rule R-H (formerly Rule 38). The `.spi` package home was added at the rc6
wave (ADR-0080, 2026-05-18) to align the published-SPI surface with Rules
R-D / 77 / 78 — the same split pattern used by `agent-runtime`
(`engine.spi.*` vs `engine.runtime.*`).

#### runtime / memory -- Memory SPI shell (W0 shell)

`agent-service/src/main/java/com/huawei/ascend/service/runtime/memory/spi/GraphMemoryRepository.java`:
interface-only SPI. No adapter ships at W0; concrete impl arrives via
the `spring-ai-ascend-graphmemory-starter` autoconfiguration, which
points at `com.huawei.ascend.service.runtime.graphmemory.GraphMemoryAutoConfiguration`
post-Phase-C.

#### runtime / engine -- Engine envelope + registry (W2.x, **consumed from `agent-runtime` post-ADR-0079**)

`agent-service` **consumes** the `agent-runtime` module — the
engine SPI surface and the registry/envelope no longer live here. After
the rc5 wave (2026-05-18) ADR-0079 extraction:

- **Engine SPI surface** (package `com.huawei.ascend.engine.spi.*`,
  module `agent-runtime`): `ExecutorAdapter`, `GraphExecutor`,
  `AgentLoopExecutor`, `EngineHookSurface`, `EngineMatchingException`
  — sources live at
  `agent-runtime/src/main/java/com/huawei/ascend/engine/spi/`.
- **Engine registry + envelope home** (package
  `com.huawei.ascend.engine.runtime.*`, module `agent-runtime`):
  `EngineRegistry`, `EngineEnvelope` record (mirrors
  `docs/contracts/engine-envelope.v1.yaml`) — sources at
  `agent-runtime/src/main/java/com/huawei/ascend/engine/runtime/`
  (relocated from the legacy `service/runtime/engine/` path in rc14 per
  ADR-0090; ADR-0079's source-compat exception was retired since rc13
  ADR-0088 already broke any consumer binding to the kernel-shim module).
- **Reference engine adapters** stay in `agent-service` at
  `agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/inmemory/`:
  `SequentialGraphExecutor` (`extends GraphExecutor`),
  `IterativeAgentLoopExecutor` (`extends AgentLoopExecutor`),
  `SyncOrchestrator`, `InMemoryCheckpointer`, `InMemoryRunRegistry`.

Every Run dispatch goes through `EngineRegistry.resolve(envelope)`
(Rule R-M.a, formerly Rule 43); pattern-matching on
`ExecutorDefinition` subtypes outside the registry is forbidden.
Mismatch raises `EngineMatchingException` and transitions the Run to
FAILED with reason `engine_mismatch` (Rule R-M.b, formerly Rule 44; no
fallback policy). The intentional split-package arrangement (SPI under
`engine.spi.*`, registry/envelope under `engine.runtime.*` — both
inside `agent-runtime`; the registry/envelope home was
relocated from the legacy `service.runtime.engine.*` package in rc14
per ADR-0090) is documented in `agent-runtime/ARCHITECTURE.md`
Status section and protected by Rule 76 (no split SPI packages —
`engine.spi.*` is owned by exactly one module).

#### runtime / s2c -- Server-to-Client callback envelope (W2.x, ADR-0040 rc3, **SPI in `agent-bus.spi.s2c` (rc13 - relocated from dissolved agent-runtime-core per ADR-0088)**)

`agent-service` **consumes** the S2C SPI surface from `agent-bus` —
the SPI records and transport interface no longer live in this module. After
the rc13 wave (2026-05-20) ADR-0088 dissolution (which moved S2C ownership
from the dissolved agent-runtime-core to agent-bus):

- **S2C SPI surface** (package
  `com.huawei.ascend.bus.spi.s2c.*`, module `agent-bus`):
  `S2cCallbackEnvelope`, `S2cCallbackResponse`, `S2cCallbackTransport` —
  sources at `agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c/`.
- **Reference transport impl** stays in `agent-service` at
  `agent-service/src/main/java/com/huawei/ascend/service/runtime/s2c/InMemoryS2cCallbackTransport.java`
  (package `com.huawei.ascend.service.runtime.s2c`, non-`.spi` —
  implementation home).

The waiting Run suspends via `SuspendSignal.forClientCallback(...)` —
the checked-suspension variant introduced in the rc3 wave per ADR-0074
(unifies the prior unchecked `S2cCallbackSignal`, which was deleted).
The orchestrator marks the parent Run SUSPENDED with
`SuspendReason.AwaitClientCallback`. Callbacks consume the
`s2c.client.callback` skill capacity declared in
`docs/governance/skill-capacity.yaml` (Rule R-M.d, formerly Rule 46).

#### runtime / probe -- OSS classpath shape probe (W0)

`OssApiProbe` under
`agent-service/src/main/java/com/huawei/ascend/service/runtime/probe/`
is a plain class (not a Spring context test). `OssApiProbeTest` runs
three tests:

1. `classIsLoadable` — `OssApiProbe.class` loads without
   `NoClassDefFoundError`.
2. `probeReturnsNonNullString` — `probe.probe()` returns a non-null
   String.
3. `temporalGetVersionShapeReturnsMinusOne` — Temporal client stub
   returns -1 (confirms SDK is on classpath without a live server).

Green `OssApiProbeTest` is a required gate for every wave.

#### runtime / idempotency -- Contract-spine entity (W0)

`IdempotencyRecord` (post-rc13: relocated to `agent-service` per ADR-0088 dissolution) lives at
`agent-service/src/main/java/com/huawei/ascend/service/runtime/idempotency/`
and is the runtime-side contract-spine entity. It mirrors the persistence
shape (`tenantId`, `idempotencyKey`, `requestHash`, `status`,
`createdAt`, `expiresAt`) consumed by the platform-side
`IdempotencyStore` interface (historical platform-internal extension
point — see §2.A platform/idempotency for the Rule R-D.d note). Mandatory `tenantId` field is the trigger
condition for Rule R-C.c (formerly Rule 11) activation (Wave 4 Track B in the Phase C re-plan).

#### runtime / wave-staged placeholders (W2–W4)

The following packages remain as wave-staged placeholders — no Java
implementation ships at W0:

| Package | Purpose | Wave |
|---|---|---|
| `service.runtime.llm/` | LlmRouter, ChatClient beans, CostMetering | W2 |
| `service.runtime.outbox/` | Postgres-backed outbox + OutboxPublisher | W2 |
| `service.runtime.observability/` (kernel side) | Custom metrics, span propagation | W2 |
| `service.runtime.tool/` | MCP server registry, per-tenant tool allowlist | W3 |
| `service.runtime.action/` | ActionGuard 5-stage filter chain | W3 |
| `service.runtime.temporal/` | Temporal workflow + activity classes (long-running) | W4 |

## 3. Sub-package layering invariant (Phase C, ADR-0078)

**`service.runtime` MUST NOT import `service.platform`.** This invariant
preserves the original cross-module purity (formerly
`agent-runtime ↛ agent-platform`) as a within-module sub-package
purity post-Phase-C. It is enforced by **Rule R-C.e** (formerly Rule 21; retargeted from the
original "no `TenantContextHolder` import" invariant) via the ArchUnit
class **`ServiceRuntimeMustNotDependOnServicePlatformTest`** under
`agent-service/src/test/java/com/huawei/ascend/service/runtime/architecture/`,
registered as enforcer **E2** in `docs/governance/enforcers.yaml`. The
narrow original case — no import of `TenantContextHolder` (which now
lives at `service.platform.tenant.TenantContextHolder`) — remains
asserted independently as defence-in-depth.

The reverse direction (`service.platform → service.runtime`) is
permitted **only** to the runtime public surface, enforced by
`ServicePlatformImportsOnlyServiceRuntimePublicApiTest` (E34). The
allowed runtime public packages are: `service.runtime.runs.*`,
`bus.spi.engine.*`, `service.runtime.posture.*`, and
the dev-posture-gated `service.runtime.orchestration.inmemory.InMemoryRunRegistry`.

Authority: `docs/adr/0078-agent-service-consolidation.yaml` (supersedes
ADR-0055, extends ADR-0066, relates_to ADR-0026).

## 4. OSS dependencies

Dependency versions are managed by the parent POM (`pom.xml`) and the
`spring-ai-ascend-dependencies` BoM. Module architecture files do not
duplicate version pins — consult `pom.xml` properties for canonical
values (keys: `spring-ai.version`, `temporal.version`, `mcp.version`,
`resilience4j.version`, `caffeine.version`, `testcontainers.version`).

| Dependency | Role | Side |
|---|---|---|
| Spring Boot starter web (see parent POM) | HTTP server, MVC controllers + filters | platform |
| Spring Security OAuth2 Resource Server | JWT validation (RS256 + JWKS) | platform |
| Spring Security | Filter chain ordering | platform |
| HikariCP | DB pool (L1 alongside durable `JdbcIdempotencyStore`) | platform |
| Flyway | Schema migrations (idempotency dedup table; tenant tables land W2) | platform |
| Hibernate Validator | `@Valid` on DTOs + `@ConfigurationProperties` cross-field checks | platform |
| Jackson | JSON serialization | platform |
| Spring Boot actuator | Lifecycle + health + Prometheus scrape | both |
| Micrometer + Prometheus | Metrics (`springai_ascend_*` prefix) | both |
| Spring AI (see parent POM) | `ChatClient` abstraction + provider bindings | runtime |
| MCP Java SDK | Tool protocol (per-tenant MCP server registry, W3) | runtime |
| Temporal Java SDK | Durable workflows for runs > 30 s (W4) | runtime |
| Apache Tika | Document-parser reference tool (W3) | runtime |
| Resilience4j | Circuit breaker on LLM + tool calls | runtime |
| Caffeine | In-process cancel-flag cache | runtime |

## 5. Public contract

- HTTP: REST, JSON. Versioned URL prefix `/v1/`.
- Auth: Bearer JWT, RS256, JWKS URL configured at boot (W1).
- Idempotency: `Idempotency-Key` header on POST/PUT/PATCH (W0: UUID validation; W1: dedup).
- Tenant: `X-Tenant-Id` header required at W0 and W1; W1 adds JWT `tenant_id` claim cross-check (ADR-0040).

## 6. Posture-aware defaults

| Aspect | dev | research | prod |
|---|---|---|---|
| Missing `X-Tenant-Id` | warn + DEV_DEFAULT | reject 400 | reject 400 |
| Weak JWT alg (HS256) | accept w/ warning (W1) | reject (W1) | reject (W1) |
| `Idempotency-Key` missing on POST/PUT/PATCH | accept w/ warning | reject 400 | reject 400 |
| LLM provider mock allowed | yes | no | no |
| Vault required for provider keys | no | yes | yes |
| Token budget enforced | off | on | on |
| OPA policy required | warn-only | enforced | enforced |
| Temporal for runs > 30 s | warn | enforced | enforced |
| Outbox sink (not log appender) | optional | required | required |

## 7. Tests

### L1 shipped tests (all green; Testcontainers ITs guarded by `@Testcontainers(disabledWithoutDocker = true)`; remaining tests are pure JUnit)

| Test | Layer | Asserts | Side |
|---|---|---|---|
| `HealthEndpointIT` | Integration | `/v1/health` 200 + body | platform |
| `TenantContextFilterIT` | Integration | UUID binding, dev-default, 400 on missing in research | platform |
| `IdempotencyHeaderFilterIT` | Integration | UUID validation, 400 on missing, header passthrough | platform |
| `PostureBindingIT` | Integration | `APP_POSTURE` env-var bridge wired | platform |
| `OpenApiContractIT` | Integration | live `/v3/api-docs` agrees with pinned `openapi-v1.yaml` for every `/v1/**` path | platform |
| `AuthPropertiesValidationTest` | Unit | `app.auth.*` binding + cross-field consistency check | platform |
| `JwtValidationIT` | Integration | real Nimbus decoder + RSA keypair, every failure row of ADR-0056 §4 (enforcer E9) | platform |
| `JwtDevLocalModeGuardIT` | Integration | `dev-local-mode=true` is fatal outside `app.posture=dev` (enforcer E11) | platform |
| `JwtTenantClaimCrossCheckTest` | Unit | claim==header / claim!=header / missing-claim / no-auth branches (enforcer E10) | platform |
| `IdempotencyStoreTest` | Unit | In-memory dev-posture store contract | platform |
| `IdempotencyStorePostgresIT` | Integration | JDBC `INSERT … ON CONFLICT` + body-drift returns existing hash (enforcer E14) | platform |
| `IdempotencyDurabilityIT` | Integration | row persists across simulated downstream failure (enforcer E12) | platform |
| `InMemoryIdempotencyAllowFlagIT` | Integration | in-memory store posture-gated (enforcer E22) | platform |
| `PostureBootGuardIT` | Integration | research/prod fail-closed on missing config (enforcer E21) | platform |
| `RunHttpContractIT` | Integration | unauthenticated 401/403 + authenticated matrix (`createReturnsPending`, `getCrossTenantRunReturns404` (cross-tenant access collapses to 404 not_found at W0 per Rule R-J.b; legacy `tenantMismatchReturns403` name retired per AUD-2026-05-27 PR77-P2-4), `cancelTerminalReturns409`, `duplicateIdempotencyKeyReturns409`, `cancel_route_is_post_not_delete`); enforcers E5/E6/E7/E10/E24 | platform |
| `RunStatusEnumTest` | Unit | enum pinned at 7 values; no `CREATED` (enforcer E5) | runtime |
| `ErrorEnvelopeContractTest` | Unit | every 4xx/5xx response has `{error:{code,message,details}}` shape (enforcer E8) | platform |
| `TenantTagMeterFilterTest` | Unit | forbidden high-cardinality tags stripped from `springai_ascend_*` (enforcer E19) | platform |
| `ServicePlatformImportsOnlyServiceRuntimePublicApiTest` | ArchUnit | platform sub-package may only import the runtime sub-package public-API packages (enforcer E34) | platform |
| `ServiceRuntimeMustNotDependOnServicePlatformTest` | ArchUnit | runtime sub-package MUST NOT import any platform sub-package (Rule R-C.e, formerly Rule 21, enforcer E2) | runtime |
| `HttpEdgeMustNotImportMemorySpiTest` | ArchUnit | HTTP edge cannot reach the memory SPI (enforcer E4) | platform |
| `ApiCompatibilityTest` | ArchUnit | module-dep direction + SPI purity (W0 baseline) | both |
| `JwtTestFixture` | Test fixture | shared RSA keypair + JWT mint helper for L1 authenticated tests (enforcer E37) | platform |
| `OssApiProbeTest` | Unit | OSS classpath shape (3 tests; no Spring context) | runtime |
| `RunStateMachineTest` | Unit | Legal + illegal DFA transitions; EXPIRED terminal | runtime |
| `TelemetryVerticalArchTest` | ArchUnit | §4 #53 — adapter classes must not write `TraceContext` outside hook/observability packages | runtime |
| `RunContextIdentityAccessorsTest` | ArchUnit | §4 #54 — `RunContext` exposes `traceId()` / `spanId()` / `sessionId()` / `traceContext()` returning declared types | runtime |
| `RunTraceSessionConsistencyIT` | Integration | §4 #54 — `Run.traceId` non-null hex when populated; nullable column tolerated at L1.x; child Run inherits sessionId via Checkpointer | runtime |
| `LlmGatewayHookChainOnlyTest` | ArchUnit | §4 #56 — no `service.runtime.llm.*` class imports `ChatModel` outside `HookChain` package (arms for W2) and Wave C1 `SpringAiChatModelGateway` remains a design-only shell until hook binding ships | runtime |
| `SpanTenantAttributeRequiredTest` | ArchUnit | §4 #57 — emission sites declare `tenant.id` attribute (vacuous at L1.x; arms for W2) | runtime |
| `McpReplaySurfaceArchTest` | ArchUnit | §4 #59 — no `@RestController` resides in `web/replay/`, `web/trace/`, or `web/session/` | runtime |
| `PostureBootPiiHookPresenceContractIT` | Integration | §4 #58 — boot-gate contract for `PiiRedactionHook` in research/prod (full negative test W2) | runtime |
| `RunTest` | Unit | Run record construction, withStatus(), withSuspension() | runtime |
| `InMemoryCheckpointerTest` | Unit | save/load/clear round-trip | runtime |
| `OrchestrationSpiArchTest` | ArchUnit | SPI packages import only java.* | runtime |
| `TenantPropagationPurityTest` | ArchUnit | Rule R-C.e (formerly Rule 21): runtime sub-package never imports TenantContextHolder | runtime |
| `NestedDualModeIT` | Integration | 3-level graph→agent-loop→graph nesting via SuspendSignal | runtime |
| `RunStatusTransitionIT` | Integration | SUSPENDED→RUNNING→SUCCEEDED state transitions | runtime |
| `SuspendSignalTest` | Unit | SuspendSignal construction + childRunId accessor | runtime |

### W2-deferred tests (currently `@Disabled` — scaffold only)

- Platform side: `TenantIsolationIT`, `GucEmptyAtTxStartIT`, `RlsPolicyCoverageIT` — enable when V2__tenant_rls.sql lands + GUC wired + RLS policies active (W2).
- Runtime side: `RunHappyPathIT`, `RunCancellationIT`, `ActionGuardE2EIT`, `OutboxAtLeastOnceIT`, `LongRunResumeIT`.

## 8. Out of scope at L1

- `SET LOCAL` GUC, Postgres RLS policies: W2.
- Spring Cloud Gateway, per-tenant config overrides: W2–W3.
- Three-track `RunDispatcher`, streaming `Flux<RunEvent>` handoff: W2.
- LLM provider integrations beyond mocks (`service.runtime.llm/`): W2.
- Per-tenant MCP tool registry (`service.runtime.tool/`): W3.
- ActionGuard 5-stage filter chain (`service.runtime.action/`): W3.
- Temporal workflow + activity classes (`service.runtime.temporal/`): W4.

## 9. Wave plan / risks

> **Phase C consolidation lands 2026-05-18 (ADR-0078).** The previous
> two L1 modules `agent-platform` and `agent-runtime` were merged into
> a single deployable `agent-service`. The original cross-module
> purity rule (`agent-runtime ↛ agent-platform`) is preserved as a
> sub-package purity rule (`service.runtime ↛ service.platform`) under
> Rule R-C.e (formerly Rule 21), enforced by `ServiceRuntimeMustNotDependOnServicePlatformTest`.

### 9.1 Wave landing

- **W0 (delivered 2026-05-13)**: platform side — `web/` (HealthController),
  `bootstrap/` (PlatformApplication + AppPosture), actuator, `tenant/`
  (TenantContextFilter — header binding + MDC), `idempotency/`
  (IdempotencyHeaderFilter — UUID validation on POST/PUT/PATCH),
  `probe/` (OssApiProbe). Runtime side — orchestration SPI contracts
  (`Orchestrator`, `GraphExecutor`, `AgentLoopExecutor`, `SuspendSignal`,
  `Checkpointer`, `ExecutorDefinition`, `RunContext`); `Run` entity,
  `RunStatus` formal DFA, `RunStateMachine` validator; posture-gated
  in-memory reference executors (`SyncOrchestrator`,
  `SequentialGraphExecutor`, `IterativeAgentLoopExecutor`,
  `InMemoryCheckpointer`, `InMemoryRunRegistry`); `ResilienceContract`
  operation-routing SPI; `GraphMemoryRepository` SPI scaffold;
  `OssApiProbe`; `IdempotencyRecord` contract-spine entity.
- **W1 / L1 (delivered 2026-05-14)**: platform side — `auth/`
  (AuthProperties + JwtDecoderConfig — JWKS-backed + dev-local-mode RSA
  fixture), `tenant/JwtTenantClaimCrossCheck` (cross-check against
  `X-Tenant-Id` header per ADR-0056 §3), `idempotency/` claim/replay
  store (`JdbcIdempotencyStore` + `InMemoryIdempotencyStore` +
  `IdempotencyHeaderFilter` body-hash claim/replay per ADR-0057),
  `posture/PostureBootGuard` (fail-closed startup in research/prod per
  ADR-0058), `web/runs/` (RunController + CreateRunRequest +
  RunResponse + RunHttpExceptionMapper for `POST /v1/runs`,
  `GET /v1/runs/{runId}`, `POST /v1/runs/{runId}/cancel`),
  `observability/TenantTagMeterFilter` (strips forbidden
  high-cardinality tags from `springai_ascend_*` metrics). Runtime
  side — Telemetry Vertical TraceContext SPI (ADR-0061).
- **Phase C (delivered 2026-05-18, ADR-0078)**: merger of `agent-platform` + `agent-runtime` → `agent-service`. Package rename `com.huawei.ascend.platform.*` → `com.huawei.ascend.service.platform.*` and `com.huawei.ascend.runtime.*` → `com.huawei.ascend.service.runtime.*`. Rule R-C.e (formerly Rule 21) retargeted; ArchUnit class renamed `RuntimeMustNotDependOnPlatformTest` → `ServiceRuntimeMustNotDependOnServicePlatformTest`. Old modules deleted; reactor count decremented by 1.
- **W2**: `config/`, tenant GUC + RLS, Spring Cloud Gateway routing,
  OTel auto-instrumentation, durable `RunRepository` (Postgres-backed
  beyond the L1 in-memory dev-posture wiring), streaming run event
  handoff; runtime side — LLM gateway (`service.runtime.llm/`), outbox
  publisher (`service.runtime.outbox/`).
- **W3+**: per-tenant config overrides via Spring Cloud Config;
  PowerShell mirror of Rule 28a–28j sub-checks (deferred at L1 per
  ADR-0060 §3); LLM gateway resilience routing; tool registry
  (`service.runtime.tool/`); ActionGuard (`service.runtime.action/`).
- **W4**: Temporal workflow + activity classes
  (`service.runtime.temporal/`) for long-running runs.

### 9.2 Risks

- **Virtual-thread + JDBC pinning** (active at L1): HikariCP wired at
  L1 alongside the durable `JdbcIdempotencyStore`. Watch for unexpected
  `parkNanos`/`Unsafe.park` pinning under load; monitor
  `springai_ascend_*` pool metrics (`hikaricp.connections.pending`,
  `hikaricp.connections.usage`).
- **Spring Security 6/Boot 4 filter ordering**: filters are registered
  with explicit `FilterRegistrationBean` order —
  `JwtTenantClaimCrossCheck` at 15, `TenantContextFilter` at 20,
  `IdempotencyHeaderFilter` after that. `RunHttpContractIT` proves the
  full chain end-to-end.
- **Idempotency claim→completion window** (W2 trigger): if an
  orchestrator crashes after `claimOrFind` but before marking
  COMPLETED, the row stays CLAIMED until expires_at. Acceptable at L1
  (replays return the original 201); W2 will add an orchestrator-side
  completion hook per ADR-0057 §4.
- **Tenant-id confusion in multi-step requests**: every async handoff
  sources tenant from `RunContext.tenantId()` (Rule R-C.e, formerly Rule 21, enforced by
  `TenantPropagationPurityTest` ArchUnit +
  `ServiceRuntimeMustNotDependOnServicePlatformTest`), not from
  `TenantContextHolder`. Phase C retargeting preserves this exact
  invariant across the sub-package boundary.
- **Sub-package purity drift** (Phase C-specific): a developer
  refactoring inside `service.runtime.*` may inadvertently import a
  type from `service.platform.*` (now visually closer in the source
  tree). Mitigation: `ServiceRuntimeMustNotDependOnServicePlatformTest`
  runs in every `mvn verify` cycle, and an EXPLICIT FAIL self-test in
  `gate/test_architecture_sync_gate.sh` injects a synthetic
  `service.runtime → service.platform` import to assert the gate
  catches it.
- **Engine extraction landed** (T2.B2, ADR-0079, 2026-05-18; package
  rename completed rc14 per ADR-0090): the engine SPI surface moved to
  `agent-runtime` at `com.huawei.ascend.engine.spi.*`
  (`ExecutorAdapter` and friends), and `EngineRegistry` + `EngineEnvelope`
  moved to the same module under `com.huawei.ascend.engine.runtime.*`
  (relocated from the legacy `service.runtime.engine.*` package in rc14
  per ADR-0090; ADR-0079's source-compat exception was retired since
  rc13 ADR-0088 already broke any consumer binding to the kernel-shim
  module). The intentional split-package arrangement is documented in
  `agent-runtime/ARCHITECTURE.md` Status section.
  `EngineRegistry.resolve` boundary remains asserted by Rule R-M.a
  (formerly Rule 43)
  enforcer E84; consumed cross-module via the `agent-runtime` (rc13 dissolution per ADR-0088) →
  `agent-runtime` → `agent-service` dependency chain.

## 10. Roadmap

- Deferred capabilities and design decisions: `deferred_sub_clauses:` block in each alphanumeric rule card under `docs/governance/rules/` (parent-rule frontmatter); legacy rules awaiting human review at `docs/governance/escalations.md` (the prior `docs/CLAUDE-deferred.md` monolith was retired 2026-05-28). Current delivery state per wave (W0..W4): `docs/governance/architecture-status.yaml` (structured capability ledger; the prior `docs/STATE.md` pointer was removed at the rc6 wave — never landed on disk).
- Wave engineering plan: `ARCHITECTURE.md §1 + docs/governance/architecture-status.yaml + per-card deferred_sub_clauses blocks under docs/governance/rules/` (per ADR-0037; engineering-plan-W0-W4.md archived).
- Phase C consolidation specification: `docs/adr/0078-agent-service-consolidation.yaml`; execution plan: `docs/plans/phase-c-merge.md`.

---

## 11. L1 Runtime-Role Decomposition (rc22 / ADR-0100)

The 2026-05-21 proposal `docs/logs/reviews/2026-05-21-agent-service-l1-expansion-proposal.en.md` addresses `agent-service` concentration risk. Ratified by ADR-0100, the module decomposes into **5 logical runtime-role components**:

| # | Component (sub-package) | Role |
|---|---|---|
| 1 | `dispatcher/` — Polymorphic Dispatcher | Unified entry point for BOTH local function-call and remote bus-call invocations |
| 2 | `orchestrator/` — Reactive Orchestrator | Task tempo control, backpressure request handling, A2A protocol envelope packaging |
| 3 | `task/` — Task Center | TaskControlState persistence (`Task` entity + `TaskStateStore` SPI — canonical name; the historical `TaskRepository` label was retired per AUD-2026-05-27 AUD-PARITY-1; lifecycle: Run ≤ Task) |
| 4 | `session/` — Session Manager | Middle/long-context data management; "context projection" toward compute nodes (`Session` entity + `ContextProjector` SPI) |
| 5 | `engine/adapter/` + `engine/spi/` — Execution Engine Adapter | Masks Workflow vs ReAct engine differences; pure-function compute injection (`StatelessEngine` SPI) |

Existing `runtime/` package (Run / RunContext / RunStateMachine) stays unchanged.

### 11.1 Lifecycle hierarchy (rc22 ratifies; rc25 implements)

```
Run     — transient compute snapshot (compute pointer + delta)
Task    — control state (done-or-not, why-stopped)
Session — data context (what was discussed, variables)
Memory  — knowledge state (consumed via GraphMemoryRepository SPI per ADR-0082)
```

TaskID and SessionID are logically decoupled: one Session may concurrently execute multiple Tasks; one Task may drift across multiple Sessions (e.g., group-chat collaboration). The join semantics + audit trail are documented in ADR-0100 §non_goals.

### 11.2 New SPI surface (3 interfaces — rc22 declares; rc24 ships impls)

| Interface FQN | SPI package | Purpose |
|---|---|---|
| `com.huawei.ascend.service.engine.spi.StatelessEngine` | `service.engine.spi` | Pure-function `Execute(TaskMetadata, InjectedContext) → StateDelta`; engine holds no state |
| `com.huawei.ascend.service.session.spi.ContextProjector` | `service.session.spi` | Projects a `SessionContext` view from full Session history (truncation / summarization policy) |
| `com.huawei.ascend.service.task.spi.TaskStateStore` | `service.task.spi` | TaskControlState persistence interface |

### 11.3 AgentInvokeRequest contract (rc22 declares; rc24 wires runtime)

Wire shape: `docs/contracts/agent-invoke-request.v1.yaml` (status `design_only` at rc22). Service is the Read-Modify-Write closure boundary; Engine is the Pure-Function compute boundary.

### 11.4 Yield + SuspendSignal coexistence (rc22 — ADR-0100 Rejection 4 counter-decision)

- `SuspendSignal` (CHECKED EXCEPTION) remains canonical for state-machine suspension. Rule R-G + ArchUnit tests + rc8/rc9 cancellation paths intact.
- `Yield` becomes a new `HookPoint.ON_YIELD` cooperative-scheduling hint (added to `engine-hooks.v1.yaml` in rc22). Engine asks orchestrator to be rescheduled without persistence transition.

Counter to the 2026-05-21 proposal's §2.3 / §5.3 "abandon exception-based suspension" framing: the two mechanisms coexist, they do not replace each other. ADR-0100 §decision documents the full rationale.

### 11.5 A2A protocol adoption — CONTRACT ONLY (rc22 — ADR-0100 Rejection 3)

The 2026-05-21 proposal's §5.1 calls for "fully embedding the a2a-java SDK". REJECTED at the SDK level: A2A protocol alignment proceeds at the contract layer (`docs/contracts/a2a-envelope.v1.yaml` in a future ADR) without an SDK runtime dependency. ADR-0100 §non_goals records the policy.

---

## 12. Development View (Rule G-1.1.a — rc22 / ADR-0099)

Target directory tree (current namespace; rc22.5 migrates to `com.huawei.ascend.*` per ADR-0104):

```text
agent-service/
└── src/main/java/
    └── com/huawei/ascend/service/
        ├── platform/                          # HTTP edge (current; §2.A)
        │   ├── auth/                          # JwtDecoderConfig, AuthProperties, JwtTenantClaimCrossCheck
        │   ├── engine/                        # StatelessEngineAutoConfiguration and adapter wiring
        │   ├── tenant/                        # TenantContextFilter, TenantContextHolder, MDC binding
        │   ├── idempotency/                   # IdempotencyHeaderFilter, IdempotencyStore (historical platform interface; not under .spi per Rule R-D.d), jdbc/, inmemory/
        │   ├── observability/                 # TenantTagMeterFilter, TraceExtractFilter
        │   ├── persistence/                   # DataSource / database presence conditions
        │   ├── posture/                       # PostureBootGuard
        │   ├── probe/                         # platform probe auto-configuration
        │   ├── resilience/                    # resilience auto-configuration
        │   └── web/                           # HealthController, runs/RunController, runs/RunHttpExceptionMapper
        ├── runtime/                           # Run kernel (current; §2.B)
        │   ├── runs/                          # Run, RunStatus, RunStateMachine, RunMode, spi/RunRepository
        │   ├── orchestration/                 # inmemory/ (SyncOrchestrator, SequentialGraphExecutor, IterativeAgentLoopExecutor, InMemoryCheckpointer, InMemoryRunRegistry)
        │   ├── resilience/                    # DefaultSkillResilienceContract, YamlResilienceContract, YamlSkillCapacityRegistry, spi/ (ResilienceContract, ResiliencePolicy, SkillResolution, SuspendReason, SkillCapacityRegistry)
        │   ├── memory/                        # spi/GraphMemoryRepository
        │   ├── s2c/                           # InMemoryS2cCallbackTransport (consumes bus.spi.s2c)
        │   ├── idempotency/                   # IdempotencyRecord contract-spine entity
        │   ├── evolution/                     # evolution export boundary hooks
        │   ├── posture/                       # runtime posture helpers
        │   └── probe/                         # OssApiProbe
        ├── dispatcher/                        # rc22 — Polymorphic Dispatcher (sub-package declared; impl rc23)
        ├── orchestrator/                      # rc22 — Reactive Orchestrator (sub-package declared; impl rc23)
        ├── task/                              # rc22 — Task Center (sub-package declared; impl rc23-25)
        │   └── spi/                           # rc22 — TaskStateStore SPI
        ├── session/                           # rc22 — Session Manager (sub-package declared; impl rc23-25)
        │   └── spi/                           # rc22 — ContextProjector SPI
        └── engine/
            ├── adapter/                       # rc23 — ExecutionEngineAdapter (StatelessEngine consumer impls)
            └── spi/                           # rc22 — StatelessEngine SPI
```

NOTE: The new sub-packages (`dispatcher/`, `orchestrator/`, `task/`, `session/`, `engine/{adapter,spi}/`) are DECLARED in rc22 (package-info.java + SPI interfaces only) — bulk Java refactor is rc23 scope per ADR-0100 timeline. The existing `platform/` + `runtime/` sub-packages remain unchanged at rc22.

Mode-A (Platform-Centric per ADR-0101): `agent-service` on platform.
Mode-B (Business-Centric per ADR-0101): `agent-service` deploys on the business department's servers / client devices alongside `agent-runtime` for zero-latency local execution loops.

## *SPI Interface Appendix* (Rule G-1.1.b — rc22 / ADR-0099)

`agent-service` publishes 9 active Java SPI interfaces as of rc43 (cross-validates against `module-metadata.yaml#spi_packages`, `docs/contracts/contract-catalog.md`, `docs/dfx/agent-service.yaml`). Records, sealed carriers, and enums in the same packages are listed separately and are not counted as SPI interfaces.

### Active Java SPI interfaces

| Interface FQN | SPI package | Purpose | Status |
|---|---|---|---|
| `com.huawei.ascend.service.runtime.runs.spi.RunRepository` | `service.runtime.runs.spi` | Run persistence (in-memory ref impl ships; durable W2) | shipped |
| `com.huawei.ascend.service.runtime.memory.spi.GraphMemoryRepository` | `service.runtime.memory.spi` | Memory SPI (consumer impl in spring-ai-ascend-graphmemory-starter) | shipped |
| `com.huawei.ascend.service.runtime.resilience.spi.ResilienceContract` | `service.runtime.resilience.spi` | Operation-routing SPI (`resolve(tenant, skill)`) | shipped |
| `com.huawei.ascend.service.runtime.resilience.spi.SkillCapacityRegistry` | `service.runtime.resilience.spi` | Tenant × skill capacity lookup | shipped |
| `com.huawei.ascend.service.engine.spi.StatelessEngine` | `service.engine.spi` | NEW rc22 — pure-function engine SPI per ADR-0100 | declared (impl rc24) |
| `com.huawei.ascend.service.session.spi.ContextProjector` | `service.session.spi` | NEW rc22 — projects SessionContext | declared (impl rc24) |
| `com.huawei.ascend.service.task.spi.TaskStateStore` | `service.task.spi` | NEW rc22 — TaskControlState persistence | declared (impl rc24) |
| `com.huawei.ascend.service.agent.spi.Agent` | `service.agent.spi` | rc43 — first-class Agent entity per ADR-0128; HTTP-edge customer registration surface; binds ModelGateway + Skill + Memory + (optional) Planner | design_only |
| `com.huawei.ascend.service.agent.spi.AgentRegistry` | `service.agent.spi` | rc43 — tenant-scoped (tenantId, agentId) registry per ADR-0128 | design_only |

### SPI-adjacent structural carriers

These types live near the SPI packages because they are request, response, or decision carriers. They are contract-relevant, but they are not extension interfaces and are not included in the 9-interface count (canonical post-rc43 count of public Java SPI interfaces under any `spi/*.java` package within `agent-service/src/main/java/`; the legacy "7-interface count" phrasing was retired per AUD-2026-05-27 AUD-PARITY-2).

| Carrier type | Home | Purpose |
|---|---|---|
| `ResiliencePolicy` | `service.runtime.resilience.spi` | Per-operation policy carrier returned by resilience resolution |
| `SkillResolution` | `service.runtime.resilience.spi` | Sealed accept/reject decision envelope |
| `SuspendReason` | `service.runtime.resilience.spi` | Sealed reason taxonomy for suspension and rate-limit decisions |
| `AgentInvokeRequest` | `service.engine.spi` | Immutable service-to-engine invocation carrier |
| `StateDelta` | `service.engine.spi` | Immutable engine result carrier with typed run transition hint |
| `Session` | `service.session` | Session aggregate used by the reference projector |
| `Task` | `service.task` | Task aggregate used by the reference task-state store |

## *L2 Constraint Linkage* (Rule G-1.1.c — rc22 / ADR-0099)

Vacuously green at rc22. Future L2 designs likely include: (a) Run lifecycle state-machine extended for Run ≤ Task ≤ Session decoupling (rc25); (b) Reactive Orchestrator backpressure protocol (rc23-25); (c) Postgres RLS migration sequence (rc25). Each L2 doc MUST carry a Boundary Contracts sub-section when authored.

## Deployment loci (rc22 / ADR-0101)

`deployment_loci: [platform_centric, business_centric]` — supports both modes. In Mode-B the module deploys on the business side alongside `agent-runtime`.

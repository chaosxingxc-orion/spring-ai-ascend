---
level: L1
view: scenarios
module: agent-service
status: active
freeze_id: null
covers_views: [logical, development, process, physical, scenarios]
spans_levels: [L1]
authority: "ADR-0078 (agent-service consolidation) + ADR-0068 (Layered 4+1) + ADR-0059 (Code-as-Contract)"
---

# agent-service — L1 architecture (2026-05-18 Phase C consolidation)

> Owner: AgentService team | Wave: W0..W3 | Maturity: shipped (post-Phase-C consolidation of agent-platform + agent-runtime)
> Last refreshed: 2026-05-18 (Phase C — single-deployable consolidation)
> Governing rule: Rule 28 — Code-as-Contract (ADR-0059). Every constraint
> below maps to at least one row in `docs/governance/enforcers.yaml`.

## 0.4 Layered 4+1 view map (W1 — ADR-0068)

This document is the **L1 root** for the `agent-service` module. Until the full 4+1 view reorganisation lands, the table below classifies each existing major section against the 4+1 view taxonomy consumed by `gate/build_architecture_graph.sh`:

| Section | View | Notes |
|---|---|---|
| §1 Purpose | scenarios | module mission (platform edge + runtime kernel under one deployable) |
| §2.A platform / web, runs, architecture | logical | HTTP contract surface + ArchUnit layering enforcers |
| §2.A platform / tenant, idempotency, observability | process | per-request binding, filter chain + durability, trace propagation |
| §2.A platform / auth, posture | logical / scenarios | JWT validation matrix; boot-time fail-closed gate |
| §2.B runtime / orchestration, resilience, memory, engine | logical | Orchestrator / GraphExecutor / AgentLoopExecutor / ResilienceContract / GraphMemoryRepository / EngineRegistry SPI |
| §2.B runtime / runs, s2c, idempotency | process | Run entity + RunStatus DFA, S2cCallbackEnvelope + suspend semantics, IdempotencyRecord spine |
| §2.B runtime / probe | development | OssApiProbe (Spring AI + Temporal classpath shape) |
| §3 Sub-package layering invariant | logical | `service.runtime` ↛ `service.platform` (Rule 21) |
| §4 OSS dependencies | development | dependency direction + BoM authority |
| §5–§9 Wave plan / risks | scenarios | rollout + audit hooks + Phase C landing note |

P1-4 follow-up (L1-expert-review 2026-05-14, carried through Phase C): legacy §§4–9 boundary contradictions resolved by Phase C; cross-module-as-cross-package narrative replaces former cross-module narrative under Rule 33.

## 1. Purpose

`agent-service` is the **consolidated edge + kernel module**. It owns the HTTP edge — accepting requests, binding them to a tenant, validating idempotency keys, validating JWT, and serving `/v1/runs` (subpackage `ascend.springai.service.platform.*`, formerly `ascend.springai.platform.*`) — AND it owns the **cognitive runtime kernel** that drives LLMs through tool-calling loops, runs the Run state machine, and dispatches engine envelopes (subpackage `ascend.springai.service.runtime.*`, formerly `ascend.springai.runtime.*`), all within a single deployable. The HTTP edge **trusts the kernel only via the kernel's published SPI surface** (Run repository, Orchestrator, RunRepository), preserving the original platform↛runtime layering as a sub-package invariant enforced by Rule 21 (ADR-0078). See `docs/adr/0078-agent-service-consolidation.yaml` for the merger rationale (supersedes ADR-0055, extends ADR-0066, relates_to ADR-0026).

## 2. Shipped components

> Path convention: every Java path below is rooted at `agent-service/src/main/java/ascend/springai/service/{platform,runtime}/...` **except where explicitly noted as living in `agent-execution-engine` (orchestration SPI: `engine.orchestration.spi`, relocated from the dissolved agent-runtime-core per ADR-0088) or `agent-bus` (S2C transport SPI: `bus.spi.s2c`, relocated from the dissolved agent-runtime-core per ADR-0088)**. The engine SPI surface and the S2C SPI types were extracted to their own modules at the rc5 wave (2026-05-18) per ADR-0079; the transient kernel-shim module `agent-runtime-core` was dissolved in rc13 (2026-05-20) per ADR-0088 and its sources redistributed back to semantic-home modules — see §2.B `runtime / engine` and `runtime / s2c` below. Test paths mirror the layout under `src/test/java/`.

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

`IdempotencyStore` is an SPI interface with two impls wired by
`IdempotencyStoreAutoConfiguration`:

- `JdbcIdempotencyStore` (default when DataSource present) — INSERT …
  ON CONFLICT (tenant_id, idempotency_key) DO NOTHING; SELECT on
  collision. Flyway `V2__idempotency_dedup.sql` (now under
  `agent-service/src/main/resources/db/migration/`) adds the table with
  a PRIMARY KEY composite (schema-layer enforcer E13) and a CHECK
  constraint on `status` (CLAIMED|COMPLETED|FAILED).
- `InMemoryIdempotencyStore` — `ConcurrentHashMap`. Registered ONLY
  when `app.posture=dev` AND `app.idempotency.allow-in-memory=true`.

`IdempotencyProperties` (`@ConfigurationProperties("app.idempotency")`)
exposes `ttl` (default PT24H) and `allowInMemory` (default false).

Status transitions (CLAIMED → COMPLETED/FAILED) and response replay are
W2 work; L1 returns 409 for any duplicate and recovers via
`expires_at` TTL.

Enforcer rows: E12 (durability), E13 (schema), E14 (body-drift),
E22 (allow-in-memory matrix).

#### platform / auth -- JWT validation (L1, ADR-0056)

`AuthProperties` (`@ConfigurationProperties("app.auth")`) holds issuer,
jwks-uri, audience, clock-skew (default PT60S), jwks-cache-ttl
(default PT5M), and dev-local-mode (default false). Cross-field
`@AssertTrue` rejects `dev-local-mode=true` together with a configured
`jwks-uri`.

`JwtDecoderConfig` wires exactly one `JwtDecoder` (Rule 6): JWKS-backed
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

Rule 21 retargeted at Phase C (ADR-0078): runtime sub-package main
sources MUST NOT import any class under
`ascend.springai.service.platform..` (`ServiceRuntimeMustNotDependOnServicePlatformTest`,
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
`agent-service/src/test/java/ascend/springai/service/platform/architecture/`:

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

#### runtime / orchestration -- Cognitive runtime SPI + reference impls (W0; SPI **owned by `agent-execution-engine` (rc13 - orchestration SPI relocated from dissolved agent-runtime-core per ADR-0088)**)

The cognitive runtime kernel's SPI contracts live in **two** modules after the
ADR-0079 (T2.B2) engine-extraction wave (2026-05-18):

- **`agent-execution-engine/src/main/java/ascend/springai/engine/orchestration/spi/`**
  (extracted per ADR-0079) — kernel SPI types `Orchestrator`, `RunContext`,
  `SuspendSignal`, `Checkpointer`, `TraceContext`, and the sealed
  `ExecutorDefinition` hierarchy (`GraphDefinition` | `AgentLoopDefinition`).
- **`agent-execution-engine/src/main/java/ascend/springai/engine/spi/`**
  (extracted per ADR-0079) — executor adapters `GraphExecutor`,
  `AgentLoopExecutor`, the unified `ExecutorAdapter`, `EngineHookSurface`, and
  `EngineMatchingException`.

`agent-service` owns the **posture-gated reference adapters** (not the SPI roots),
under
`agent-service/src/main/java/ascend/springai/service/runtime/orchestration/inmemory/`:
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

- **`agent-service/src/main/java/ascend/springai/service/runtime/runs/`** —
  `Run` (immutable record), `RunStatus` (formal DFA, 7 values: PENDING,
  RUNNING, SUSPENDED, CANCELLED, SUCCEEDED, FAILED, EXPIRED), `RunMode`
  discriminator, `RunStateMachine` (validates every `withStatus(newStatus)`
  transition; illegal transitions throw `IllegalStateException` per Rule 20).
- **`agent-service/src/main/java/ascend/springai/service/runtime/runs/spi/`** —
  `RunRepository` SPI (interface only; pure Java per Rule 32).

After rc13 / ADR-0088 dissolution, `agent-service` is the canonical owner of `runs/` kernel types. Its only orchestration-adapter contribution
on the runs axis is the posture-gated `InMemoryRunRegistry` reference adapter
under `agent-service/.../runtime/orchestration/inmemory/` listed above.

#### runtime / resilience -- Operation-routing SPI (W0, **`.spi` package home per ADR-0080**)

The `ResilienceContract` published SPI surface lives at
`agent-service/src/main/java/ascend/springai/service/runtime/resilience/spi/`
(package `ascend.springai.service.runtime.resilience.spi`): `ResilienceContract`,
`ResiliencePolicy`, `SkillResolution`, `SuspendReason`, `SkillCapacityRegistry`.
Implementations stay in the parent package
`agent-service/src/main/java/ascend/springai/service/runtime/resilience/`:
`DefaultSkillResilienceContract`, `YamlResilienceContract`,
`YamlSkillCapacityRegistry`.

The runtime `ResilienceContract.resolve(tenant, skill)` consults
`docs/governance/skill-capacity.yaml` (Rule 41); over-cap callers are
SUSPENDED, not rejected (Chronos Hydration interlock with Rule 38). The
`.spi` package home was added at the rc6 wave (ADR-0080, 2026-05-18) to align
the published-SPI surface with Rules 32 / 77 / 78 — the same split pattern
used by `agent-execution-engine` (`engine.spi.*` vs `service.runtime.engine.*`).

#### runtime / memory -- Memory SPI shell (W0 shell)

`agent-service/src/main/java/ascend/springai/service/runtime/memory/spi/GraphMemoryRepository.java`:
interface-only SPI. No adapter ships at W0; concrete impl arrives via
the `spring-ai-ascend-graphmemory-starter` autoconfiguration, which
points at `ascend.springai.service.runtime.graphmemory.GraphMemoryAutoConfiguration`
post-Phase-C.

#### runtime / engine -- Engine envelope + registry (W2.x, **consumed from `agent-execution-engine` post-ADR-0079**)

`agent-service` **consumes** the `agent-execution-engine` module — the
engine SPI surface and the registry/envelope no longer live here. After
the rc5 wave (2026-05-18) ADR-0079 extraction:

- **Engine SPI surface** (package `ascend.springai.engine.spi.*`,
  module `agent-execution-engine`): `ExecutorAdapter`, `GraphExecutor`,
  `AgentLoopExecutor`, `EngineHookSurface`, `EngineMatchingException`
  — sources live at
  `agent-execution-engine/src/main/java/ascend/springai/engine/spi/`.
- **Engine registry + envelope home** (package
  `ascend.springai.service.runtime.engine.*`, module
  `agent-execution-engine` — the package root is preserved across the
  module move so that `agent-service` callers do not see an import
  rename): `EngineRegistry`, `EngineEnvelope` record (mirrors
  `docs/contracts/engine-envelope.v1.yaml`) — sources at
  `agent-execution-engine/src/main/java/ascend/springai/service/runtime/engine/`.
- **Reference engine adapters** stay in `agent-service` at
  `agent-service/src/main/java/ascend/springai/service/runtime/orchestration/inmemory/`:
  `SequentialGraphExecutor` (`extends GraphExecutor`),
  `IterativeAgentLoopExecutor` (`extends AgentLoopExecutor`),
  `SyncOrchestrator`, `InMemoryCheckpointer`, `InMemoryRunRegistry`.

Every Run dispatch goes through `EngineRegistry.resolve(envelope)`
(Rule 43); pattern-matching on `ExecutorDefinition` subtypes outside
the registry is forbidden. Mismatch raises `EngineMatchingException`
and transitions the Run to FAILED with reason `engine_mismatch`
(Rule 44, no fallback policy). The intentional split-package
arrangement (SPI under `engine.spi.*`, registry/envelope under
`service.runtime.engine.*` — both inside `agent-execution-engine`) is
documented in `agent-execution-engine/ARCHITECTURE.md` Status section
and protected by Rule 76 (no split SPI packages — `engine.spi.*` is
owned by exactly one module).

#### runtime / s2c -- Server-to-Client callback envelope (W2.x, ADR-0040 rc3, **SPI in `agent-bus.spi.s2c` (rc13 - relocated from dissolved agent-runtime-core per ADR-0088)**)

`agent-service` **consumes** the S2C SPI surface from `agent-bus` —
the SPI records and transport interface no longer live in this module. After
the rc13 wave (2026-05-20) ADR-0088 dissolution (which moved S2C ownership
from the dissolved agent-runtime-core to agent-bus):

- **S2C SPI surface** (package
  `ascend.springai.bus.spi.s2c.*`, module `agent-bus`):
  `S2cCallbackEnvelope`, `S2cCallbackResponse`, `S2cCallbackTransport` —
  sources at `agent-bus/src/main/java/ascend/springai/bus/spi/s2c/`.
- **Reference transport impl** stays in `agent-service` at
  `agent-service/src/main/java/ascend/springai/service/runtime/s2c/InMemoryS2cCallbackTransport.java`
  (package `ascend.springai.service.runtime.s2c`, non-`.spi` —
  implementation home).

The waiting Run suspends via `SuspendSignal.forClientCallback(...)` —
the checked-suspension variant introduced in the rc3 wave per ADR-0074
(unifies the prior unchecked `S2cCallbackSignal`, which was deleted).
The orchestrator marks the parent Run SUSPENDED with
`SuspendReason.AwaitClientCallback`. Callbacks consume the
`s2c.client.callback` skill capacity declared in
`docs/governance/skill-capacity.yaml` (Rule 46).

#### runtime / probe -- OSS classpath shape probe (W0)

`OssApiProbe` under
`agent-service/src/main/java/ascend/springai/service/runtime/probe/`
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
`agent-service/src/main/java/ascend/springai/service/runtime/idempotency/`
and is the runtime-side contract-spine entity. It mirrors the persistence
shape (`tenantId`, `idempotencyKey`, `requestHash`, `status`,
`createdAt`, `expiresAt`) consumed by the platform-side
`IdempotencyStore` SPI. Mandatory `tenantId` field is the trigger
condition for Rule 11 activation (Wave 4 Track B in the Phase C re-plan).

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
purity post-Phase-C. It is enforced by **Rule 21** (retargeted from the
original "no `TenantContextHolder` import" invariant) via the ArchUnit
class **`ServiceRuntimeMustNotDependOnServicePlatformTest`** under
`agent-service/src/test/java/ascend/springai/service/runtime/architecture/`,
registered as enforcer **E2** in `docs/governance/enforcers.yaml`. The
narrow original case — no import of `TenantContextHolder` (which now
lives at `service.platform.tenant.TenantContextHolder`) — remains
asserted independently as defence-in-depth.

The reverse direction (`service.platform → service.runtime`) is
permitted **only** to the runtime public surface, enforced by
`ServicePlatformImportsOnlyServiceRuntimePublicApiTest` (E34). The
allowed runtime public packages are: `service.runtime.runs.*`,
`service.runtime.orchestration.spi.*`, `service.runtime.posture.*`, and
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
| `RunHttpContractIT` | Integration | unauthenticated 401/403 + authenticated matrix (`createReturnsPending`, `tenantMismatchReturns403`, `cancelTerminalReturns409`, `duplicateIdempotencyKeyReturns409`, `cancel_route_is_post_not_delete`); enforcers E5/E6/E7/E10/E24 | platform |
| `RunStatusEnumTest` | Unit | enum pinned at 7 values; no `CREATED` (enforcer E5) | runtime |
| `ErrorEnvelopeContractTest` | Unit | every 4xx/5xx response has `{error:{code,message,details}}` shape (enforcer E8) | platform |
| `TenantTagMeterFilterTest` | Unit | forbidden high-cardinality tags stripped from `springai_ascend_*` (enforcer E19) | platform |
| `ServicePlatformImportsOnlyServiceRuntimePublicApiTest` | ArchUnit | platform sub-package may only import the runtime sub-package public-API packages (enforcer E34) | platform |
| `ServiceRuntimeMustNotDependOnServicePlatformTest` | ArchUnit | runtime sub-package MUST NOT import any platform sub-package (Rule 21, enforcer E2) | runtime |
| `HttpEdgeMustNotImportMemorySpiTest` | ArchUnit | HTTP edge cannot reach the memory SPI (enforcer E4) | platform |
| `ApiCompatibilityTest` | ArchUnit | module-dep direction + SPI purity (W0 baseline) | both |
| `JwtTestFixture` | Test fixture | shared RSA keypair + JWT mint helper for L1 authenticated tests (enforcer E37) | platform |
| `OssApiProbeTest` | Unit | OSS classpath shape (3 tests; no Spring context) | runtime |
| `RunStateMachineTest` | Unit | Legal + illegal DFA transitions; EXPIRED terminal | runtime |
| `TelemetryVerticalArchTest` | ArchUnit | §4 #53 — adapter classes must not write `TraceContext` outside hook/observability packages | runtime |
| `RunContextIdentityAccessorsTest` | ArchUnit | §4 #54 — `RunContext` exposes `traceId()` / `spanId()` / `sessionId()` / `traceContext()` returning declared types | runtime |
| `RunTraceSessionConsistencyIT` | Integration | §4 #54 — `Run.traceId` non-null hex when populated; nullable column tolerated at L1.x; child Run inherits sessionId via Checkpointer | runtime |
| `LlmGatewayHookChainOnlyTest` | ArchUnit | §4 #56 — no `service.runtime.llm.*` class imports `ChatModel` outside `HookChain` package (vacuous at L1.x; arms for W2) | runtime |
| `SpanTenantAttributeRequiredTest` | ArchUnit | §4 #57 — emission sites declare `tenant.id` attribute (vacuous at L1.x; arms for W2) | runtime |
| `McpReplaySurfaceArchTest` | ArchUnit | §4 #59 — no `@RestController` resides in `web/replay/`, `web/trace/`, or `web/session/` | runtime |
| `PostureBootPiiHookPresenceContractIT` | Integration | §4 #58 — boot-gate contract for `PiiRedactionHook` in research/prod (full negative test W2) | runtime |
| `RunTest` | Unit | Run record construction, withStatus(), withSuspension() | runtime |
| `InMemoryCheckpointerTest` | Unit | save/load/clear round-trip | runtime |
| `OrchestrationSpiArchTest` | ArchUnit | SPI packages import only java.* | runtime |
| `TenantPropagationPurityTest` | ArchUnit | Rule 21: runtime sub-package never imports TenantContextHolder | runtime |
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
> Rule 21, enforced by `ServiceRuntimeMustNotDependOnServicePlatformTest`.

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
- **Phase C (delivered 2026-05-18, ADR-0078)**: merger of `agent-platform` + `agent-runtime` → `agent-service`. Package rename `ascend.springai.platform.*` → `ascend.springai.service.platform.*` and `ascend.springai.runtime.*` → `ascend.springai.service.runtime.*`. Rule 21 retargeted; ArchUnit class renamed `RuntimeMustNotDependOnPlatformTest` → `ServiceRuntimeMustNotDependOnServicePlatformTest`. Old modules deleted; reactor count decremented by 1.
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
  sources tenant from `RunContext.tenantId()` (Rule 21, enforced by
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
- **Engine extraction landed** (T2.B2, ADR-0079, 2026-05-18): the engine
  SPI surface moved to `agent-execution-engine` at
  `ascend.springai.engine.spi.*` (`ExecutorAdapter` and friends), and
  `EngineRegistry` + `EngineEnvelope` moved to the same module under
  `ascend.springai.service.runtime.engine.*` (package preserved to keep
  callers unchanged). The intentional split-package arrangement is
  documented in `agent-execution-engine/ARCHITECTURE.md` Status section.
  `EngineRegistry.resolve` boundary remains asserted by Rule 43
  enforcer E84; consumed cross-module via the `agent-execution-engine` (rc13 dissolution per ADR-0088) →
  `agent-execution-engine` → `agent-service` dependency chain.

## 10. Roadmap

- Deferred capabilities and design decisions: `docs/CLAUDE-deferred.md`; current delivery state per wave (W0..W4): `docs/governance/architecture-status.yaml` (structured capability ledger; the prior `docs/STATE.md` pointer was removed at the rc6 wave — never landed on disk).
- Wave engineering plan: `ARCHITECTURE.md §1 + docs/governance/architecture-status.yaml + docs/CLAUDE-deferred.md` (per ADR-0037; engineering-plan-W0-W4.md archived).
- Phase C consolidation specification: `docs/adr/0078-agent-service-consolidation.yaml`; execution plan: `docs/plans/phase-c-merge.md`.

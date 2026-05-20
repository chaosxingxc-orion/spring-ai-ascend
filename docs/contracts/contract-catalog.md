# Contract Catalog

> Single source of truth for all public contracts in the spring-ai-ascend platform.
> Version: 0.1.0-SNAPSHOT | Last refreshed: 2026-05-20 (rc13 — agent-runtime-core dissolution + ingress gateway mandate per ADR-0088 + ADR-0089)

---

## 1. HTTP API contracts

Stable W0 routes: `GET /v1/health`, `GET /actuator/health`, `GET /actuator/prometheus` (no auth headers). Shipped W1 routes: `POST /v1/runs` (202 + TaskCursor per Rule R-F Cursor Flow), `GET /v1/runs/{id}`, `POST /v1/runs/{id}/cancel` (200/403/404/409 per Rule R-J.b cancel re-authorization) — all require `X-Tenant-Id`; POST routes also require `Idempotency-Key`; W1 adds JWT `tenant_id` claim cross-check against `X-Tenant-Id` (ADR-0040). Implementation: `agent-service/src/main/java/.../web/runs/RunController.java`. Full per-route spec: [http-api-contracts.md](http-api-contracts.md) + `docs/contracts/openapi-v1.yaml`. (rc12 K-ζ updated these rows from prior `Planned W1` per rc11 review P2-1; Rule 104 `openapi_implemented_route_catalog_truth` prevents recurrence.)

**API conventions** (absorbed from `api-conventions.md`): URL major-versioned (`/v1/`); plural nouns; RFC 7807 `application/problem+json` errors with stable `code`; cursor pagination (`?limit=20&cursor=`); `GET`=200, POST-create=201, async=202, DELETE=204; `Idempotency-Key` required on POST/PUT/PATCH in research/prod; `OpenApiContractIT` snapshot-tests spec; SSE streaming reserved W3+.

---

## 2. Java SPI interfaces

**Inclusion rule:** Java `interface` types that represent named public extension points in the current reactor modules; not probes, not data carriers (records / sealed status types / exceptions), not implementations.

SPI impls: thread-safe, no null returns. SPIs that process tenant-owned runtime data MUST carry tenant scope (via explicit `tenantId` argument or `RunContext.tenantId()`). SPI packages import only `java.*` plus same-spi-package siblings (ArchUnit `SpiPurityGeneralizedArchTest`). japicmp binary-compat from W1.

**Active SPI interfaces (13 total):**

| Interface | Module | Package | Status |
|---|---|---|---|
| `RunRepository` | `agent-service` | `ascend.springai.service.runtime.runs.spi` | shipped — W0 in-memory impl (`InMemoryRunRegistry`); relocated to agent-service per ADR-0088 |
| `Checkpointer` | `agent-execution-engine` | `ascend.springai.engine.orchestration.spi` | shipped — W0 in-memory impl (`InMemoryCheckpointer`, in `agent-service`); package renamed + module relocated per ADR-0088 |
| `Orchestrator` | `agent-execution-engine` | `ascend.springai.engine.orchestration.spi` | shipped — W0 reference impl (`SyncOrchestrator`, in `agent-service`); relocated per ADR-0088 |
| `S2cCallbackTransport` | `agent-bus` | `ascend.springai.bus.spi.s2c` | shipped — W2.x; `InMemoryS2cCallbackTransport` reference (ADR-0074); relocated to agent-bus per ADR-0088 |
| `IngressGateway` | `agent-bus` | `ascend.springai.bus.spi.ingress` | shipped (SPI stub) — W1 design_only contract per ADR-0089; runtime binding W3+ with agent-client SDK |
| `GraphMemoryRepository` | `agent-service` | `ascend.springai.service.runtime.memory.spi` | shipped — interface only; Graphiti W1 reference (ADR-0034) |
| `ResilienceContract` | `agent-service` | `ascend.springai.service.runtime.resilience.spi` | shipped — W0 Resilience4j-backed impl (`DefaultSkillResilienceContract`); per-skill capacity via `YamlResilienceContract`; package home moved to `.spi` per ADR-0080 to align with Rules 32/77/78 — implementations stay in `runtime.resilience.*` |
| `SkillCapacityRegistry` | `agent-service` | `ascend.springai.service.runtime.resilience.spi` | shipped — W0 YAML-backed impl (`YamlSkillCapacityRegistry`, in `agent-service`); `ResilienceAutoConfiguration` exposes it as an `@ConditionalOnMissingBean` extension point. Consumed by `ResilienceContract.resolve(tenant, skill)` per ADR-0070 / ADR-0080 / ADR-0081 |
| `ExecutorAdapter` | `agent-execution-engine` | `ascend.springai.engine.spi` | shipped — W2.x; reference adapters in `agent-service` (ADR-0072 / ADR-0088) |
| `GraphExecutor` | `agent-execution-engine` | `ascend.springai.engine.spi` | shipped — `extends ExecutorAdapter`; W0 reference impl (`SequentialGraphExecutor`, in `agent-service`) |
| `AgentLoopExecutor` | `agent-execution-engine` | `ascend.springai.engine.spi` | shipped — `extends ExecutorAdapter`; W0 reference impl (`IterativeAgentLoopExecutor`, in `agent-service`) |
| `EngineHookSurface` | `agent-execution-engine` | `ascend.springai.engine.spi` | shipped — W2.x; bridge to `RuntimeMiddleware` (ADR-0073) |
| `RuntimeMiddleware` | `agent-middleware` | `ascend.springai.middleware.spi` | shipped — W2.x; `@FunctionalInterface` listener (ADR-0073) |

**SPI count by module (post-ADR-0088 + ADR-0089):**

| Module | SPI interfaces |
|---|---|
| `agent-service` | 4 (`RunRepository`, `GraphMemoryRepository`, `ResilienceContract`, `SkillCapacityRegistry`) |
| `agent-execution-engine` | 6 (`ExecutorAdapter`, `GraphExecutor`, `AgentLoopExecutor`, `EngineHookSurface`, `Checkpointer`, `Orchestrator`) |
| `agent-bus` | 2 (`IngressGateway`, `S2cCallbackTransport`) |
| `agent-middleware` | 1 (`RuntimeMiddleware`) |
| `agent-client` / `agent-evolve` | 0 — skeleton `spi/package-info.java` only |
| `spring-ai-ascend-graphmemory-starter` | 0 — sidecar adapter; no new SPI |

**Per-SPI tenant scope (canonical post-ADR-0044):**

| SPI | Scope | Tenant carrier | Planned scope evolution |
|---|---|---|---|
| `RunRepository` | tenant-scoped | explicit `tenantId` arg on `findByTenant*` | unchanged |
| `Checkpointer` | run-scoped | implicit via `runId` uniqueness | unchanged (ADR-0027) |
| `Orchestrator` | tenant-scoped | explicit `tenantId` arg in `run(runId, tenantId, …)` | unchanged |
| `S2cCallbackTransport` | tenant-scoped | `S2cCallbackEnvelope.tenantId` field (Rule R-C.c) | unchanged — relocated to agent-bus per ADR-0088 |
| `IngressGateway` | tenant-scoped | `IngressEnvelope.tenantId` field (Rule R-C.c, validated non-blank) | design_only at W1; promoted to runtime_enforced with W3+ SDK per ADR-0089 |
| `GraphMemoryRepository` | tenant-scoped | explicit `tenantId` first arg on every method (Rule R-C, formerly Rule 11) | unchanged |
| `ResilienceContract` | dual-surface: operation-policy + skill-capacity | W0+ `resolve(operationId)` (operation-policy routing; legacy axis) **and** W1.x Phase 9+ `resolve(tenant, skill)` (skill-capacity arbitration per ADR-0070, Rule R-K.b — formerly Rule 41.b) | Operation-policy axis only; the pre-ADR-0070 plan to extend the *operation* surface to `(tenantId, operationId)` is **superseded** by ADR-0070 / ADR-0081 — the skill axis is `(tenant, skill)`, NOT `(tenantId, operationId)`. The two axes MUST NOT be conflated. |
| `SkillCapacityRegistry` | tenant-scoped | explicit `tenantId` arg on `tryAcquire(tenantId, skillKey)` / `release(tenantId, skillKey)` (Rule R-C compliant) | unchanged |
| `ExecutorAdapter` / `GraphExecutor` / `AgentLoopExecutor` | tenant-scoped | via injected `RunContext.tenantId()` | unchanged |
| `EngineHookSurface` | tenant-scoped | dispatches through `HookContext.tenantId()` | unchanged |
| `RuntimeMiddleware` | tenant-scoped | `HookContext.tenantId()` on every callback | unchanged |

**Structural carriers (records / sealed interfaces / sealed status types / exceptions — not SPI interfaces but part of the contract surface):**

| Type | Module | Notes |
|---|---|---|
| `Run` | `agent-service` (`...service.runtime.runs`) | Run aggregate record; status transitions guarded by `RunStateMachine` (Rule R-C.d); relocated per ADR-0088 |
| `RunStatus` | `agent-service` (`...service.runtime.runs`) | Sealed status taxonomy; relocated per ADR-0088 |
| `RunMode` | `agent-execution-engine` (`...engine.orchestration.spi`) | GRAPH \| AGENT_LOOP discriminator; co-located with orchestration SPI per ADR-0088 |
| `RunContext` | `agent-execution-engine` (`...engine.orchestration.spi`) | Per-run context interface; exposes `tenantId()`, `runId()` |
| `TraceContext` | `agent-execution-engine` (`...engine.orchestration.spi`) | Trace-id / span carrier interface |
| `ExecutorDefinition` | `agent-execution-engine` (`...engine.orchestration.spi`) | Sealed: `GraphDefinition` \| `AgentLoopDefinition` |
| `SuspendSignal` | `agent-execution-engine` (`...engine.orchestration.spi`) | Checked-exception interrupt primitive; carries `forClientCallback(...)` variant per ADR-0074 |
| `IdempotencyRecord` | `agent-service` (`...service.runtime.idempotency`) | Idempotency-Key persistence record (Rule R-C.c contract spine); relocated per ADR-0088 |
| `S2cCallbackEnvelope` / `S2cCallbackResponse` | `agent-bus` (`...bus.spi.s2c`) | Six mandatory request fields per ADR-0074; relocated per ADR-0088 |
| `IngressEnvelope` / `IngressResponse` / `IngressStatus` / `IngressRequestType` | `agent-bus` (`...bus.spi.ingress`) | C2S ingress envelope (6 required fields per ADR-0089); response carries Task Cursor (Rule R-F) on ACCEPTED RUN_CREATE |
| `EngineRegistry` | `agent-execution-engine` (`...service.runtime.engine`) | Single authority for `resolve(envelope)` / `resolveByPayload(...)` (Rule 43) |
| `EngineEnvelope` | `agent-execution-engine` (`...service.runtime.engine`) | Request shape mirroring `engine-envelope.v1.yaml` |
| `EngineMatchingException` | `agent-execution-engine` (`...engine.spi`) | Thrown by `EngineRegistry.resolve(...)` on engine-type mismatch (Rule 44) |
| `HookPoint` | `agent-middleware` (`...middleware.spi`) | 9-value enum (before/after LLM/tool/memory + before_suspension + before_resume + on_error); mirrors `engine-hooks.v1.yaml` |
| `HookContext` | `agent-middleware` (`...middleware.spi`) | Hook invocation carrier record |
| `HookOutcome` | `agent-middleware` (`...middleware.spi`) | Sealed: continue \| short_circuit \| fail |

**Design-named SPIs (deferred W2+):**

| Interface | Planned Wave | ADR |
|---|---|---|
| `Skill` + `SkillContext` + `SkillResourceMatrix` | W2 | ADR-0030 / ADR-0052 |
| `RunDispatcher` | W2 | ADR-0031 |
| `CapabilityRegistry` | W2 | ADR-0021 |
| `AgentRegistry` + `RemoteAgentClient` | post-W4 | ADR-0016 |

Seven previously listed SDK SPI interfaces were deleted in the 2026-05-12 Occam pass (see `architecture-status.yaml` row `sdk_spi_starters`). They are no longer part of the contract surface.

---

## 3. YAML domain contracts

Schema-first domain contracts (Rule 48). Each YAML file is the single source of truth for the named protocol; Java records/enums mirror the schema and validate fields on construction.

| Contract | Path | Status | Authority |
|---|---|---|---|
| `engine-envelope.v1.yaml` | `docs/contracts/` | `runtime_enforced` | ADR-0072 (Rule R-M.a) |
| `engine-hooks.v1.yaml` | `docs/contracts/` | `runtime_enforced` (delivery) | ADR-0073 (Rule R-M.c) |
| `s2c-callback.v1.yaml` | `docs/contracts/` | `runtime_enforced` | ADR-0074 (Rule R-M.d); java types in `agent-bus.bus.spi.s2c` per ADR-0088 |
| `ingress-envelope.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0089 (Rule R-I.b); runtime binding W3+ with agent-client SDK |
| `plan-projection.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0032 (planner contract minimal); ADR-0052 (`SkillResourceMatrix`); rc4 review P1-3 amendment |
| `evolution-scope.v1.yaml` | `docs/governance/` | `schema_shipped` | ADR-0077 (Rule R-M.e) |

Note: `evolution-scope.v1.yaml` lives under `docs/governance/`, not `docs/contracts/`, because it indexes governance-plane export rules rather than a wire/Java contract.

---

## 4. Configuration contracts

**Absorbed from `configuration-contracts.md`**: All properties under `springai.ascend.*`; `app.posture={dev,research,prod}` read once at boot (dev=permissive, research/prod=fail-closed). Each starter exposes `springai.ascend.<domain>.enabled`. Sidecar adapters (`graphmemory`) default `enabled=false`. The `base-url` / `api-key` knobs on graphmemory are **RESERVED for the W1 Graphiti REST adapter** (ADR-0034) — at W0 they are accepted but not consumed (orphan-config Rule 3 exemption flagged via `@Deprecated(forRemoval=false)` on the property fields per v2.0.0-rc3 cross-constraint audit α-8 / P1-7).

**Absorbed from `contract-evolution-policy.md`**: Config deprecation = N+2 release cycle. HTTP /v1 stays active after /v2 (research: 90 days, prod: 180 days). Breaking-change checklist required before any contract-surface PR merges.

---

## 5. Telemetry contract (absorbed from `telemetry-contracts.md`)

Counter: `springai_ascend_<domain>_<subject>_total`. Timer: `springai_ascend_<domain>_<operation>_seconds`. High-cardinality labels (`tenant_id`, `run_id`, `user_id`) forbidden on Prometheus; use structured JSON logs. Cardinality cap: 1 000 (research) / 10 000 (prod). Key counters: `*_default_impl_not_configured_total{spi, method}`, `filter_errors_total{filter, reason}`, `idempotency_{claimed,replayed,conflict,error}_total`.

---

## 6. SDK versioning (absorbed from `sdk-versioning.md`)

SemVer from 1.0.0: PATCH=fix, MINOR=additive, MAJOR=breaking. Stable surface: starter artifacts, SPI interfaces, Spring Boot property keys. Deprecate with `@Deprecated` in current MINOR; remove in next MAJOR. All deps pinned to exact patch in `spring-ai-ascend-dependencies` BoM. Spring AI 2.0.0-M5; CI forces upgrade after 2026-08-01. Current maturity: SPI=L1, HTTP /v1=L2, Config=L1, Telemetry=L1.

---

## 7. Maven BoM

`ascend.springai:spring-ai-ascend-dependencies:0.1.0-SNAPSHOT` — active reactor modules (8 per `architecture-status.yaml#repository_counts.reactor_modules`):

| Artifact | Plane (P-I) | Status |
|---|---|---|
| `spring-ai-ascend-parent` | none | Parent POM (not a reactor row by itself; declared via `<parent>`) |
| `spring-ai-ascend-dependencies` | none | BoM (dependency management) |
| `agent-service` | compute_control | HTTP edge (`service.platform.*`) + cognitive runtime kernel (`service.runtime.*`) + Run/RunStateMachine/IdempotencyRecord entities + `RunRepository` / `GraphMemoryRepository` / `ResilienceContract` / `SkillCapacityRegistry` SPIs and W0 reference impls — Phase C / ADR-0078 + rc13 re-consolidation per ADR-0088 |
| `agent-middleware` | compute_control | Cross-cutting middleware SPI (`RuntimeMiddleware` + `HookDispatcher`) — ADR-0073 |
| `agent-execution-engine` | compute_control | Heterogeneous engine surface (`EngineRegistry`, `EngineEnvelope`, `ExecutorAdapter` SPI, hook bridge) + orchestration SPI (RunMode + Checkpointer + Orchestrator + RunContext + SuspendSignal + TraceContext + ExecutorDefinition; `engine.orchestration.spi`) — ADR-0072 / ADR-0088 |
| `agent-bus` | bus_state | Active cross-plane control surfaces: `bus.spi.ingress.IngressGateway` (ADR-0089) + `bus.spi.s2c.S2cCallbackTransport` (ADR-0074 + ADR-0088). Workflow primitives W2 per ADR-0050 |
| `agent-client` | edge | Skeleton — W3+ per ADR-0049; cross-plane traffic locked to `bus.spi.ingress.IngressGateway` per ADR-0089 / Rule R-I.b |
| `agent-evolve` | evolution | Skeleton — Python ML; Java adapter deferred |
| `spring-ai-ascend-graphmemory-starter` | bus_state | Sidecar adapter (graphmemory SPI scaffold) — ADR-0034 |

**Module history (rc12 → rc13 reactor count: 9 → 8)**

- Pre-Phase-C era modules `agent-platform` + `agent-runtime` were merged into `agent-service` per **ADR-0078** (consolidation, 2026-05-18).
- **ADR-0079** (2026-05-18) introduced a transient shared kernel module `agent-runtime-core` to host SPI + kernel types shared by `agent-service` and `agent-execution-engine`, breaking the engine ↔ service back-dep cycle.
- **ADR-0088** (2026-05-20, rc13) **dissolved** `agent-runtime-core` and redistributed its 16 sources to semantic-home modules: `Run`/`RunStatus`/`RunStateMachine`/`RunRepository`/`IdempotencyRecord` back to `agent-service`; `RunMode` + 6 orchestration SPI types into `agent-execution-engine.engine.orchestration.spi`; 3 S2C transport types into `agent-bus.bus.spi.s2c`. Symmetric with **ADR-0089** which adds `agent-bus.bus.spi.ingress.IngressGateway` as the C2S cross-plane surface — Bus & State Hub plane now owns cross-plane traffic in both directions.
- Pre-2026-05-12 starters (`-memory`, `-skills`, `-knowledge`, `-governance`, `-persistence`, `-resilience`, `-mem0`, `-docling`, `-langchain4j-profile`) were deleted in the 2026-05-12 Occam pass.

---

*See also*: `docs/telemetry/policy.md` · `docs/cross-cutting/posture-model.md`

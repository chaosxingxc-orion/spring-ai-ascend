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

# agent-service — L1 architecture (module-root grounding)

> **Altitude discipline (L1).** This module-root file is the
> **shipped-state grounding** surface — purpose, the shipped frames
> (package clusters) and their responsibility, the public-SPI surface
> (named, with generated-fact refs), dependencies, posture defaults, the
> wave plan, and risks. It deliberately does NOT carry code-level detail:
> HTTP routes / verbs / status codes, SQL / `ON CONFLICT` / RLS / GUC,
> Flyway migration files, filter ordering, error-envelope JSON shapes,
> method signatures, and test-class inventories are **L2 / contract /
> verification** material. Those live in the route + engine + S2C +
> RunEvent contracts under `docs/contracts/`, the per-view 4+1 files in
> this directory, the L2 Boundary Contracts in
> [`development.md`](development.md) §5, and the generated facts under
> `architecture/facts/generated/`. The 4+1 views are the canonical
> architectural surface; this file cross-links to them.

## 0.5 Canonical L1 4+1 View Source (ADR-0143)

The canonical 4+1 view of this module (Scenarios + Logical + Process +
Development + Physical) lives as 5 per-view files under this directory,
per [ADR-0143](../../../../docs/adr/0143-review-log-demotion-l1-canonical-move.yaml):

- **Index:** [`./README.md`](./README.md)
- **Scenarios:** [`./scenarios.md`](./scenarios.md) — S1-S5 canonical scenarios + AS-SC enterprise inventory.
- **Logical:** [`./logical.md`](./logical.md) — 5-layer model (with the ADR-0140 5a/5b split + ADR-0142 single-owner + ADR-0145 RunEvent hierarchy fact) + aggregate model + state machines + vocabulary glossary.
- **Process:** [`./process.md`](./process.md) — layer-interaction flows P1-P6 (including the concurrent-cancel loser flow P6).
- **Physical:** [`./physical.md`](./physical.md) — 5-plane deployment + persistence-plane tenancy posture + 3-track bus + sandbox.
- **Development:** [`./development.md`](./development.md) — package tree + Layer↔Package matrix per [ADR-0144](../../../../docs/adr/0144-layer-vs-package-matrix.yaml) + 5 L2 Boundary Contracts (Rule G-1.1.c).
- **SPI Appendix:** [`./spi-appendix.md`](./spi-appendix.md) — active SPIs with 4-way parity (Rule G-1.1.b).

**Historical note:** the rc53 review file (+ `.cn.md` sibling) was the
original authoring surface for the §§14-20 view content. Per ADR-0143 it
is now a **historical authoring record** (freeze-marked); the canonical
4+1 source is the per-view files above. Where they disagree, the
canonical files win.

Governing rule: Rule R-C — Code-as-Contract (ADR-0059 + ADR-0086
namespace ratchet). Every constraint below maps to at least one row in
`docs/governance/enforcers.yaml`.

## 1. Purpose

`agent-service` is the **consolidated edge + kernel module**. It owns the
HTTP edge — accepting requests, binding them to a tenant, validating
idempotency, validating JWT, and serving the run API (subpackage
`com.huawei.ascend.service.platform.*`) — AND it owns the **cognitive
runtime kernel** that drives LLMs through tool-calling loops, runs the
Run state machine, and dispatches engine envelopes (subpackage
`com.huawei.ascend.service.runtime.*`), all within a single deployable.
The HTTP edge **trusts the kernel only via the kernel's published SPI
surface**, preserving the original platform↛runtime layering as a
sub-package invariant enforced by the ArchUnit mechanism behind Rule
R-C.e (enforcer E2). See
[`docs/adr/0078-agent-service-consolidation.yaml`](../../../../docs/adr/0078-agent-service-consolidation.yaml)
for the merger rationale (supersedes ADR-0055, extends ADR-0066,
relates_to ADR-0026).

## 2. Shipped frames (package clusters) and their responsibility

> Path convention: every Java path below is rooted at
> `agent-service/src/main/java/com/huawei/ascend/service/{platform,runtime}/...`
> **except where noted as living in `agent-bus`** (neutral
> orchestration/engine SPI `bus.spi.engine`, re-homed per ADR-0158; S2C
> transport SPI `bus.spi.s2c`, relocated per ADR-0088) **or
> `agent-execution-engine`** (engine adapter SPI `engine.spi` + the
> `InProcessEnginePort` realization per ADR-0158). This section names each
> frame's **responsibility** and its **public boundary**; the runtime
> behaviour (routes, status codes, SQL, filter order, error shapes) is
> delegated to the contracts + per-view files cited per frame.

### 2.A Platform-side frames (subpackage `service.platform.*`)

| Frame (package) | Responsibility | Boundary surface · where the behaviour is defined |
|---|---|---|
| `platform/web` | HTTP front door + security config + error-envelope shaping. | Route + status + error-envelope shapes owned by [`openapi-v1.yaml`](../../../../docs/contracts) (Rule R-F / enforcer E8). |
| `platform/web/runs` | The run API surface (create / get / cancel; resume W2). | Route verbs, status codes, and the cancel-vs-DELETE discipline owned by `openapi-v1.yaml` (enforcers E5/E6/E7/E8/E24); scenario grounding in [`scenarios.md`](scenarios.md) §S1/§S5. |
| `platform/tenant` | Per-request tenant binding + MDC correlation; JWT tenant-claim cross-check. | Cross-check + binding behaviour per ADR-0040 / ADR-0056 §3 (enforcers E10, E2); filter-chain ordering is Layer 1 contract detail, not restated here. |
| `platform/idempotency` | Durable claim/replay (`IdempotencyHeaderFilter` + the `IdempotencyStore` historical platform-internal interface — not under `.spi` per Rule R-D.d). | Claim/replay + body-drift semantics per ADR-0057 (enforcers E12/E13/E14/E22); SQL + schema are the §5.3 L2 Boundary Contract. |
| `platform/auth` | JWT validation wiring (single `JwtDecoder` per Rule D-8). | Validation matrix + dev-local-mode posture guard per ADR-0056 (enforcers E9, E11). |
| `platform/posture` | Boot-time fail-closed gate (`PostureBootGuard`). | Required-config matrix per ADR-0058 (enforcers E11, E21, E22). |
| `platform/observability` | Tenant tagging + forbidden-tag scrub + Telemetry-Vertical trace edge. | Metric-prefix + tag-scrub + traceparent behaviour per ADR-0061 (enforcers E18/E19/E38/E40/E41). |
| `platform/architecture` (test-only) | Layering enforcers. | ArchUnit mechanisms registered as enforcers E2/E34/E4 etc.; the test-class inventory is verification material (`architecture/facts/generated/tests.json`). |

### 2.B Runtime-side frames (subpackage `service.runtime.*`)

| Frame (package) | Responsibility | Public SPI / boundary · generated-fact ref |
|---|---|---|
| `runtime/runs` (+ `runs/spi`) | The Run aggregate: `Run`, `RunStatus` (7-value DFA), `RunMode`, `RunStateMachine`; the `RunRepository` SPI is the single sanctioned Run-state-transition path (Rule R-C.2.b + ADR-0142). | `RunRepository` → [`code-symbol/com-huawei-ascend-service-runtime-runs-spi-runrepository`](../../../../architecture/facts/generated/code-symbols.json); Run → [`…runtime-runs-run`](../../../../architecture/facts/generated/code-symbols.json). Owner of `runs/` kernel types post-ADR-0088. |
| `runtime/orchestration/inmemory` | Posture-gated in-memory reference adapters (orchestrator + Graph / AgentLoop executors + checkpointer + run registry); fail-closed in research/prod via `AppPostureGate`. | Reference impls of the neutral engine SPI (`bus.spi.engine`, re-homed per ADR-0158) + the engine adapter SPI (`engine.spi`, `agent-execution-engine`). |
| `runtime/resilience` (+ `resilience/spi`) | Operation-routing contract + capacity registry impls. | `ResilienceContract` → [`…resilience-spi-resiliencecontract`](../../../../architecture/facts/generated/code-symbols.json); `SkillCapacityRegistry` → [`…resilience-spi-skillcapacityregistry`](../../../../architecture/facts/generated/code-symbols.json). Consults `skill-capacity.yaml` (Rule R-K). |
| `runtime/memory/spi` | Memory SPI scaffold (no W0 adapter; consumer impl in the graphmemory starter). | `GraphMemoryRepository` → [`…memory-spi-graphmemoryrepository`](../../../../architecture/facts/generated/code-symbols.json). |
| `runtime/s2c` | S2C callback transport reference impl (consumes `bus.spi.s2c`). | Suspension via the checked `SuspendSignal.forClientCallback(...)` variant (ADR-0074); envelope shape owned by [`s2c-callback.v1.yaml`](../../../../docs/contracts/s2c-callback.v1.yaml). |
| `runtime/idempotency` | `IdempotencyRecord` contract-spine entity (mandatory tenant identity — Rule R-C.c trigger). | Mirrors the persistence shape consumed by the platform-side store; schema is the §5.3 L2 Boundary Contract. |
| `runtime/evolution` | `EvolutionExport` enum — discriminator for the ADR-0145 sealed RunEvent hierarchy. | Variant + field shapes owned by [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml); the Java sealed type lands in a follow-up impl-mode wave. |
| `runtime/probe` | `OssApiProbe` — W0 OSS classpath shape probe. | Verification material (`tests.json`); green probe is a required per-wave gate. |

**Engine SPI + registry/envelope (consumed cross-module).**
`agent-service` **consumes** `agent-execution-engine` for the engine SPI
surface (`engine.spi.*`: `ExecutorAdapter` and friends) and the engine
registry + envelope (`engine.runtime.*`: `EngineRegistry`,
`EngineEnvelope`); these no longer live in this module (extracted per
ADR-0079; package rename completed per ADR-0090). Every Run dispatch
goes through `EngineRegistry.resolve(envelope)` (Rule R-M.a); strict
matching per Rule R-M.b raises `EngineMatchingException` on mismatch (no
fallback). The envelope shape is owned by
[`engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml).
The split-package arrangement is documented in
`agent-execution-engine/ARCHITECTURE.md` and protected by Rule 76.

**Wave-staged placeholders (W2-W4).** The following frames are declared
but ship no Java at W0; their realisation waves are listed in
[`development.md`](development.md) §4: `runtime.llm/` (W2),
`runtime.outbox/` (W2), `runtime.observability/` kernel side (W2),
`runtime.tool/` (W3), `runtime.action/` (W3), `runtime.temporal/` (W4).

## 3. Sub-package layering invariant (Phase C, ADR-0078)

**`service.runtime` MUST NOT import `service.platform`.** This preserves
the original cross-module purity (formerly `agent-runtime ↛
agent-platform`) as a within-module sub-package purity post-Phase-C. It
is enforced by **Rule R-C.e** via the ArchUnit mechanism
`ServiceRuntimeMustNotDependOnServicePlatformTest` (enforcer **E2**). The
reverse (`service.platform → service.runtime`) is permitted ONLY to the
runtime public surface, enforced by
`ServicePlatformImportsOnlyServiceRuntimePublicApiTest` (enforcer
**E34**): the `runs/` package, the neutral engine SPI
(`bus.spi.engine.*`), the `posture/` package, and the dev-posture-gated
in-memory run registry. Authority:
[`docs/adr/0078-agent-service-consolidation.yaml`](../../../../docs/adr/0078-agent-service-consolidation.yaml).

## Development View (Rule G-1.1.a — ADR-0099)

Package decomposition (the type inventory under each package is owned by
the generated code facts, `architecture/facts/generated/code-symbols.json`,
and is not restated here; the full source-rendered tree lives in
[`./development.md`](./development.md)):

```text
agent-service/
└── src/main/java/
    └── com/huawei/ascend/service/
        ├── platform/   # HTTP edge: web + tenant + idempotency + auth + posture + observability
        └── runtime/    # runtime kernel: runs + orchestration + resilience + engine adapters
```

The `service.runtime ↛ service.platform` sub-package layering invariant
(section 3) governs the boundary between the two clusters; the neutral
engine SPI the runtime consumes is owned by `agent-bus` (`bus.spi.engine`,
ADR-0158).

## 4. OSS dependencies

Dependency versions are managed by the parent POM and the
`spring-ai-ascend-dependencies` BoM; module files do not duplicate
version pins (consult `pom.xml` properties for canonical values).

| Dependency | Role | Side |
|---|---|---|
| Spring Boot starter web | HTTP server, MVC controllers + filters | platform |
| Spring Security OAuth2 Resource Server | JWT validation (RS256 + JWKS) | platform |
| Spring Security | Filter chain ordering | platform |
| HikariCP | DB pool (alongside the durable idempotency store) | platform |
| Flyway | Schema migrations | platform |
| Hibernate Validator | DTO + `@ConfigurationProperties` validation | platform |
| Jackson | JSON serialization | platform |
| Spring Boot actuator | Lifecycle + health + Prometheus scrape | both |
| Micrometer + Prometheus | Metrics (`springai_ascend_*` prefix) | both |
| Spring AI | `ChatClient` abstraction + provider bindings | runtime |
| MCP Java SDK | Tool protocol (per-tenant MCP registry, W3) | runtime |
| Temporal Java SDK | Durable workflows for long runs (W4) | runtime |
| Apache Tika | Document-parser reference tool (W3) | runtime |
| Resilience4j | Circuit breaker on LLM + tool calls | runtime |
| Caffeine | In-process cancel-flag cache | runtime |

## 5. Public contract (summary)

The concrete wire surface — versioned route prefix, route verbs, status
codes, header requirements, auth scheme, idempotency + tenant header
semantics — is owned by [`openapi-v1.yaml`](../../../../docs/contracts)
and the auth / idempotency / tenant ADRs (ADR-0040 / 0056 / 0057). At
L1: the module exposes a versioned REST/JSON run API, bearer-JWT
authenticated, with idempotency-key and tenant binding at the edge.

## 6. Posture-aware defaults

| Aspect | dev | research | prod |
|---|---|---|---|
| Missing tenant header | warn + DEV_DEFAULT | reject | reject |
| Weak JWT alg | accept w/ warning | reject | reject |
| Missing idempotency key on mutating request | accept w/ warning | reject | reject |
| LLM provider mock allowed | yes | no | no |
| Vault required for provider keys | no | yes | yes |
| Token budget enforced | off | on | on |
| OPA policy required | warn-only | enforced | enforced |
| Temporal for long runs | warn | enforced | enforced |
| Outbox sink (not log appender) | optional | required | required |

The exact response posture per row (status codes, reject behaviour) is
Layer 1 contract detail.

## 7. Tests + out-of-scope

The test inventory (which classes assert which enforcers) is
**verification material**, owned by the verification layer and the
generated facts `architecture/facts/generated/tests.json`; it is not
enumerated in this L1 file. Three-layer testing discipline per Rule D-4.

**Out of scope at L1** (delegated to later waves — see
[`development.md`](development.md) §4): GUC + RLS policies (W2); Spring
Cloud Gateway + per-tenant overrides (W2-W3); three-track dispatcher +
streaming event handoff (W2); LLM provider integrations beyond mocks
(W2); per-tenant MCP tool registry (W3); ActionGuard chain (W3);
Temporal workflow classes (W4).

## 8. Wave plan / risks

> **Phase C consolidation landed (ADR-0078):** the previous two L1
> modules `agent-platform` + `agent-runtime` were merged into the single
> deployable `agent-service`; the cross-module purity rule became the
> sub-package purity rule (Rule R-C.e, enforcer E2).

### 8.1 Wave landing (summary)

- **W0** — platform: web front door, tenant binding, idempotency
  validation, posture probe. Runtime: the neutral engine SPI contracts;
  the Run aggregate + state machine; posture-gated in-memory reference
  adapters; the resilience contract; the memory SPI scaffold; the
  idempotency contract-spine entity.
- **W1** — platform: JWT validation + tenant-claim cross-check;
  idempotency claim/replay store; boot-time fail-closed guard; the run
  API; observability tag scrub. Runtime: Telemetry-Vertical trace SPI.
- **Phase C** — merger + package rename; Rule R-C.e retargeted across
  the new sub-package boundary; old modules deleted.
- **W2** — tenant GUC + RLS (the §5.3 L2 zone); durable `RunRepository`;
  streaming event handoff; LLM gateway + outbox.
- **W3+** — per-tenant config; LLM gateway resilience routing; tool
  registry; ActionGuard.
- **W4** — Temporal workflow + activity classes for long-running runs.

### 8.2 Risks

- **Virtual-thread + JDBC pinning** — HikariCP wired alongside the
  durable idempotency store; watch pool metrics under load.
- **Filter ordering under Spring Security 6 / Boot 4** — the edge filter
  chain is order-pinned; the route contract integration test proves the
  chain end-to-end (verification material).
- **Idempotency claim→completion window** (W2 trigger) — an orchestrator
  crash between claim and completion leaves the row claimed until TTL;
  acceptable at L1, with a W2 completion hook per ADR-0057 §4.
- **Tenant-id confusion in multi-step requests** — every async handoff
  sources tenant from the persisted Run (Rule R-C.e), not a
  platform-side ThreadLocal; the ArchUnit purity mechanism (E2) + the
  `TenantPropagationPurityTest` mechanism defend this.
- **Sub-package purity drift** (Phase C-specific) — a refactor inside
  `service.runtime.*` may accidentally import `service.platform.*`; the
  E2 mechanism runs every `mvn verify`, and an explicit-fail self-test in
  the gate injects a synthetic violation to assert the gate catches it.
- **Engine extraction landed** (ADR-0079 / ADR-0090) — the engine SPI +
  registry/envelope live in `agent-execution-engine`; the
  `EngineRegistry.resolve` boundary is asserted by Rule R-M.a (enforcer
  E84) and consumed via the module dependency chain.

## 9. Roadmap

- Deferred capabilities + design decisions: the `deferred_sub_clauses:`
  block in each alphanumeric rule card under `docs/governance/rules/`;
  legacy rules awaiting human review at `docs/governance/escalations.md`.
  Current delivery state per wave:
  `docs/governance/architecture-status.yaml`.
- Phase C consolidation spec:
  [`docs/adr/0078-agent-service-consolidation.yaml`](../../../../docs/adr/0078-agent-service-consolidation.yaml).

---

## 10. L1 Runtime-Role Decomposition (ADR-0100)

The agent-service concentration-risk proposal, ratified by ADR-0100,
decomposes the module into **5 logical runtime-role components**:

| # | Component (sub-package) | Role |
|---|---|---|
| 1 | `dispatcher/` — Polymorphic Dispatcher | Unified entry point for both local function-call and remote bus-call invocations. |
| 2 | `orchestrator/` — Reactive Orchestrator | Task tempo control, backpressure handling, A2A envelope packaging. |
| 3 | `task/` — Task Center | Task control-state persistence (`Task` entity + `TaskStateStore` SPI; lifecycle Run ≤ Task). |
| 4 | `session/` — Session Manager | Middle/long-context management; context projection toward compute nodes (`Session` entity + `ContextProjector` SPI). |
| 5 | `engine/{adapter,spi}/` — Execution Engine Adapter | Masks Workflow vs ReAct engine differences; pure-function compute injection (`StatelessEngine` SPI). |

The existing `runtime/` package (Run / RunContext / RunStateMachine)
stays unchanged. The Layer↔Package mapping is the canonical matrix in
[`development.md`](development.md) §2.

### 10.1 Lifecycle hierarchy (ADR-0100)

```
Run     — transient compute snapshot (compute pointer + delta)
Task    — control state (done-or-not, why-stopped)
Session — data context (what was discussed, variables)
Memory  — knowledge state (consumed via GraphMemoryRepository SPI per ADR-0082)
```

TaskID and SessionID are logically decoupled: one Session may run many
Tasks; one Task may drift across many Sessions. Join semantics + audit
trail per ADR-0100 §non_goals.

### 10.2 New SPI surface (ADR-0100)

| Interface FQN | SPI package | Generated-fact ref |
|---|---|---|
| `…service.engine.spi.StatelessEngine` | `service.engine.spi` | [`code-symbol/com-huawei-ascend-service-engine-spi-statelessengine`](../../../../architecture/facts/generated/code-symbols.json) |
| `…service.session.spi.ContextProjector` | `service.session.spi` | [`code-symbol/com-huawei-ascend-service-session-spi-contextprojector`](../../../../architecture/facts/generated/code-symbols.json) |
| `…service.task.spi.TaskStateStore` | `service.task.spi` | [`code-symbol/com-huawei-ascend-service-task-spi-taskstatestore`](../../../../architecture/facts/generated/code-symbols.json) |

The pure-function engine contract — its method signature and the
`AgentInvokeRequest` wire shape — is contract material
(`agent-invoke-request.v1.yaml`, `design_only`) with the Java surface
in [`code-symbol/com-huawei-ascend-service-engine-spi-statelessengine`](../../../../architecture/facts/generated/code-symbols.json):
Service is the Read-Modify-Write closure boundary; Engine is the
Pure-Function compute boundary.

### 10.3 Coexistence + A2A policy (ADR-0100)

- `SuspendSignal` (checked exception) remains canonical for state-machine
  suspension. `Yield` is a separate cooperative-scheduling hint
  (`HookPoint.ON_YIELD`); the two coexist, they do not replace each other
  (ADR-0100 §decision).
- A2A protocol alignment proceeds at the **contract** layer
  (`a2a-envelope.v1.yaml` in a future ADR) without an SDK runtime
  dependency — the `a2a-java` SDK embedding was rejected (ADR-0100
  §non_goals).

## 11. Deployment loci (ADR-0101)

`deployment_loci: [platform_centric, business_centric]`. In Mode B the
module deploys on the business side alongside `agent-execution-engine`
for zero-latency local execution loops. See [`physical.md`](physical.md)
§1 for the plane mapping.

## *SPI Interface Appendix* (Rule G-1.1.b)

`agent-service` publishes its active Java SPI interfaces as the module's
public extension surface. The canonical 4-way-parity listing (SPI ↔
`module-metadata.yaml#spi_packages` ↔
`docs/contracts/contract-catalog.md` ↔ generated facts) lives in
[`spi-appendix.md`](spi-appendix.md); the table below is the module-root
summary with generated-fact refs. Records, sealed carriers, and enums in
the same packages are SPI-adjacent and are not counted as SPI
interfaces.

| Interface FQN | SPI package | Generated-fact ref | Status |
|---|---|---|---|
| `…service.runtime.runs.spi.RunRepository` | `service.runtime.runs.spi` | [`…runtime-runs-spi-runrepository`](../../../../architecture/facts/generated/code-symbols.json) | shipped |
| `…service.runtime.memory.spi.GraphMemoryRepository` | `service.runtime.memory.spi` | [`…memory-spi-graphmemoryrepository`](../../../../architecture/facts/generated/code-symbols.json) | shipped |
| `…service.runtime.resilience.spi.ResilienceContract` | `service.runtime.resilience.spi` | [`…resilience-spi-resiliencecontract`](../../../../architecture/facts/generated/code-symbols.json) | shipped |
| `…service.runtime.resilience.spi.SkillCapacityRegistry` | `service.runtime.resilience.spi` | [`…resilience-spi-skillcapacityregistry`](../../../../architecture/facts/generated/code-symbols.json) | shipped |
| `…service.engine.spi.StatelessEngine` | `service.engine.spi` | [`…engine-spi-statelessengine`](../../../../architecture/facts/generated/code-symbols.json) | declared (impl follows) |
| `…service.session.spi.ContextProjector` | `service.session.spi` | [`…session-spi-contextprojector`](../../../../architecture/facts/generated/code-symbols.json) | declared (impl follows) |
| `…service.task.spi.TaskStateStore` | `service.task.spi` | [`…task-spi-taskstatestore`](../../../../architecture/facts/generated/code-symbols.json) | declared (impl follows) |
| `…service.agent.spi.Agent` | `service.agent.spi` | [`…agent-spi-agent`](../../../../architecture/facts/generated/code-symbols.json) | design_only |
| `…service.agent.spi.AgentRegistry` | `service.agent.spi` | [`…agent-spi-agentregistry`](../../../../architecture/facts/generated/code-symbols.json) | design_only |

The SPI-adjacent structural carriers (policy / decision / request /
response / aggregate carriers near these packages) are listed in
[`spi-appendix.md`](spi-appendix.md); their generated-fact refs live in
`code-symbols.json`.

## *L2 Constraint Linkage* (Rule G-1.1.c)

The 5 delegated L2 zones — (1) Run lifecycle extended for Session
decoupling, (2) Reactive Orchestrator backpressure protocol, (3)
Postgres RLS migration sequence, (4) DualTrackRouter predicate
refinement, (5) Internal Event Queue binding — have their Boundary
Contracts published in [`development.md`](development.md) §5. Each future
L2 doc MUST satisfy the inputs / outputs / DFX obligations declared
there.

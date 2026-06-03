---
level: L1
view: development
module: agent-service
status: active
authority: "ADR-0143 (rc55 — canonical 4+1 source moved here) + ADR-0078 (consolidation) + ADR-0099 (rc22 — Rule G-1.1 L1 depth + grounding) + ADR-0100 (rc22 — 5-component decomposition) + ADR-0138 (rc53 — 5-layer L1 ratification) + ADR-0140 (rc55 — Engine Adapter split 5a/5b) + ADR-0141 (rc55 — Internal Event Queue design_only; service.queue/ NOT shown in tree) + ADR-0144 (rc55 — Layer↔Package matrix)"
---

# agent-service — Development View

> Authoring source: rc53 review file §18 + `ARCHITECTURE.md` §12 (current Development View tree), ported in rc55 W5 with corrections:
>
> - **R7** + **ADR-0144**: §2 publishes the canonical Layer↔Package matrix unifying ADR-0100's 5-component package-structural decomposition with ADR-0138's 5-layer logical-view decomposition.
> - **R2** + **ADR-0141**: `service.queue/` is NOT shown in the §1 tree (it does NOT exist on disk at rc55); referenced only in §4 "Future Sub-Packages" with ADR-0141 anchor.
> - **M8** + **M9**: §3 makes the logical-layer ↔ package-tree mapping explicit; §3.1 clarifies `service.dispatcher/` is a top-level package introduced at rc22 alongside the new 5-component decomposition (NOT a relocation of `web/` or `idempotency/` which stay under `platform/`).
> - **Rule G-1.1.c**: §5 publishes 5 L2 Boundary Contracts (4 from review §20 + 1 from ADR-0141's design-time Boundary Contract publication).

## 1. Target Directory Tree (Rule G-1.1.a — E166)

Cross-walked against filesystem at gate time. Every documented package
exists on disk; every existing production package is documented OR
explicitly enumerated under §4 as a future/staged sub-package.

```text
agent-service/
└── src/main/java/
    └── com/huawei/ascend/service/
        ├── dispatcher/                  # rc22 ADR-0100 — Polymorphic Dispatcher (top-level sub-package; entry-point intake)
        ├── orchestrator/                # rc22 ADR-0100 — Reactive Orchestrator (top-level sub-package; tempo control)
        ├── task/                        # rc22 ADR-0100 — Task aggregate
        │   └── spi/                     # TaskStateStore SPI (rc23 per ADR-0100)
        ├── session/                     # rc22 ADR-0100 — Session aggregate
        │   └── spi/                     # ContextProjector SPI (rc23 per ADR-0100)
        ├── engine/                      # rc23 ADR-0100 — Execution Engine Adapter (Layer 5a per ADR-0140)
        │   ├── adapter/                 # rc23 — ExecutionEngineAdapter impls (StatelessEngine consumers)
        │   └── spi/                     # rc23 — StatelessEngine SPI per ADR-0100
        ├── agent/                       # rc43 ADR-0128 — Agent first-class entity host
        │   └── spi/                     # rc43 — Agent + AgentRegistry SPIs (design_only)
        ├── integration/
        │   └── springai/                # rc51 — Spring AI reference adapter shells (Layer 5b per ADR-0140)
        ├── platform/                    # HTTP edge + cross-cutting (Phase C consolidation per ADR-0078)
        │   ├── auth/                    # AuthProperties, JwtDecoderConfig, JwtTenantClaimCrossCheck
        │   ├── engine/                  # StatelessEngineAutoConfiguration + adapter wiring
        │   ├── idempotency/             # IdempotencyHeaderFilter + IdempotencyStore (historical platform interface; not under .spi per Rule R-D.d)
        │   ├── observability/           # TenantTagMeterFilter, TraceExtractFilter (per ADR-0061 Telemetry Vertical)
        │   ├── persistence/             # DataSource / database presence conditions
        │   ├── posture/                 # PostureBootGuard (boot-time fail-closed per ADR-0058)
        │   ├── probe/                   # platform probe auto-configuration
        │   ├── resilience/              # resilience auto-configuration
        │   ├── tenant/                  # TenantContextFilter, TenantContextHolder, MDC binding
        │   └── web/                     # HealthController + WebSecurityConfig + ErrorEnvelope
        │       └── runs/                # RunController + RunHttpExceptionMapper + CreateRunRequest + RunResponse
        └── runtime/                     # Run kernel (Phase C consolidation per ADR-0078; post-ADR-0088 RunRepository SPI lives here)
            ├── evolution/               # EvolutionExport enum (rc55 ADR-0145 — sealed RunEvent hierarchy specification; Java sealed type lands in follow-up impl-mode wave)
            ├── idempotency/             # IdempotencyRecord contract-spine entity
            ├── memory/                  # GraphMemoryRepository SPI scaffold
            │   └── spi/                 # GraphMemoryRepository (1 interface; rc55 M10 audit — clean per rc55 W0 sibling sweep)
            ├── orchestration/
            │   └── inmemory/            # SyncOrchestrator + SequentialGraphExecutor + IterativeAgentLoopExecutor + InMemoryCheckpointer + InMemoryRunRegistry (posture-gated reference impls)
            ├── posture/                 # AppPostureGate (dev-only guard)
            ├── probe/                   # OssApiProbe (W0 classpath shape probe)
            ├── resilience/              # DefaultSkillResilienceContract + YamlResilienceContract + YamlSkillCapacityRegistry
            │   └── spi/                 # ResilienceContract + SkillCapacityRegistry + ResiliencePolicy + SkillResolution + SuspendReason
            ├── runs/                    # Run + RunStatus + RunStateMachine + RunMode (Run aggregate per ADR-0142 single-owner pinning)
            │   └── spi/                 # RunRepository SPI (atomic updateIfNotTerminal per ADR-0118)
            └── s2c/                     # InMemoryS2cCallbackTransport (consumes bus.spi.s2c per ADR-0088)
```

**Cross-walk verification status:** every directory listed above
exists on disk per the rc55 W5 filesystem scan. Future sub-packages
NOT yet on disk are explicitly listed under §4 with their ADR anchor.

### v1.2 SPI package additions (ADR-0155)

Two new SPI packages live under `agent-service/src/main/java/`:

```
service.runtime.executor.spi/
  ExecutorAdapter.java        (interface)
  InjectionMode.java          (enum — InjectionMode wiring choice per ADR-0155 §4)
service.runtime.intercept.spi/
  PlatformChatClient.java     (interface)
  PlatformToolCallback.java   (interface)
  PlatformMemoryProvider.java (interface)
  PlatformRetriever.java      (interface)
```

`InjectionMode` enum values: `NATIVE_DI | THIRD_PARTY_BRIDGE | EVENT_RELAY | NONE` — see L5a EDE-08 in the features inventory.

## 2. Layer ↔ Package Matrix (ADR-0144)

The canonical mapping between ADR-0138's 5-layer logical-view
decomposition and ADR-0100's 5-component (+ Phase C platform/runtime)
package-structural decomposition. Layer 5 is split into 5a + 5b per
ADR-0140.

| Logical Layer (ADR-0138 + ADR-0140) | Owned sub-packages (rc55 actual) | Notes |
|---|---|---|
| **1. Access Layer** | `service.dispatcher/` (rc22), `service.platform.web/` (+ `web/runs/`), `service.platform.idempotency/`, `service.platform.tenant/`, `service.platform.auth/`, `service.platform.observability/` (TraceExtractFilter) | Inbound protocol convergence + tenant binding + JWT cross-check + idempotency claim + trace origination. Future `service.platform.a2a/` (W3+) joins this layer when the A2A SDK lands. |
| **2. Session & Task Manager (Run aggregate owner per ADR-0142)** | `service.runtime.runs/` (+ `runs/spi/`), `service.task/` (+ `task/spi/`), `service.session/` (+ `session/spi/`), `service.runtime.idempotency/` | Owns Run / Task / Session aggregates + their SPIs (`RunRepository`, `TaskStateStore`, `ContextProjector`) + tenantId-first persistence. Run aggregate ownership pinned exclusively here. `RunRepository.updateIfNotTerminal(...)` atomic CAS is the SINGLE sanctioned status-transition path. |
| **3. Internal Event Queue** | `service.queue/` *(design_only — DOES NOT EXIST on filesystem at rc55 per ADR-0141)* | Binding-only layer over agent-bus three-track channels per Rule R-E. Code home deferred to a future wave; Boundary Contract published at design time in ADR-0141. |
| **4. Task-Centric Control Layer (RuntimeMiddleware exclusive home per ADR-0140)** | `service.orchestrator/` (rc22), `service.runtime.orchestration/` (+ `orchestration/inmemory/`), `service.runtime.posture/` | RunStateMachine validation invoked via Layer 2's CAS (ADR-0142). RuntimeMiddleware chain dispatched on HookPoint events (per ADR-0140 — exclusive home, no double-homing). DualTrackRouter *(design_only — W2, ADR-0112)*. SuspendSignal handling (child-run + S2C callback variants). |
| **5a. Engine Dispatch & Execution (ADR-0140)** | `service.engine.adapter/` (rc23), `service.engine.spi/` (rc23) — consumes `agent-runtime.engine.spi.*` cross-module per the allowed-dependency declaration in `agent-service/module-metadata.yaml` | EngineRegistry.resolve(envelope) per Rule R-M.a. ExecutorAdapter impls per Rule R-M.b strict matching. EngineHookSurface emits HookPoint events INTO Layer 4 (never directly invokes RuntimeMiddleware). |
| **5b. Translation & Tool-Intercept (ADR-0140)** | `service.integration.springai/` (rc51) + consumes `service.session.spi.ContextProjector` (cross-layer read-only) | ContextProjector → PromptTemplate → StructuredOutputConverter → ChatAdvisor composition. No RuntimeMiddleware here. Spring AI evolution cadence is independent of Rule R-M (the rationale for the split). |
| **(cross-cutting)** | `service.platform.posture/` (PostureBootGuard), `service.platform.persistence/`, `service.platform.engine/`, `service.platform.resilience/`, `service.platform.probe/`, `service.runtime.resilience/` (+ `resilience/spi/`), `service.runtime.memory/` (+ `memory/spi/`), `service.runtime.s2c/`, `service.runtime.evolution/`, `service.runtime.probe/`, `service.agent.spi/` | Cross-cutting concerns not owned by a specific layer: posture gating (boot fail-closed), persistence wiring, autoconfig, resilience contracts, memory SPI, S2C transport, evolution-export discriminator, probes, agent SPIs. |

**Layer assignment discipline going forward** (ADR-0144 §4):
- New sub-packages under `service.**` MUST be classified into exactly
  ONE layer OR the cross-cutting bucket — never two layers (the
  F-layer-decomposition-low-cohesion guard).
- The author MUST update this matrix in the same commit that adds the
  new sub-package.
- If a sub-package's responsibility crosses layers, split it OR refine
  the layer ownership BEFORE landing.

## 3. Logical Layer ↔ Package Tree Mapping (M8 + M9 clarifications)

### 3.1 `service.dispatcher/` boundary clarification (M9)

`service.dispatcher/` is a TOP-LEVEL sub-package introduced at rc22 per
ADR-0100 for the Polymorphic Dispatcher. It is a sibling of `platform/`
and `runtime/` in the package tree, NOT a sub-package under `platform/`.

The dispatcher participates in the **Layer 1 (Access Layer)** logical
view per ADR-0144, alongside `platform/web/`, `platform/idempotency/`,
`platform/tenant/`, `platform/auth/`, and `platform/observability/`
(TraceExtractFilter). Layer 1 is a LOGICAL grouping; the package tree
distributes the layer's components across the dispatcher + platform
sub-trees per the historical Phase C consolidation (ADR-0078).

**Rationale for the split** (rc22 ADR-0100): the dispatcher's
Polymorphic-Dispatcher responsibility is execution-domain (deciding
which engine adapter to call), distinct from `platform/web/`'s HTTP-edge
responsibility (parsing requests, error envelope shaping). They are
sibling sub-packages because they have different change cadences and
test surfaces; lumping them under `platform/` would obscure the
distinction.

### 3.2 `platform/` vs `runtime/` cross-cutting boundary

The `service.platform.*` sub-package owns HTTP-edge cross-cutting
concerns (web, idempotency, tenant, auth, observability, posture,
persistence, resilience auto-config, engine auto-config, probes).

The `service.runtime.*` sub-package owns Runtime-kernel concerns
(Run aggregate, RunRepository SPI, orchestration in-memory reference,
resilience SPI + impls, memory SPI, S2C transport impl, evolution
discriminator, idempotency entity, probes).

**Layering invariant** (Rule R-C.e, formerly Rule 21, retargeted at
Phase C per ADR-0078): `service.runtime.**` MUST NOT import any class
under `service.platform.**`. Enforced by ArchUnit
`ServiceRuntimeMustNotDependOnServicePlatformTest` (enforcer E2). The
reverse (`service.platform.* → service.runtime.*`) is permitted ONLY
to the runtime public surface declared by
`ServicePlatformImportsOnlyServiceRuntimePublicApiTest` (enforcer E34):
`runs/`, `bus.spi.engine.*`, `posture/`, and the dev-posture-
gated `InMemoryRunRegistry`.

### 3.3 New rc22 sub-packages (`dispatcher/orchestrator/task/session/engine/`)

These five top-level sub-packages were INTRODUCED at rc22 alongside
the ADR-0100 5-component decomposition. They are siblings of
`platform/` and `runtime/`, NOT nested inside them. The bulk Java
refactor (move Run/Task/Session aggregates from `runtime/runs/` etc.
into these new sub-packages) is rc23+ scope per the ADR-0100 timeline;
rc55 does NOT execute that refactor.

## 4. Future Sub-Packages (declared in design, NOT on disk at rc55)

| Sub-package | Wave | Authority | Status |
|---|---|---|---|
| `service.queue/` | W4+ (or W2 per scheduling) | ADR-0141 (Internal Event Queue design_only Boundary Contract) | NOT on disk; binding contract published in ADR-0141; will host the Layer 3 Producer/Consumer split routing to agent-bus three channels per Rule R-E |
| `service.runtime.llm/` | W2 | per `ARCHITECTURE.md` §2.B wave-staged placeholders | NOT on disk; LlmRouter + ChatClient beans + CostMetering |
| `service.runtime.outbox/` | W2 | per ARCHITECTURE.md | NOT on disk; Postgres-backed outbox + OutboxPublisher |
| `service.runtime.observability/` (kernel side) | W2 | per ARCHITECTURE.md | NOT on disk; custom metrics + span propagation |
| `service.runtime.tool/` | W3 | per ARCHITECTURE.md | NOT on disk; MCP server registry + per-tenant tool allowlist |
| `service.runtime.action/` | W3 | per ARCHITECTURE.md | NOT on disk; ActionGuard 5-stage filter chain |
| `service.runtime.temporal/` | W4 | per ARCHITECTURE.md | NOT on disk; Temporal workflow + activity classes (long-running runs) |
| `service.platform.a2a/` | W3+ | per ADR-0100 §rejected-framing #1 (CONTRACT-only A2A; no `a2a-java` SDK runtime dep) | NOT on disk; A2A Server + A2A Client when SDK adoption lands |
| `service.runtime.evolution.event.*` (sealed RunEvent + 10 records) | follow-up impl-mode rc | ADR-0145 + `docs/contracts/run-event.v1.yaml` (status: design_only) | NOT on disk; Java sealed type + 10 record variants land when impl-mode wave executes; Rule R-M.e becomes non-vacuous at that point |

**Discipline**: any new sub-package added under `service.**` MUST be
either listed here (with ADR anchor + wave) OR added to the §1 tree
in the same commit + classified under the §2 layer matrix.

## 5. L2 Boundary Contracts (Rule G-1.1.c — E168)

Five L2 zones are delegated from this L1 design. For each, the
Boundary Contract (inputs / outputs / DFX expectations) is published
HERE at design time so future L2 docs MUST satisfy these contracts.

### 5.1 L2 zone — Run lifecycle extended for Session decoupling (rc25 candidate per review §20)

```
inputs:
  - Run aggregate (per ADR-0142 single-owner pinning in Layer 2)
  - Session aggregate (per ADR-0100; per-Session context shared across Runs)
  - Task aggregate (per ADR-0100; control state distinct from Run execution state)
outputs:
  - Run-to-Session N:M projection per ADR-0135 (AgentSession-as-Run-projection)
  - Task-to-Session 1:N ownership per ADR-0100
  - Tenant-bound RLS coverage extending to the projection tables
dfx:
  - releasability: durable Postgres-backed projection store; in-memory ref impl W0
  - resilience: projection is read-replica; failure tolerated up to W2 SLA
  - availability: N:M projection eventually consistent; UI surfaces last-known
  - vulnerability: cross-tenant projection blocked at RLS layer
  - observability: projection lag metrics per tenant
```

### 5.2 L2 zone — Reactive Orchestrator backpressure protocol (rc23-25 candidate)

```
inputs:
  - SuspendSignal.BackpressureRequested (W2-deferred variant; sealed
    SuspendReason extension)
  - backpressure-request.v1.yaml contract (design_only per ADR-0100;
    bus control-track payload)
outputs:
  - Orchestrator-level admission decision (admit / queue / reject)
  - SkillCapacityRegistry-mediated capacity grants
dfx:
  - releasability: admission policy hot-reload from skill-capacity.yaml
  - resilience: backpressure escalation paths declared per skill row
  - availability: rejected admissions return to caller with SuspendReason envelope
  - vulnerability: per-tenant capacity not bypassable
  - observability: per-tenant admission decision metrics
```

### 5.3 L2 zone — Postgres RLS migration sequence (rc25 candidate)

```
inputs:
  - Existing Flyway migrations (V2__idempotency_dedup.sql grandfathered
    in gate/rls-baseline-grandfathered.txt)
  - tenant_id propagation from RunContext.tenantId() (Rule R-J.a)
outputs:
  - V?__tenant_rls.sql series creating RLS policies on all tenant_id-
    bearing tables (runs, tasks, sessions, lifecycle_state_audit)
  - SET LOCAL app.tenant_id GUC wiring via R2DBC
  - Backfill plan for grandfathered idempotency_dedup table
dfx:
  - releasability: per-table migration + smoke test; rollback plan documented
  - resilience: RLS bypass detection in dev (warn) / prod (fail)
  - availability: zero-downtime migration via dual-write window
  - vulnerability: cross-tenant SELECT returns empty (defense-in-depth)
  - observability: RLS-blocked SELECTs counted per tenant
```

### 5.4 L2 zone — DualTrackRouter predicate refinement (W2 candidate per ADR-0112)

```
inputs:
  - dual-track-routing-policy.yaml (design_only at W1; W2 follow-up)
  - SlowTrackJudge SPI per ADR-0112
  - per-tenant routing thresholds (estimated_wall_clock, has_external_input,
    has_s2c_callback, has_a2a_collab, estimated_deployment_locus)
outputs:
  - FastPath OR SlowPath decision per Run dispatch
  - Per-decision audit event (extends RunEvent hierarchy in a follow-up
    ADR if needed)
dfx:
  - releasability: routing-policy hot-reload from dual-track-routing-policy.yaml
  - resilience: misclassification recovery via mid-execution SuspendSignal
    upgrading FastPath → SlowPath
  - availability: routing decision is per-Run; failure tolerated
  - vulnerability: routing decision does NOT bypass tenantId / RLS / R-G / R-H / R-J.a (ADR-0139 red line)
  - observability: per-tenant FastPath/SlowPath ratio metrics
```

### 5.5 L2 zone — Internal Event Queue (Layer 3) Boundary Contract (ADR-0141 — published at design time even though no L2 doc exists yet)

```
inputs:
  - RunEvent emissions from Layer 2 Session & Task Manager (per ADR-0145)
  - control-channel signals from Layer 4 Control (cancel / resume / suspend)
  - rhythm-channel ticks from agent-bus Tick Engine (per Rule R-H / Chronos Hydration)
outputs:
  - control-channel publications (cancel / resume / suspend broadcasts to peer Run instances)
  - data-channel publications (payload + S2C response envelopes; inline ≤ 16 KiB per Rule R-E)
  - rhythm-channel publications (heartbeat / liveness ticks for the long-horizon Run)
dfx:
  - releasability: per-channel durability tier at deployment time (bus-channels.yaml#physical_channel)
  - resilience: at-least-once delivery on each channel with dedup keyed by (tenantId, idempotencyKey) per ADR-0057
  - availability: per-channel back-pressure surfaced as SuspendReason.BackpressureRequested (W2-deferred)
  - vulnerability: tenantId binding from IngressEnvelope to every channel emission (Rule R-J.a)
  - observability: per-channel metrics springai_ascend_queue_<channel>_<op>_total{tenantId}
```

## 6. Cross-references

- Scenarios: [`scenarios.md`](scenarios.md) — S1-S5 + cross-scenario invariants.
- Logical: [`logical.md`](logical.md) — 5-layer + ADR-0140 split + ER + state machines + RunEvent hierarchy.
- Process: [`process.md`](process.md) — sequence diagrams P1-P6.
- Physical: [`physical.md`](physical.md) — 5-plane deployment + RLS + 3-track bus + sandbox.
<<<<<<<< HEAD:docs/architecture/l0/l1/agent-service/development.md
- SPI Appendix: [`spi-appendix.md`](05-contracts/spi-appendix.md) — 9 active SPI 4-way parity.
- Module-root: [`agent-service/ARCHITECTURE.md`](../../../../../agent-service/ARCHITECTURE.md) — shipped components + dependencies + wave plan.
- ADRs: [ADR-0078](../../../../adr/0078-agent-service-consolidation.yaml) (consolidation), [ADR-0088](../../../../adr/0088-agent-runtime-core-dissolution.yaml) (kernel redistribution), [ADR-0099](../../../../adr/0099-rc22-l1-architecture-depth-and-grounding.yaml) (Rule G-1.1), [ADR-0100](../../../../adr/0100-rc22-agent-service-l1-runtime-role-decomposition.yaml) (5-component), [ADR-0138](../../../../adr/0138-agent-service-five-layer-l1-ratification.yaml) (5-layer), [ADR-0140](../../../../adr/0140-engine-adapter-layer-split.yaml) (5a/5b split), [ADR-0141](../../../../adr/0141-internal-event-queue-design-only.yaml) (Layer 3 design_only), [ADR-0142](../../../../adr/0142-run-aggregate-single-owner.yaml) (Run aggregate pinning), [ADR-0144](../../../../adr/0144-layer-vs-package-matrix.yaml) (THIS matrix), [ADR-0145](../../../../adr/0145-run-event-sealed-hierarchy.yaml) (sealed RunEvent).
========
- SPI Appendix: [`spi-appendix.md`](spi-appendix.md) — 9 active SPI 4-way parity.
- Module-root: [`ARCHITECTURE.md`](ARCHITECTURE.md) — shipped components + dependencies + wave plan.
- ADRs: [ADR-0078](../../../../docs/adr/0078-agent-service-consolidation.yaml) (consolidation), [ADR-0088](../../../../docs/adr/0088-agent-runtime-core-dissolution.yaml) (kernel redistribution), [ADR-0099](../../../../docs/adr/0099-rc22-l1-architecture-depth-and-grounding.yaml) (Rule G-1.1), [ADR-0100](../../../../docs/adr/0100-rc22-agent-service-l1-runtime-role-decomposition.yaml) (5-component), [ADR-0138](../../../../docs/adr/0138-agent-service-five-layer-l1-ratification.yaml) (5-layer), [ADR-0140](../../../../docs/adr/0140-engine-adapter-layer-split.yaml) (5a/5b split), [ADR-0141](../../../../docs/adr/0141-internal-event-queue-design-only.yaml) (Layer 3 design_only), [ADR-0142](../../../../docs/adr/0142-run-aggregate-single-owner.yaml) (Run aggregate pinning), [ADR-0144](../../../../docs/adr/0144-layer-vs-package-matrix.yaml) (THIS matrix), [ADR-0145](../../../../docs/adr/0145-run-event-sealed-hierarchy.yaml) (sealed RunEvent).
>>>>>>>> origin/main:architecture/docs/L1/agent-service/development.md

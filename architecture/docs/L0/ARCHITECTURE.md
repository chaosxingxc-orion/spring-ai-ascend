---
level: L0
view: scenarios
status: active
freeze_id: W1-russell-2026-05-14
covers_views: [logical, development, process, physical, scenarios]
spans_levels: [L0]
authority: "ADR-0068 (Layered 4+1 + Architecture Graph) + W1.x Phase 7 freeze activation (docs/logs/reviews/2026-05-15-w1x-phase7-freeze-activation.en.md)"
---

# spring-ai-ascend Platform — Architecture

> Last updated: 2026-05-13 (14th cycle — Cohesive Agent Swarm Execution + Long-Connection Containment via ADR-0053/0054 + §4 #51-#52; class-based self-audit surfaced 11-dimension SpawnEnvelope propagation gap + 3 uncovered resource-explosion vectors; B/B'/B''/P capability-label notation REJECTED with mapping documented in docs/logs/reviews/2026-05-13-l0-capability-labels-platformization-response.en.md; Whitepaper-Alignment Remediation — §4 #47-#50, ADR-0049/0050/0051/0052, Gate Rules 28-29, +5 self-tests (30→35); C/S Dynamic Hydration Protocol named at L0 contract level (ADR-0049 — TaskCursor / BusinessRuleSubset / SkillPoolLimit / HydrationRequest / SyncStateResponse / SubStreamFrame / YieldResponse / ResumeEnvelope + degradation authority red line); Workflow Intermediary + Mailbox Backpressure + Rhythm track restored as independent third cross-service bus track (ADR-0050 — WorkflowIntermediary / IntentEvent / Mailbox / AdmissionDecision / BackpressureSignal / WorkStateEvent / SleepDeclaration / WakeupPulse / TickEngine / ChronosHydration); Memory & Knowledge Ownership Boundary (ADR-0051 — C-side business ontology vs S-side trajectory vs delegated; BusinessFactEvent / OntologyUpdateCandidate / PlaceholderPreservationPolicy / SymbolicReturnEnvelope); Skill Topology Scheduler + Capability Bidding (ADR-0052 — two-axis tenant×global arbitration; SkillResourceMatrix / CapabilityRegistry / BidRequest / BidResponse / PermissionEnvelope / SkillSaturationYield); ADR-0048 narrowed (deployment-topology only; subordinate to ADR-0049/0050/0051; heartbeats moved from control bus to Rhythm track per ADR-0050); whitepaper alignment matrix at docs/governance/whitepaper-alignment-matrix.md; mandatory self-audit at docs/logs/reviews/2026-05-13-whitepaper-alignment-self-audit.en.md (PASS); release-note baseline truth gate (Rule R-C.a) closes drift between release notes and canonical YAML; whitepaper-alignment-matrix presence gate (Rule R-A) enforces 20 required concept rows; L0 release note frozen at SHA 82a1397 via freeze marker; closes reviewer findings P0-1/P0-2/P0-3/P0-4/P0-5/P1-1/P1-2/P2-1 from docs/logs/reviews/2026-05-13-whitepaper-alignment-remediation-proposal.en.md; Service-Layer Microservice-Architecture Commitment — §4 #46, ADR-0048, Service Layer deployed as long-running microservices coordinating via Agent Bus; bus traffic split locked at data-P2P / control-event-bus; serverless direction archived as future work at `docs/archive/2026-05-13-serverless-architecture-future-direction.md`; SPI primitives `SuspendSignal`/`Checkpointer`/`RunRepository`/`RunStateMachine` remain serverless-friendly so W4+ migration stays open; whitepaper §1.3 microservice-dictatorship trap mitigated by scoping microservice to the Service Layer and routing inter-agent calls through the bus by intent; L0 final entrypoint truth review — §4 #45, ADR-0047, Gate Rule 27, system-boundary prose split into target architecture vs W0 shipped subset, active-entrypoint baseline truth gate, header-metadata convention codified, +2 self-tests (28→30); L0 release-note contract review — §4 #44, ADR-0046, Gate Rule 26, GATE-SCOPE-GAP closure for `docs/logs/releases/*.md`, +4 self-tests (24→28); post-seventh third-pass: §4 #42-#43, ADR-0045, Gate Rules 24-25, Rule 19 strengthened, Rule 22 PS case-sensitivity fix, REF-DRIFT path-existence gate, HISTORY-PARADOX W0-evidence-skeleton archived, PERIPHERAL-DRIFT entry-point wave-qualifier gate, shared ACTIVE_NORMATIVE_DOCS enumerator, self-tests for Rules 19/22/24/25, refresh-metadata reconciliation across 11 active-corpus files).

## 0.4 Layered 4+1 view map (W1 — ADR-0068)

This document is the **L0 root** of the Layered 4+1 corpus introduced by Rule G-1 sub-clause .a / Rule G-1 sub-clause .b / ADR-0068. The reorganisation of sections under 4+1 view headings is staged across W1; until completion, the table below is the authoritative section-to-view classification consumed by `gate/build_architecture_graph.sh`:

| Section | Level | View | Notes |
|---|---|---|---|
| §1 System boundary | L0 | scenarios | golden-link / north-star description |
| §0.5.1 Tenant vertical | L0 | logical | domain concept (`TenantContext` carrier) |
| §0.5.2 Posture vertical | L0 | process | boot-time / fail-closed semantics |
| §0.5.3 Telemetry vertical | L0 | process | cross-cutting flow, OTLP wire format |
| §2 Module layout | L0 | development | package + module decomposition |
| §3 Threat model | L0 | physical | trust boundaries + sandbox topology |
| §4 (#1–#65) Architectural constraints | L0 | scenarios | constraint corpus (each #N carries its own view in the graph) |
| §5 Staged rollout | L0 | scenarios | wave plan |

Per-module L1 design lives under `architecture/docs/L1/<module>.md` (single-narrative shape — agent-bus / agent-runtime) OR `architecture/docs/L1/<module>/` (per-view directory shape — agent-service only, with rc55 4+1 per-view files + features/ + ARCHITECTURE.md companion). Deep technical designs are at `architecture/docs/L2/` (W8 — moved from `docs/L2/` per ADR-0150). All future edits to this file (root L0) MUST flow through `docs/logs/reviews/` once the document is frozen at L1 closure.

---

## 0.6 Rhetorical stance of this document

This document is the **declarative L0** system boundary + 65 numbered architectural constraints (§4 #1..#65). It states what the platform commits to STRUCTURALLY. It does NOT carry:

- **Enforcement logic** — read [`CLAUDE.md`](../../../CLAUDE.md) rule kernels + `docs/governance/rules/*.md` cards for that.
- **Runtime contracts** (wire shapes, route behavior, SPI signatures) — read [`docs/contracts/contract-catalog.md`](../../../docs/contracts/contract-catalog.md) for that.
- **L1 module design** (how a module realises its slice of the constraints) — read [`architecture/docs/L1/<module>{.md,/}`](../L1/) for that.
- **Per-capability shipped/deferred ledger** — read [`docs/governance/architecture-status.yaml#capabilities`](../../../docs/governance/architecture-status.yaml) for that.

Readers seeking "the architecture" should start at [`architecture/workspace.dsl`](../../workspace.dsl) (the machine-readable architecture authority root per ADR-0147 + ADR-0150). This document is one slice (the declarative constraint corpus), not the architecture as a whole.

## 0.7 Constraint ↔ Rule cross-reference

Each §4 constraint #N MAY be enforced by one or more `CLAUDE.md` rules (D-/R-/G-/M- namespace). The mapping lives in `architecture/generated/enforcers.dsl` (the workspace projection's `enforced_by` edges) and `docs/governance/enforcers.yaml`. Highest-cited pairs:

- §4 #10 module dependency direction ↔ Rule R-C.1 (Independent Module Evolution); enforcer E1.
- §4 #20 Run W0-race / W1.5+W2 CAS ↔ Rule R-C.2 (Run Contract Spine); enforcers E2, E58.
- §4 #56 JWT validation + tenant claim cross-check ↔ Rule R-J (Storage-Engine Tenant Isolation); enforcer E69.
- §4 #58 PostureBootGuard ↔ Rule D-6 (Posture-Aware Defaults); enforcer E55.
- §4 #65 Architecture workspace truth ↔ Rule G-1.b (Architecture Workspace Truth; W5/W8 amended); enforcers E56, E58.

The workspace projection at `docs/governance/architecture-workspace-graph.yaml` carries the full set of constraint↔rule↔enforcer chains.

---

## 1. System boundary

`spring-ai-ascend` is a self-hostable agent runtime architecture targeting two audiences in a stepped sequence (declared in §1.1 below). The term "agent runtime" used here is the generic system class; its concrete home is the `agent-runtime` Maven module, which per **ADR-0159** is the consolidated run-owning runtime (the former `agent-execution-engine` plus the runtime internals of the former `agent-service`), while `agent-service` is re-founded as the enterprise serviceization façade. The system boundary below is split into the **target architecture** (the W1–W4 product contract) and the **W0 shipped subset** (what runs today). All target-architecture sentences are written in target tense; W0 shipped behavior is enumerated separately below and in `§5`.

### 1.1 Audience boundary (2026-05-22)

The platform targets two distinct audiences in W0–W2 + W3+ sequence; the active design must satisfy BOTH audiences without compromising either. Misreading §1 as targeting a single audience produces false-negative reviews of governance choices.

**Audience A — framework-internal contributors (W0/W1/W2 primary).** Engineers building the platform's SPI surface, gate rules, and contract catalog. They consume `ARCHITECTURE.md`, the 65 §4 constraints, the ~88 enforcer rows, the ~64 ADRs, and `docs/governance/architecture-status.yaml` directly. The high governance / low surface-area baseline (per the rationale below) is calibrated for this audience.

**Audience B — external Spring developers (W0/W1/W2 secondary; W2/W3 primary).** Engineers integrating the platform into their own Spring Boot 4 + Java 21 applications. They consume `agent-client` SDK + `ChatClient` / `VectorStore` / MCP adapter shapes + `docs/quickstart.md`. Their developer-ergonomics surface lands at W2 (Hook SPI + LLM gateway un-freeze) and W3 (`agent-client` SDK GA per ADR-0063). At W0/W1 their surface is intentionally narrow.

**Audience C — regulated-industry self-host operators (W3+ deferred, vertical-agnostic).** Self-host operators in regulated industries, with **no single lead vertical**. They consume packaged appliances, compliance reports, FIPS-attested builds, and tamper-evident audit. Their surface is W3+ vertical scope, not W0/W1/W2 baseline. The platform's strategic positioning is **vertical-agnostic and Ascend/Kunpeng hardware-synergy-led** per ADR-0117, which resolved `architecture-status.yaml#strategic_decisions.audience_w3_vertical_positioning` (`open → resolved`): any industry is an illustrative example, never the product identity. Finance references that remain in historical ADRs, review records, and `docs/archive/` are decision history accurate as of their date, not the current framing.

**Rationale for governance-first refinement allocation.** The platform builds the contract surface (Audience A scaffolding) ahead of shipped code by design — refactoring contracts is more expensive than refactoring W0 code, and Audience B + C surfaces are stable functions of the contract surface. The 5 R1 refinement edits (2026-05-22 review) landing exclusively on internal-governance artefacts reflects this allocation, not a misprioritization. See `docs/logs/reviews/2026-05-22-architecture-design-document-review-r1-r2-response.en.md` §"Family α + Family β + META rebuttal" for full reasoning.

**Target architecture (W1–W4).** The W1–W4 product accepts authenticated tenant HTTP requests, drives LLMs through a tool-calling loop with audit-grade evidence, and persists durable side effects through an idempotent outbox. Built on Spring Boot 4.0.5 + Java 21.

**W0 shipped subset.** What runs at the current release: the `agent-runtime` library — A2A access (`A2aJsonRpcController` + the well-known agent-card endpoint + the egress output registry), the framework-neutral engine (`engine.spi.AgentRuntimeHandler` + `StreamAdapter`, `EngineDispatcher` + `AgentRuntimeHandlerRegistry`, openJiuwen ReAct adapter), task-centric `control` (the single write authority), `session` (`RuntimeSessionRepository`), the internal event queue, and the pure-Java `app.RuntimeApp` entry — exercised end-to-end by `examples/agent-runtime-a2a-llm-e2e` (real-LLM A2A run). `agent-bus` ships the neutral SPI surfaces (`bus.spi.engine`: `Orchestrator` / `SuspendSignal` / `Checkpointer` / `RunContext` / `RunMode` / `ExecutorDefinition`; `bus.spi.ingress`; `bus.spi.s2c`). The Run domain kernel, LLM gateway, tool registry, outbox publisher, durable Postgres checkpointer, ActionGuard, and Temporal workflow implementations are staged as W1–W4 design contracts (see `§5` + `docs/governance/architecture-status.yaml`); they are not present as half-built runtime paths.

**Not in scope:** admin UI, LangChain4j dispatch, Python sidecars (out-of-process IPC), multi-region replication, on-device models. In-process polyglot (GraalVM Polyglot embedded in the JVM) is a W3-optional sandbox impl per ADR-0018 — it is not a sidecar. See `docs/governance/architecture-status.yaml` (per-capability deferral ledger) and `docs/governance/escalations.md` (legacy rules awaiting human review) for deferred items.

---

## 0.5 Cross-cutting verticals

Three named verticals span every horizontal layer (HTTP edge → orchestration → executor → adapter → MCP). A vertical is a cross-cutting concern that every other capability MUST emit into via a single carrier — not a re-invented sibling per layer.

### 0.5.1 Tenant Vertical

Carrier: `TenantContext` (HTTP edge ThreadLocal, valid for one request) + `RunContext.tenantId()` (canonical inside orchestration, ADR-0023). Rule R-C.e (L1 generalisation per ADR-0055) enforces that no runtime production class imports any class under `com.huawei.ascend.service.platform..`; the original narrow case — no read of `TenantContextHolder` — is preserved as defence-in-depth. Every persisted row carries `tenant_id NOT NULL`. References: §4 #3, §4 #22, §4 #37, Rule R-C.e.

### 0.5.2 Posture Vertical

Carrier: `APP_POSTURE={dev|research|prod}` read once at boot. `AppPostureGate` and `PostureBootGuard` enforce posture-aware fail-closed defaults at construction and startup. References: §4 #2, §4 #32, ADR-0058.

### 0.5.3 Telemetry Vertical (NEW — L1.x contract surface)

Carrier: `TraceContext` SPI (companion to `RunContext`) + W3C `traceparent` propagation at the HTTP edge + Logback MDC (`tenant_id`, `trace_id`, `span_id`, `run_id`). The vertical owns three entities — `Trace`, `Span`, and `LlmCall` — defined in ADR-0061. Every LLM call, tool invocation, state transition, and middleware adapter emission goes through this vertical via the `Hook SPI` (§4 #16) or `TraceContext` (§4 #53–#59); no layer emits telemetry directly.

Wire format: OTLP/HTTP (Langfuse-compatible attribute namespace `gen_ai.*` + `langfuse.*`). Hybrid sink: OTLP exporter + `trace_store` Postgres dual-write per ADR-0017. Sampling is posture-aware (dev=100 %, research=10 %, prod=1 % head + tail-on-error at W4). MCP-only replay surface per §4 #59 preserves the §1 "no admin UI" exclusion.

Staged rollout:

- **L1.x**: ARCHITECTURE.md §4 #53–#59; ADR-0061/0062/0063; `TraceContext` SPI (Noop impl); `TraceExtractFilter` (HTTP edge, no OTel SDK dep); MDC expansion; `Run.traceId` + `Run.sessionId` columns (nullable); ArchUnit + integration enforcers that do not require the OTel SDK.
- **W2**: OTel SDK + `opentelemetry-spring-boot-starter`; OTLP exporter; Hook SPI un-frozen (§4 #16) with reference hooks (`TokenCounterHook`, `PiiRedactionHook`, `CostAttributionHook`, `LlmSpanEmitterHook`); `trace_store` Postgres + dual-write; `Run.traceId` NOT NULL.
- **W3**: `springai-ascend-client` (Java/Kotlin) per ADR-0063; `Score` entity; cost dashboards.
- **W4**: MCP replay tools (`get_run_trace`, `list_runs`, `get_llm_call`, `list_sessions`) per ADR-0017.

References: §4 #53–#59, ADR-0061/0062/0063, `docs/telemetry/policy.md`.

---

## 2. Module layout

### Eight-module post-ADR-0159 state (2026-06-03 — agent-runtime consolidation)

The reactor declares **8 modules** today: six L0 team-facing substantive
modules (AgentClient, AgentService, AgentMiddleware, AgentRuntime,
AgentBus, AgentEvolve) + the BoM + the graphmemory starter. The arc that
brought us here: pre-Phase-C `agent-platform` + the original `agent-runtime`
were **consolidated** into `agent-service` per **ADR-0078** (2026-05-18); the
shared kernel was extracted then dissolved (ADR-0079 / ADR-0088) and the neutral
orchestration/engine SPI re-homed to `agent-bus` per ADR-0158. **ADR-0159**
(2026-06-03) then re-partitioned along the true seam: the mislabeled
`agent-service` runtime SDK and the `agent-execution-engine` are **consolidated
into a new run-owning `agent-runtime`** (package root
`com.huawei.ascend.runtime.*`), and `agent-service` is **re-founded** as the
enterprise serviceization façade. Module count is unchanged —
`agent-execution-engine` was renamed/absorbed into `agent-runtime`,
`agent-service` is retained. Ownership under ADR-0159:

- The **run-owning runtime SDK** — framework-neutral engine (`runtime.engine.spi`
  + `EngineDispatcher`), access (`runtime.access`, A2A), session, task-centric
  `control`, internal queue, and the pure-Java entry `runtime.app.RuntimeApp` —
  lives in **agent-runtime** (ships as a library). The Run domain entities (`Run` / `RunStatus` /
  `RunStateMachine` / `RunRepository` / `IdempotencyRecord`) are a **design
  target** owned by this module; their executable kernel is deferred to the
  implementation phase (design-phase repo — not stubbed early).
- `RunMode` (enum discriminator: `GRAPH` | `AGENT_LOOP`) plus the orchestration SPI
  interfaces (`Checkpointer` / `Orchestrator` / `RunContext` / `SuspendSignal` / `TraceContext` /
  `ExecutorDefinition`) now live in **agent-bus** under
  `com.huawei.ascend.bus.spi.engine` per ADR-0158 (transport-agnostic EnginePort
  boundary) — the neutral execution contract is owned by the Bus & State Hub plane
  so the engine is treated as a real instance behind a port. The shim that briefly
  co-located them in the engine module was retired; ADR-0158 re-homed the neutral SPI
  to agent-bus, and `agent-runtime` consumes it directly (the engine runs in-process
  behind `EngineDispatcher`; the former `InProcessEnginePort` realization was retired
  by the pure rebuild) while owning the full run-owning runtime SDK (ADR-0159).
- The 3 S2C transport types into **agent-bus** under `com.huawei.ascend.bus.spi.s2c` —
  pairing with the **new** `IngressGateway` SPI in `com.huawei.ascend.bus.spi.ingress`
  (**ADR-0089**, 2026-05-20) so the Bus & State Hub plane owns the entirety of
  cross-plane traffic in both directions (C2S and S2C).

After ADR-0159 `agent-service` carries no runtime internals; the former
`service.runtime -> service.platform` sub-package invariant (Rule R-C.e) is now
satisfied vacuously, and the runtime↔façade boundary is the Maven-module edge
`agent-service -> agent-runtime` (Rule 10 / ArchUnit).

| Module | Plane (P-I) | Owner team | Maturity today |
|--------|-------------|-----------|----------------|
| `agent-runtime` | compute_control | AgentRuntime | run-owning runtime SDK (ADR-0159) — framework-neutral engine (`engine.spi.AgentRuntimeHandler` + `StreamAdapter`; `EngineDispatcher` + `AgentRuntimeHandlerRegistry`) + access (`runtime.access`, A2A) + session + task-centric control + internal queue + pure-Java entry `app.RuntimeApp` / `LocalA2aRuntimeHost`; consumes the neutral `bus.spi.engine` boundary. Ships as a library; Run domain kernel is a design target, impl deferred |
| `agent-service` | compute_control | AgentService | serviceization façade skeleton (ADR-0159) — enterprise serviceization layer that will drive `agent-runtime`-hosted Agent instances via registration/discovery (deferred; single placeholder SPI today); all runtime internals relocated to `agent-runtime` |
| `agent-bus` | bus_state | AgentBus | active SPI surfaces — `bus.spi.ingress` (IngressGateway per ADR-0089) + `bus.spi.s2c` (S2cCallbackTransport per ADR-0088) + `bus.spi.engine` (neutral EnginePort + orchestration SPI: RunMode + Checkpointer + Orchestrator + RunContext + SuspendSignal + TraceContext + ExecutorDefinition + ExecutionContext per ADR-0158). Workflow primitives + W2 channel impls per ADR-0050 |
| `spring-ai-ascend-dependencies` | none | platform | shipped (BoM) |

(The historical `agent-client` / `agent-middleware` / `agent-evolve` / `spring-ai-ascend-graphmemory-starter` modules are retired or never materialized and are NOT in the reactor.)

Per-module `module-metadata.yaml` (Rule R-C.b), `ARCHITECTURE.md` (Rule G-1 sub-clause .a), and
`docs/dfx/<module>.yaml` (Rule R-D sub-clause .a) carry the authoritative identity, layered-4+1 view,
and Design-for-X declarations for each module.

### Tree (skeletons elided for brevity — see each module's own ARCHITECTURE.md)

```
spring-ai-ascend/
  pom.xml                                      # parent BOM (Java 21, Spring Boot 4.0.5)

  spring-ai-ascend-dependencies/               # Bill of Materials — pins all module +
    pom.xml                                    #   OSS transitive coords; no code

  agent-bus/                                   # Bus & State Hub plane — cross-plane control surfaces in BOTH directions (bus_state plane; ADR-0050 + ADR-0088 + ADR-0089)
    pom.xml + module-metadata.yaml + ARCHITECTURE.md + docs/dfx/agent-bus.yaml
    src/main/java/com/huawei/ascend/bus/spi/
      ingress/                                 # NEW 2026-05-20 per ADR-0089: client-to-server ingress SPI
        IngressGateway.java                    # the cross-plane C2S control surface; consumed by edge plane (agent-client) at W3+
        IngressEnvelope.java                   # 6-required-field request shape (Rule R-C.c tenant scope)
        IngressResponse.java                   # 4-field response carrying Task Cursor (Rule R-F) on ACCEPTED RUN_CREATE
      s2c/                                     # NEW 2026-05-20 per ADR-0088 (relocated from agent-runtime-core): server-to-client S2C SPI
        S2cCallbackTransport.java              # transport interface (ADR-0074)
        S2cCallbackEnvelope.java               # 6-required-field S2C request shape
        S2cCallbackResponse.java               # outcome enum + correlation fields
      engine/                                  # NEW per ADR-0158: neutral EnginePort + orchestration SPI (re-homed from agent-runtime)
        EnginePort.java                        # transport-agnostic engine boundary; engine is a real instance behind this port
        Orchestrator.java                      # top-level orchestration entry-point SPI
        RunContext.java                        # per-run context interface
        ExecutionContext.java                  # engine-boundary execution context (ADR-0158 sibling type)
        SuspendSignal.java                     # checked-suspension primitive with forClientCallback variant (ADR-0074 rc3 unification)
        Checkpointer.java                      # suspend-point persistence SPI
        TraceContext.java                      # trace correlation carrier
        RunMode.java                           # engine type discriminator (GRAPH | AGENT_LOOP)
        ExecutorDefinition.java                # sealed: GraphDefinition | AgentLoopDefinition

  agent-runtime/                      # run-owning runtime SDK (compute_control plane; ADR-0159). Ships as a library.
    pom.xml + module-metadata.yaml + ARCHITECTURE.md + docs/dfx/agent-runtime.yaml
    src/main/java/com/huawei/ascend/runtime/
      engine/spi/                              # AgentRuntimeHandler, StreamAdapter, AbstractAgentRuntimeHandler, AgentExecutionResult (framework-neutral runtime SPI)
      engine/api/                              # EngineExecutionApi (inbound enqueue: execute / resume / cancel)
      engine/command/                          # EngineWorker + internal command queue
      engine/port/                             # TaskControlClient, AccessLayerClient (outbound; intra-service, not SPI)
      engine/openjiuwen/                       # openJiuwen ReAct AgentRuntimeHandler adapter
      engine/EngineDispatcher.java             # routes an EngineCommandEvent to the registered handler by agentId
      access/                                  # A2A protocol ingress + egress: A2aJsonRpcController, A2aWellKnownAgentCardController, output registry
      session/ control/ queue/ schema/         # session + task-centric control (single authority) + internal queue + response types
      app/                                     # RuntimeApp / RuntimeHost / LocalA2aRuntimeHost + RuntimeWiringConfiguration
    # The neutral orchestration/engine SPI (Orchestrator, RunContext, SuspendSignal, Checkpointer,
    # TraceContext, RunMode, ExecutorDefinition, ExecutionContext) lives in agent-bus under
    # com.huawei.ascend.bus.spi.engine (ADR-0158); this module consumes it.

  agent-service/                               # Enterprise serviceization façade skeleton (ADR-0159) — registration/discovery deferred
    pom.xml + module-metadata.yaml + ARCHITECTURE.md companion + docs/dfx/agent-service.yaml
    src/main/java/com/huawei/ascend/service/
      spi/                                     # placeholder SPI (package-info); registration/discovery SPI lands in a later ADR.
                                               # All former platform/runtime/engine internals relocated to agent-runtime per ADR-0159.

```

Historical note (pre-ADR-0078 / pre-ADR-0079): the reactor previously had
separate `agent-platform/` and `agent-runtime/` modules. ADR-0078 (Phase C,
2026-05-18) merged them into the consolidated `agent-service/` module with
sub-package layering shown above. ADR-0079 (T2.B2, 2026-05-18) then extracted
the shared kernel SPI types out of `agent-service.service.runtime.*` into the
new `agent-runtime-core/` module so the back-dependency between engine and
runtime kernel could be resolved without circular Maven references. **ADR-0159**
(2026-06-03) later re-used the `agent-runtime` module name for the consolidated
run-owning runtime (the former `agent-execution-engine` + the `agent-service`
runtime internals); it is unrelated to the long-dissolved `agent-runtime-core` shim.

Module dependency direction (enforced by `ApiCompatibilityTest`, `RuntimeMustNotDependOnPlatformTest`, `OrchestrationSpiArchTest`, `MemorySpiArchTest`, `SpiPurityGeneralizedArchTest`, and `EdgeToComputeDirectLinkArchTest` ArchUnit rules — post-ADR-0078 + ADR-0088 + ADR-0089):

```
agent-service  ────────────►  agent-runtime, agent-bus, [Postgres / LLMs / sidecars]

agent-runtime ───►  agent-bus (for the neutral bus.spi.engine RunContext / SuspendSignal types per ADR-0158),
                              [externals — framework-neutral engine (AgentRuntimeHandler) + EngineDispatcher
                              + access (A2A) + session + task-centric control + app.RuntimeApp]

agent-bus  ──────────────►  [externals only — pure-Java SPI;
                              owns ingress + s2c + neutral engine surfaces]
```

The original pre-Phase-C `agent-runtime → agent-platform` Maven dependency was
unused at the source level and was removed per ADR-0026; both of those modules
no longer exist as separate Maven modules after ADR-0078 (Phase C consolidation).
The negative invariant Rule R-C.e was generalised to sub-package layering inside
`agent-service`: no class under `com.huawei.ascend.service.runtime..` may import any
class under `com.huawei.ascend.service.platform..` (broad — enforced by
`RuntimeMustNotDependOnPlatformTest`) and the original narrow case (no import of
`TenantContextHolder`) is preserved as defence-in-depth (enforced by
`TenantPropagationPurityTest`). The HTTP edge MUST NOT import memory SPI or
internal runtime impl packages (enforced by `PlatformImportsOnlyRuntimePublicApiTest`).
SPI packages must avoid implementation and framework dependencies. The strict
SPI-purity rule applies to the current SPI homes `com.huawei.ascend.bus.spi..`
and `com.huawei.ascend.runtime.engine.spi..`: they import only `java.*` plus
same-package sibling carriers. (The rc52 `com.huawei.ascend.middleware..spi..`
sweep is historical — `agent-middleware` was retired by ADR-0159.) Older non-
middleware SPI packages still carry documented legacy cross-package references
listed in §3.7 and require a separate migration before the strict rule becomes
repo-wide.

---

## 3. OSS dependencies

| Component | Version | Role |
|---|---|---|
| Spring Boot | 4.0.5 | HTTP server, DI container, actuator |
| Spring AI | 2.0.0-M5 (milestone; not GA) | ChatClient, VectorStore, MCP adapters — `gate/check_spring_ai_milestone.sh` enforces re-evaluation by 2026-08-01; see `docs/cross-cutting/oss-bill-of-materials.md` §3.1. W2 LLM-gateway surfaces consuming `ChatClient`/Advisor APIs (§4 #16, §4 #56) are most exposed to API drift between M5 and GA |
| Spring Security | 6.x | JWT filter chain, SecurityFilterChain |
| Spring Cloud Gateway | see parent POM (`spring-cloud.version`) | Edge routing (W2) |
| MCP Java SDK | see parent POM (`mcp.version`) | Tool protocol (W3) |
| Java (OpenJDK) | 21 | Virtual threads (Project Loom) |
| PostgreSQL | 16 | Relational + vector (pgvector) + outbox |
| Flyway | see parent POM | Schema migrations |
| HikariCP | see parent POM | Connection pool |
| Temporal Java SDK | see parent POM (`temporal.version`) | Durable workflow engine (W4) |
| Resilience4j | see parent POM (`resilience4j.version`) | Circuit breaker, rate limiter |
| Caffeine | see parent POM (`caffeine.version`) | In-process L0 cache |
| Apache Tika | see parent POM | Document parsing (W3) |
| Micrometer + Prometheus | latest | Metrics (`springai_ascend_*` prefix) |
| Testcontainers | see parent POM (`testcontainers.version`) | Integration test containers |
| Maven | 3.9 | Build, multi-module |

---

## 4. Architecture constraints

1. **Dependency direction** (post-ADR-0159 — agent-runtime consolidation; supersedes
   the post-ADR-0088 intermediate state): the Maven-level direction in the 4-module reactor
   (spring-ai-ascend-dependencies BoM, agent-bus, agent-runtime, agent-service) is
   (a) `agent-service` (serviceization façade skeleton) depends on `agent-runtime` and `agent-bus`
   to drive runtime-hosted Agent instances; registration/discovery SPI is deferred per ADR-0159;
   (b) `agent-runtime` (run-owning runtime SDK) depends on `agent-bus` only (for the neutral
   `bus.spi.engine` types — RunContext / SuspendSignal / Checkpointer / RunMode / ExecutorDefinition —
   and `bus.spi.s2c`), never on `agent-service`; the engine runs in-process behind `EngineDispatcher`
   (the former `InProcessEnginePort` realization was retired by the pure rebuild). ADR-0159
   supersedes only ADR-0158 §Decision.5 (engine tenant-neutrality): the run-owning runtime owns
   Run/session/tenant while the neutral SPI stays in `agent-bus`;
   (c) `agent-bus` depends on no inner peer — only `java.*` + minimal externals;
   (d) the Run domain kernel (`Run` / `RunStatus` / `RunStateMachine` / `RunRepository` /
   `IdempotencyRecord`) is owned by `agent-runtime` as a design target; its executable kernel is
   deferred to the implementation phase. The historical `agent-runtime-core` shim was dissolved per
   ADR-0088 (ADR-0079 superseded); the runtime↔façade boundary, previously a sub-package layering
   invariant inside the consolidated `agent-service`, is now the Maven-module edge
   `agent-service -> agent-runtime` (Rule 10 / ArchUnit).

2. **Posture model**: `APP_POSTURE={dev|research|prod}`. Read once at boot.
   `dev` is permissive (in-memory stores, relaxed validation).
   `research` and `prod` are fail-closed (Vault secrets, durable stores, strict JWT).

3. **Tenant isolation** (phased by wave):
   - W0 (shipped): `TenantContextFilter` reads `X-Tenant-Id` header (UUID shape),
     stores in `TenantContextHolder` + MDC. Every persistent record carries
     `tenant_id NOT NULL`.
   - W1 (planned): add JWT `tenant_id` claim cross-check against the existing
     `X-Tenant-Id` header; validate against `tenants` table (ADR-0040).
   - W2 (planned): add `SET LOCAL app.tenant_id = :id` GUC inside each transaction;
     enable Postgres RLS policies on tenant tables. See ADR-0005, ADR-0023.

4. **Idempotency** (phased by wave):
   - W0 (shipped): `IdempotencyHeaderFilter` validates the `Idempotency-Key` header
     (UUID shape, required on POST/PUT/PATCH; missing returns 400 in research/prod).
     No deduplication, no caching, no `IdempotencyStore` interaction.
   - W1 (planned): wire `IdempotencyStore` with `(tenant_id, key)` claim/replay
     semantics; concurrent duplicate returns 409; backed by Postgres `idempotency_dedup`
     table. See ADR-0027.

5. **Metric naming**: all custom Micrometer metrics use the prefix
   `springai_ascend_`. No bare or provider-prefixed names on platform meters.
   Span attribute naming follows `gen_ai.*` (OTel semconv) and `langfuse.*`
   (platform-specific) per §4 #56 and `docs/telemetry/policy.md §4`.

6. **OSS-first**: every core concern is delegated to an existing OSS project.
   New glue must answer "why is this not a configuration of an existing OSS dep?"
   Glue LOC target ≤ 1 500 at W0 close.

7. **SPI purity**: new SPI surfaces default to strict package purity. The current
   SPI homes are `com.huawei.ascend.bus.spi..` (neutral engine / ingress / s2c) and
   `com.huawei.ascend.runtime.engine.spi..` (`AgentRuntimeHandler` / `StreamAdapter`);
   they import only `java.*` plus same-package sibling carriers. Cross-SPI dependency
   is not an allowed escape hatch; adapter layers translate between packages.
   (Historical: the rc52 corrective sweep applied this rule to the advisor / memory /
   model / vector / retrieval / prompt / embedding / skill SPI packages of the
   now-retired `agent-middleware` module — `SpiPurityGeneralizedArchTest`; those
   packages no longer exist post-ADR-0159.)

   Historical pre-rc52 SPI packages still contain documented cross-package
   relationships that predate the strict rule: `agent-bus` federation
   uses ingress envelopes; `agent-runtime.engine.spi` (now `AgentRuntimeHandler` /
   `StreamAdapter`) consumes the neutral `bus.spi.engine` carriers;
   `agent-service.agent.spi` exposes agent bindings to
   model/skill/memory/planner refs; `RunRepository` uses the runtime `Run` domain
   type. These are treated as legacy residuals, not precedent for new SPI design.
   No SPI may depend on Spring, Micrometer, OTel, platform web/security packages,
   or in-memory/reference implementations.

8. **Per-operation resilience routing**: `ResilienceContract` maps `operationId`
   (e.g. `"llm-call"`, `"vector-search"`) to a `ResiliencePolicy(cbName, retryName, tlName)`.
   Call sites use Resilience4j annotations with the resolved names. Spring
   `@ConfigurationProperties` wiring is deferred to W2 LLM gateway.

9. **Dual-mode runtime + interrupt-driven nesting**: both `GraphExecutor` (deterministic
   state machine) and `AgentLoopExecutor` (ReAct-style) use one interrupt primitive
   (`SuspendSignal`) to delegate to a child run. Ownership at suspension is split:
   executors persist executor-local **resume cursors** (keys `_graph_next_node`,
   `_loop_resume_iter`, `_loop_resume_state`) via `Checkpointer.save()`; the
   `Orchestrator` persists the **Run row** (status=SUSPENDED) via `RunRepository.save()`.
   Both writes must be observable atomically (ADR-0024 — sequential at W0, transactional
   at W2). `Run.mode` discriminates `GRAPH` vs `AGENT_LOOP`; `Run.parentRunId` +
   `Run.parentNodeKey` encode the nesting chain. Durability tiers: in-memory (dev/W0)
   → Postgres checkpoint (W2) → Temporal child workflow (W4). Layered SPI taxonomy:
   stable cross-tier core (Layer 1: `Run`, `RunStatus`, `RunRepository`, `RunContext`,
   `Orchestrator`) + tier-specific adapters (Layers 2–3: `Checkpointer`,
   `IdempotencyStore`); W4 Temporal bypasses Layer 3 entirely (ADR-0021).

10. **Long-horizon lifecycle.** `Run` is an execution record; long-horizon agent identity
    is `AgentSubject` (deferred — `agent_subject_identity`). `SuspendSignal` will gain typed
    reasons (`ChildRun | AwaitTimer | AwaitExternal | AwaitApproval | RateLimited`); single-cause
    suspend is a W0 reference-only constraint. `RunRepository` queries that may return unbounded
    sets MUST gain `Pageable` parameters before W2 (`repository_paging_contract`). No `archivedAt`
    hook at W0; archival lifecycle is deferred.

11. **Northbound handoff contract.** Three modes: synchronous `Object` return (shipped), streamed
    `Flux<RunEvent>` (deferred W2 — Rule 15), yield-via-`SuspendSignal` (shipped). When streaming
    is introduced, the surface MUST carry: (a) backpressure strategy, (b) cancellation propagation
    to `RunStatus.CANCELLED`, (c) heartbeat cadence ≤ 30 s, (d) terminal frame with `runId` +
    final `RunStatus`, (e) typed progress events — no raw `Object`. The W2 streamed surface is
    split into three physical tracks (§4 #28): Control (cancel/suspend commands), Data
    (`Flux<RunEvent>` progress), Heartbeat (liveness cadence). See `streamed_handoff_mode`,
    `orchestrator_cancellation_handshake`, `three_track_channel_isolation`, ADR-0031.

12. **Two-axis resource arbitration.** `ResilienceContract.resolve(operationId)` extends to a
    two-axis policy `(tenantQuota, globalSkillCapacity)` (`skill_capacity_matrix`). Skill saturation
    MUST suspend the Run (`SUSPENDED + suspendedAt + reason=RateLimited`) rather than fail. Call-tree
    budget propagates through `RunContext` (`call_tree_budget_propagation`). Per Rule 16. The Skill
    SPI (§4 #27) adds per-skill `SkillResourceMatrix` declarations that feed into both quota axes;
    see ADR-0030.

13. **Payload addressing and serialization contract.** `Checkpointer.save` carries opaque bytes
    ≤ 16 KiB inline; larger payloads MUST be references to `PayloadStore` (`payload_store_spi`).
    The 16-KiB cap is enforced at W0 by `InMemoryCheckpointer` (posture-aware: dev warns, research/
    prod throws). `SuspendSignal.resumePayload` is an in-process `Object` correct for W0 in-memory
    only; when the durability tier crosses JVM boundaries (W2 Postgres, W4 Temporal), resumePayload
    MUST be serializable to bytes (`serializable_resume_payload`). Above the serialization layer,
    every payload that crosses a suspend boundary MUST be wrapped in a `CausalPayloadEnvelope`
    (§4 #25) declaring its `SemanticOntology` and carrying a SHA-256 fingerprint for tamper
    detection. Checkpoint eviction: Runs in terminal status become evictable after N days (deferred
    — `checkpoint_eviction_policy`). See ADR-0028.

14. **Resume re-authorization.** Resuming a suspended Run is a re-authorization boundary.
    The resume request's tenant context MUST match the original `Run.tenantId`; mismatch returns
    403 (`resume_reauthorization_check`). Actor identity at resume is captured in an audit envelope.
    Degradation authority: S-side may substitute means (alternative tool/model) without C-side
    approval; ends-modification requires explicit C-side authority. Per Rule 17.

15. **SPI serialization path.** Orchestration SPI types are pure Java (`OrchestrationSpiArchTest`)
    AND must be wire-serializable by W4. `ExecutorDefinition.NodeFunction` / `Reasoner` are inline
    lambdas at W0 — correct for in-process; before W2 Postgres-backed async orchestrator, they
    MUST become named `CapabilityRegistry` entries resolved by name, not inline closures
    (`capability_registry_spi`, `executor_definition_serialization`).

16. **Runtime Hook SPI [RETIRED / design_only — agent-runtime pure rebuild, ADR-0159].** This
    constraint described the pre-rebuild hook/middleware runtime, which was REMOVED: no `HookPoint`
    / `RuntimeMiddleware` / `HookDispatcher` Java type exists and `docs/contracts/engine-hooks.v1.yaml`
    is `design_only`. The text below is retained as design reference for a future hook vision, NOT a
    current MUST. (Design vision:) every LLM invocation, tool call, memory access, suspension, resume,
    and error boundary would flow through a hook chain. The canonical 10 hook positions (single
    source of truth: `docs/contracts/engine-hooks.v1.yaml`) are:
    `BEFORE_LLM_INVOCATION` / `AFTER_LLM_INVOCATION` /
    `BEFORE_TOOL_INVOCATION` / `AFTER_TOOL_INVOCATION` /
    `BEFORE_MEMORY_READ` / `AFTER_MEMORY_WRITE` /
    `BEFORE_SUSPENSION` / `BEFORE_RESUME` / `ON_ERROR` / `ON_YIELD` (last added rc22 per
    ADR-0100). Hooks are pluggable `RuntimeMiddleware` beans dispatched by
    `agent-middleware.HookDispatcher`; the chain is **ordered** (registration order; lower
    `@Order` fires earlier; `BEFORE_*` ascending, `AFTER_*` reverse — LIFO unwind) and exhibits
    **two-level failure semantics**: (a) **fail-fast inside the chain** — a non-`Proceed`
    outcome (`ShortCircuit` or `Fail`) stops subsequent middlewares for the same `HookPoint`;
    (b) **failsafe at the invocation boundary today (W0/W2.x)** — `Fail` outcomes are
    DISCARDED by `SyncOrchestrator` per `engine-hooks.v1.yaml` `outcome_consumption_status:
    design_only`, so the surrounding LLM/tool invocation does not abort. The Rule R-M.c.b
    (formerly Rule 45.b) target wires `Fail` → `Run.FAILED` and `ShortCircuit` → engine
    bypass; that escalation activates with the W2 Telemetry Vertical. `ON_ERROR` is
    `best_effort` per `engine-hooks.v1.yaml#failure_propagation.per_hook` — it always fires
    the full chain to avoid masking the original error. Reference hooks shipped in W2: PII
    filter, token counter, summariser, tool-call-limit, `LlmSpanEmitterHook`,
    `ToolSpanEmitterHook`. Direct LLM/tool calls that bypass `HookChain` are a gate-blocking
    defect (Rule 19 — deferred W2; `HookChain` SPI and `HookChainConformanceTest` do not exist
    at W0). Hooks are the sole emission path for `LlmCall` and middleware spans per §4 #56 and
    ADR-0061 §7. `@Order` tie-breaking (two hooks declaring the same `@Order` value) is
    resolved by `Class.getName()` lexicographic order — deterministic and reproducible across
    JVMs.

17. **Graph DSL conformance.** `ExecutorDefinition.GraphDefinition` MUST support beyond W2:
    (a) per-key `StateReducer` registry (`OverwriteReducer` — last-write-wins; `AppendReducer` —
    list concat; `DeepMergeReducer` — recursive map merge) applied when a node returns a partial
    state update;
    (b) typed `Edge` records replacing the flat `Map<String,String>` edges — an `Edge` may carry
    an optional predicate (`Function<RunContext, Boolean>`) for conditional routing;
    (c) JSON and Mermaid export of the compiled graph topology for debugging and documentation.
    A backward-compatible factory method (`GraphDefinition.simple(nodes, edges, startNode)`)
    retains the existing API. Implementation deferred to W3 (`graph_dsl_conformance`).

18. **Eval Harness Contract.** Every shipped capability MUST declare, by W4: (a) a golden
    corpus in `docs/eval/<capability>/corpus.jsonl` — versioned input/expected pairs;
    (b) an LLM-as-judge evaluator definition (judge model, prompt template, metric name);
    (c) a per-metric regression threshold checked in as `docs/eval/<capability>/thresholds.yaml`.
    Pre-merge gate (Rule 18, W4+): re-run corpus; any metric below its threshold blocks merge.
    Evaluation infrastructure (corpus loader, judge runner, result store) deferred to W4
    (`eval_harness_contract`).

19. **Fan-out, suspend-reason taxonomy, and suspend-deadline contract.** `SuspendSignal` MUST
    carry a sealed `SuspendReason` identifying why the run is suspended. Every reason MUST carry a
    `deadline() : Instant` at which the suspended run transitions to `EXPIRED` if not resumed.
    Sealed variants: `ChildRun(UUID childRunId, ChildFailurePolicy, Instant deadline)` |
    `AwaitChildren(List<UUID> childRunIds, JoinPolicy, ChildFailurePolicy, Instant deadline)` |
    `AwaitTimer(Instant fireAt)` | `AwaitExternal(String callbackToken, Instant deadline)` |
    `AwaitApproval(String approvalRequestId, Instant deadline)` |
    `RateLimited(String resourceKey, Instant retryAfter)`.
    `JoinPolicy: ALL | ANY | N_OF`; `ChildFailurePolicy: PROPAGATE | IGNORE | COMPENSATE`.
    W0 reference impl covers only single-`ChildRun`; remaining variants are contract-level,
    deferred to W2 (`suspend_reason_taxonomy`, `parallel_child_dispatch`, `suspend_deadline_watchdog`).
    See ADR-0019.

20. **RunStatus formal transition DFA + transition audit trail.** Legal transitions:
    `PENDING → RUNNING | CANCELLED`; `RUNNING → SUSPENDED | SUCCEEDED | FAILED | CANCELLED`;
    `SUSPENDED → RUNNING | EXPIRED | FAILED | CANCELLED`; `FAILED → RUNNING` (retry, new `attemptId`);
    `SUCCEEDED`, `CANCELLED`, `EXPIRED` are terminal. Every `Run.withStatus(newStatus)` MUST invoke
    `RunStateMachine.validate(from, to)`, throwing `IllegalStateException` on illegal transitions
    (Rule R-C.d, enforced at W0). Idempotency: `cancel` on already-cancelled run returns 200 + same row;
    `cancel` on `SUCCEEDED`/`EXPIRED` returns 409. Every transition writes a `run_state_change` audit
    row (W2); optimistic lock (`version` field) required before W2 Postgres. **Known W0 limitation**:
    in the W0 in-memory tier, `Run.withStatus()` validates the DFA but does not serialise concurrent
    HTTP cancel + orchestrator resume on the same Run; last `RunRepository.save()` wins. **Two-phase
    migration W1.5 → W2 (per ADR-0106)**: at W1.5 the `Run` record gains a `long version` field
    (default 0; no behavioural change — saves leave it untouched), so pre-W2 in-flight rows already
    carry a usable version when the W2 CAS check arms. At W2 the Postgres tier enables CAS on
    `RunRepository.save(run)` (rejected if `persisted.version != run.version() - 1`); migration is
    a no-op for in-flight Runs because the W1.5 field is already populated. Integration test
    `RunCancelDuringResumeRaceIT` arms the invariant in W2. See ADR-0020 + ADR-0106.

21. **Typed payload + PayloadCodec SPI.** Every payload crossing a JVM boundary (checkpoint bytes,
    resume payload, streaming event) MUST be encoded via a registered `PayloadCodec<T>` with stable
    `codecId` and `typeRef`. `RawPayload(Object)` is valid only within a single JVM. `EncodedPayload
    (byte[], String codecId, String typeRef)` is the persistence contract. `RunEvent` (streamed
    northbound per §4 #11) is a sealed interface: `NodeStarted | NodeCompleted | Suspended | Resumed |
    Failed | Terminal`. PII redaction hooks (§4 #16) depend on `TypedPayload<T>` to locate PII fields
    per type. All implementation deferred to W2 (Rule 22). See ADR-0022.

22. **Canonical run context propagation.** `RunContext.tenantId()` is the sole carrier of tenant
    identity inside the runtime kernel. The kernel SPI types (`RunContext`, `SuspendSignal`,
    `Checkpointer`, `Orchestrator`, `ExecutorDefinition`) live under
    `agent-bus.bus.spi.engine` per ADR-0158 (transport-agnostic EnginePort boundary; the neutral
    execution contract is owned by the Bus & State Hub plane, re-homed from the transient
    `agent-runtime.engine.orchestration.spi` co-location under ADR-0088); their runtime
    impl host (`agent-service.service.runtime`) consumes the SPI. No production class under
    `com.huawei.ascend.service.runtime..` may import any class under
    `com.huawei.ascend.service.platform..` — including (but not limited to) `TenantContextHolder`
    (HTTP-edge ThreadLocal in `agent-service.service.platform`; was rooted in `agent-platform`
    pre-ADR-0078). Enforced by `RuntimeMustNotDependOnPlatformTest`
    (ArchUnit — Rule R-C.e L1 generalisation per ADR-0055) and `TenantPropagationPurityTest`
    (ArchUnit — original narrow Rule R-C.e per ADR-0023, preserved as defence-in-depth).
    Timer-driven and async resumes source tenant
    from `Run.tenantId`. `TenantContextFilter` populates Logback MDC `tenant_id` alongside
    `TenantContextHolder` for log correlation (shipped at W0). `RunContext.tenantId() : String` migrates
    to `UUID` at W1 alongside Keycloak integration. Micrometer `tenant_id` tag enforcement and OTel
    `traceparent` propagation across suspend are deferred to W1/W2. `RunContext.traceId()` /
    `spanId()` / `sessionId()` / `traceContext()` are mandatory L1.x accessors per §4 #54
    and ADR-0062 (Trace ↔ Run ↔ Session N:M). See ADR-0023, ADR-0061.

23. **Suspension write atomicity.** At the suspension boundary, `RunRepository.save(suspended)` and
    `checkpointer.save(runId, nodeKey, payload)` MUST be observable atomically. Tiered contract:
    W0 in-memory — single-threaded, sequential on same call stack (invariant documented in
    `SyncOrchestrator.executeLoop` javadoc); W2 Postgres — both in one `@Transactional` block;
    W2 Redis Checkpointer — transactional outbox (ADR-0007); W4 Temporal — SPI bypassed entirely.
    Any W2+ orchestrator that violates this contract is a ship-blocking defect (Rule 23, deferred).
    See ADR-0024.

24. **Typed payload + PayloadCodec SPI.** *(Renumbered — formerly constraint #21 in this list.)*
    See §4 #21 above. No content change; number preserved for backward reference in older docs.

25. **Causal payload envelope and semantic ontology.** Every payload that crosses a suspend/resume
    boundary at W2+ MUST be wrapped in a `CausalPayloadEnvelope` declaring: (a) `SemanticOntology`
    tag — `FACT | PLACEHOLDER | HYPOTHESIS | REDACTED`; (b) `payloadFingerprint` — SHA-256 hex of
    encoded bytes (tamper detection on resume); (c) `byteSize` and `decayed` flag (logical decay:
    payloads exceeding 16 KiB inline cap are replaced with a `PayloadStoreRef`). Consumers MUST
    inspect the `SemanticOntology` tag before passing content to LLM context: `PLACEHOLDER` data
    MUST NOT be interpreted as a verified fact. The PII filter hook (§4 #16) exempts `PLACEHOLDER`
    and `REDACTED` payloads from further field-level redaction. Implementation deferred to W2.
    See ADR-0028, `causal_payload_envelope`, `semantic_ontology_tags`, `payload_fingerprint_precommit`.

26. **Cognition-Action separation.** Cognitive processes (LLM-driven reasoning, plan synthesis,
    hallucination tolerance) are isolated from action processes (database writes, tool invocations,
    RLS-bound transactions, idempotent outbox events) by the orchestration SPI boundary. Cognitive
    processes observe and produce *intent*; action processes execute *verified intent* with full
    determinism and auditability. Neither layer may bypass the SPI to reach the other directly.
    Language policy: the cognitive layer MAY be implemented in any language that can call the
    `Orchestrator` SPI. W0-W2 default: Spring AI Java (ChatClient). W3 optional: GraalVM in-process
    polyglot (ADR-0018), MCP Java SDK remote tool server. No language is mandatory.
    `CapabilityRegistry` entries carry a `SkillKind` discriminator (`JAVA_NATIVE | MCP_TOOL |
    SANDBOXED_CODE_INTERPRETER`) defining the dispatch path. See ADR-0029, `cognition_action_separation`.

27. **Skill SPI: lifecycle, ResourceMatrix, posture-mandatory sandbox.** Every external capability
    MUST be registered via the `Skill` SPI with: (a) lifecycle methods `init / execute / suspend /
    teardown` — `teardown` is called unconditionally even when `execute` throws; (b)
    `SkillResourceMatrix` declaring `(tenantQuotaKey, globalCapacityKey, tokenBudget, wallClockMs,
    cpuMillis, maxMemoryBytes, concurrencyCap)` — the Orchestrator validates declared limits before
    `init()` AND enforces the subset supported by the dispatch path (see ADR-0038 §4 tiers); (c)
    `SkillTrustTier (VETTED | UNTRUSTED)` — in research/prod posture, `UNTRUSTED`
    skills MUST route through a non-`NoOp` `SandboxExecutor` (ADR-0018); startup gate asserts
    (Rule 27, deferred W3). Every `execute()` returns a `SkillCostReceipt` for Rule 13 (P1). When
    a Run is SUSPENDED, `Skill.suspend()` releases heavy resources; `Skill.resume(token)` reconnects
    before the next `execute()`. Implementation deferred to W2 (SPI) + W3 (mandatory sandbox).
    See ADR-0030, `skill_spi_lifecycle`, `skill_resource_matrix`, `untrusted_skill_sandbox_mandatory`.

28. **Three-track channel isolation.** The W2 northbound streaming surface (§4 #11) is physically
    split into three tracks: (1) **Control** — `RunControlSink.push(RunControlCommand)`: out-of-band
    cancel/priority-suspend commands delivered before the next executor iteration boundary; (2)
    **Data** — `Flux<RunEvent>`: typed progress events with caller-controlled demand and bounded
    buffer (default 64 events, DROP_OLDEST overflow — Terminal events never dropped); (3)
    **Heartbeat** — `Flux<Instant>`: liveness cadence on a dedicated scheduler independent of data
    channel load, cadence `≤ 30 s`. `CapabilityRegistry.resolve(name, runContext)` is tenant-scoped:
    lookups for capabilities not authorised for the requesting tenant are rejected. A `RunDispatcher`
    SPI separates intent-enqueue from intent-execute for async dispatch at W2. Implementation
    deferred to W2. See ADR-0031, `three_track_channel_isolation`, `run_dispatcher_spi`.

29. **Scope-based run hierarchy + planner contract.** `Run` carries a `RunScope` discriminator
    (`STEP_LOCAL | SWARM`): `STEP_LOCAL` runs are orchestrator-local, directly addressable by
    `parentRunId` chain; `SWARM` runs are federated across multiple orchestrators and visible only
    via `AgentRegistry`. `SuspendReason.SwarmDelegation` variant covers delegation to a SWARM child.
    Minimal planner contract: `PlanState` (current plan status) and `RunPlanRef` (reference from a
    Run row to its associated plan artifact) are the design-level types; full `RunPlanSheet` toolset
    deferred to W4. `RunRepository.findRootRuns(tenantId)` (shipped W0) returns top-level runs with
    `parentRunId == null`. `RunScope` Java field on the `Run` entity deferred to W2. See ADR-0032.

30. **Logical identity equivalence + deployment-locus vocabulary.** The platform recognizes three
    deployment loci: `S-Cloud` (server-side, cloud-hosted), `S-Edge` (server-side, edge-deployed
    at network boundary), `C-Device` (client-resident, on-device). A capability designated for
    `S-Cloud` MUST remain functionally equivalent when deployed at `S-Edge` (same SPI, same
    security controls, same tenant isolation — only the execution venue differs). The existing
    Rule 17 vocabulary `S-side / C-side` is **preserved unchanged** — it expresses substitution
    authority (who may substitute means vs ends), not deployment location. No `edge` posture
    variant is introduced; the three-posture model (`dev/research/prod`) is sufficient. Locus
    scheduling is post-W4. See ADR-0033, `logical_identity_equivalence`.

31. **Memory and knowledge taxonomy.** The platform recognizes six memory categories:
    M1 Short-Term Run Context (in-process per run, TTL = run lifetime);
    M2 Episodic Session Memory (across turns in a session, tenant-scoped);
    M3 Semantic Long-Term (persistent embeddings, tenant-scoped);
    M4 Graph Relationship Memory (graph nodes/edges, tenant-scoped);
    M5 Knowledge Index (indexable documents/chunks, tenant-scoped);
    M6 Retrieved Context (ephemeral RAG results, TTL = turn lifetime).
    All persistent memory entries carry a common `MemoryMetadata` schema:
    `{tenantId, runId?, sessionId?, source, ontologyTag, confidence, retentionExpiry,
    embeddingModelVersion?, redactionState, visibilityScope}`.
    W1 reference sidecar: Graphiti (graph relationship memory, M4). mem0 and Cognee are not
    selected. Code-level implementation deferred to W2. See ADR-0034, `memory_knowledge_taxonomy`.

32. **Posture enforcement single-construction-path.** All posture reads MUST flow through
    `AppPostureGate.requireDevForInMemoryComponent(componentName)`. No class other than
    `AppPostureGate` may call `System.getenv("APP_POSTURE")` (Rule D-8 single-construction-path).
    `dev` or missing: emits WARN to stderr and continues; `research`/`prod`: throws
    `IllegalStateException` with ADR-0035 reference. Gate Rule 12 enforces the literal
    `AppPostureGate.requireDev` in `SyncOrchestrator`, `InMemoryRunRegistry`, and
    `InMemoryCheckpointer`. `docs/cross-cutting/posture-model.md` is the canonical posture-truth
    ledger; every posture-aware component row MUST appear there. See ADR-0035,
    `posture_single_construction_path`.

33. **Contract-surface truth (generalized Rule G-2 sub-clause .a).** Beyond the four original Rule G-2 sub-clause .a cases,
    two additional truth constraints are gate-enforced: Gate Rule 13 — `contract-catalog.md` MUST
    NOT reference any deleted SPI interface name or deleted starter coordinate (deleted-name list
    sourced from `architecture-status.yaml` `sdk_spi_starters` note); Gate Rule 14 — every method
    name appearing in a code-fence block in `agent-service/ARCHITECTURE.md` or
    `agent-service/ARCHITECTURE.md` MUST exist in the named Java class (pragmatic regex sweep).
    See ADR-0036, `contract_surface_truth_generalization`.

34. **Wave authority consolidation.** A single chain of authority governs wave-planning decisions:
    (1) `ARCHITECTURE.md` §1 + §4 — wave boundary constraints; (2)
    `docs/governance/architecture-status.yaml` — per-capability shipped/deferred status;
    (3) `deferred_sub_clauses:` blocks in each alphanumeric rule card under
    `docs/governance/rules/` — deferred engineering rules with re-introduction triggers
    (the prior `docs/CLAUDE-deferred.md` monolith was retired 2026-05-28 in favour of
    per-card frontmatter; legacy rules awaiting human review live in
    `docs/governance/escalations.md`). All other planning documents are informational
    or archived. Stale parallel plans (`roadmap-W0-W4.md`, `engineering-plan-W0-W4.md`)
    are archived in `docs/archive/2026-05-13-plans-archived/`. See ADR-0037,
    `wave_authority_consolidation`.

35. **Skill SPI resource-tier classification.** `SkillResourceMatrix` fields are grouped into four
    enforceability tiers: (a) **Hard-enforceable** — quota key, token budget, wall-clock timeout,
    concurrency cap, trust tier, sandbox requirement for UNTRUSTED; Orchestrator checks these before
    `init()` and blocks or routes through sandbox; (b) **Sandbox-enforceable** — CPU millis and
    max-memory-bytes; enforced only when the dispatch path routes through a non-NoOp
    `SandboxExecutor`; (c) **Advisory/receipt** — observed CPU time, memory, and wall-clock logged
    as `SkillCostReceipt`; no enforcement, only cost attribution; (d) **Skill-specific hints** —
    freeform metadata passed through to the skill implementation. Claims about CPU/memory enforcement
    in documentation MUST qualify which tier they target. See ADR-0038, `skill_spi_resource_tiers`.

36. **Payload migration adapter strategy.** There is one normative migration path for payload types:
    raw `Object` → `Payload` (typed, ADR-0022) → `CausalPayloadEnvelope` (causally annotated,
    ADR-0028). Any `NodeFunction` or `Reasoner` implementation using raw `Object` parameters MUST
    be wrapped with `PayloadAdapter.wrap(Object)` before being passed to a typed boundary. A
    `@Deprecated` annotation window is mandatory on any method with raw `Object` payload before
    removal; removal without an adapter wrapper is a ship-blocking defect. See ADR-0039,
    `payload_migration_adapter`.

37. **W1 HTTP contract reconciliation.** W1 tenant identity: `X-Tenant-Id` header stays required;
    W1 adds JWT `tenant_id` claim cross-check against the header value (403 on mismatch). The
    initial run status is `PENDING` (matching `RunStatus` enum and RunStateMachine DFA). Cancellation
    is a state transition expressed as `POST /v1/runs/{id}/cancel` (not `DELETE`); run records survive
    cancellation as terminal records. Gate Rule 16 enforces these three points across the five active
    HTTP contract documents. Gate Rule 16a (the tenant-model check) uses case-sensitive matching and
    catches both the original three literal phrasings and replacement-implying verb forms applied to
    `TenantContextFilter` (i.e., verb-to-JWT constructs that imply the header is discarded rather
    than cross-checked). Full forbidden-phrasing list: see ADR-0040, `w1_http_contract_reconciliation`.

38. **Active-corpus truth sweep.** No active document outside `docs/archive/` and `docs/logs/reviews/`
    may reference the two deleted plan paths (the engineering plan and the roadmap archived under
    `docs/archive/2026-05-13-plans-archived/` per ADR-0037). Wave-planning information from those
    files lives exclusively in the single wave authority (§4 #34). ADR references are repointed to
    the archived copies. The companion systems-engineering plan is archived alongside its peers.
    Gate Rule 15 enforces the deleted-path freeze. See ADR-0041, `active_corpus_truth_sweep`.

39. **Test-evidence enforcement for shipped capabilities.** Every `shipped: true` row in
    `docs/governance/architecture-status.yaml` MUST have a non-empty `tests:` list pointing
    to a real test class or test script. Gate Rule 19 (`shipped_row_tests_evidence`) fails any
    `shipped: true` row with `tests: []` or absent `tests:`. See ADR-0042,
    `shipped_row_tests_evidence`.

40. **Peripheral entry-point drift prevention.** The ACTIVE_NORMATIVE_DOCS corpus is the
    canonical domain for contract-truth claims. Every peripheral entry-point in that corpus
    (module POM descriptions, module READMEs, BoM implementation-path cells, contract-catalog
    tables) must agree with the central truth established by the architecture, ADR, and Java
    source layers. Gate Rules 18 (widened), 20, 21, 22, and 23 enforce this at commit time.
    See ADR-0043, `active_normative_doc_catalog`.

41. **SPI catalog precision matches Java source.** The `contract-catalog.md` SPI table MUST
    match each SPI's actual Java signature at W0: `RunContext` is classified as `interface` (not
    `record`); scope invariants are per-SPI (tenant-scoped, run-scoped, or operation-scoped);
    `embeddingModelVersion` is the canonical field name per ADR-0034. Gate Rule 17 is extended
    to verify `RunContext` is labeled "interface" in the catalog. See ADR-0044,
    `spi_contract_precision_and_memory_metadata_reconciliation`.

42. **Shipped-row evidence paths must resolve on disk.** Every `l2_documents:` entry and
    `latest_delivery_file:` value on a `shipped: true` capability row in
    `docs/governance/architecture-status.yaml` MUST point to a file that exists on disk.
    Syntactically valid references to non-existent artifacts are a REF-DRIFT class defect.
    Gate Rule R-J.b (`shipped_row_evidence_paths_exist`) enforces this at commit time. Note:
    `implementation:` paths are covered by Gate Rule 7; `tests:` paths are covered by Gate Rule 19.
    See ADR-0045, `shipped_row_evidence_paths_exist`.

43. **Entry-point prose must carry wave qualifiers for future-wave impl claims.** SPI Javadoc
    in `agent-service/src/main/java` and active normative markdown docs must not name a
    future-wave implementation or sidecar adapter without a wave qualifier (W0/W1/W2/W3/W4) or
    an ADR reference in the same text block. Patterns "Primary sidecar impl:" and
    "Sidecar adapter —" without a wave qualifier are gate-failing (ADR-0045). Gate Rule G-2 sub-clause .a
    (`peripheral_wave_qualifier`) enforces this at commit time. Closes the PERIPHERAL-DRIFT
    class defect at the gate level. See ADR-0045, `peripheral_wave_qualifier`.

44. **Release-note shipped-surface truth.** Every shipped row in `docs/logs/releases/*.md` MUST
    reference real Java symbols and real test classes. Group labels (e.g. "Orchestration SPI")
    must match the actual Java surface, or carry an explicit `W1`/`W2`/`W3`/`W4` qualifier or
    `design-only` / `deferred` / `not shipped` / `remains design` marker for future-wave names.
    Method lists on shipped SPIs must be a subset of the canonical interface (e.g. `RunContext`
    is `runId`/`tenantId`/`checkpointer`/`suspendForChild` — `posture()` is forbidden because
    it does not exist on the interface). Test attributions must name the test that actually
    performs the asserted check (`OpenApiContractIT` for OpenAPI snapshot diff; `ApiCompatibilityTest`
    is ArchUnit-only). Module placement and component-breadth claims must match the code's call
    sites (`AppPostureGate` belongs under Runtime Kernel, not HTTP Edge; only `SyncOrchestrator`,
    `InMemoryRunRegistry`, `InMemoryCheckpointer` call it — not "all runtime components"). Gate
    Rule 26 (`release_note_shipped_surface_truth`) enforces this with four sub-checks (26a name
    guard, 26b method-list guard, 26c test attribution, 26d scope guard). Closes the
    GATE-SCOPE-GAP class defect for the release-artifact class. See ADR-0046.

45. **Active-entrypoint truth and system-boundary prose convention.** Two
    sub-constraints, both enforced under ADR-0047:
    (a) **Baseline cross-check.** The root `README.md` MUST contain the four architecture
        baseline counts (§4 constraints, ADRs, gate rules, gate self-tests) currently
        asserted by `docs/governance/architecture-status.yaml.architecture_sync_gate.allowed_claim`.
        If the canonical baseline advances, the README MUST advance with it; the gate rejects
        stale-count drift before commit.
    (b) **Target-vs-W0 prose split.** The system-boundary section (`§1`) of the root and
        module `ARCHITECTURE.md` files MUST explicitly separate target architecture (W1–W4)
        from the W0 shipped subset. Present-tense prose describing future-wave capabilities
        as if they are running today is forbidden in active entrypoint docs; either qualify
        with a wave marker or move the sentence under a target-architecture heading.
    Gate Rule 27 (`active_entrypoint_baseline_truth`) enforces sub-constraint (a)
    mechanically. Sub-constraint (b) is enforced by review and by the §1 structure itself.
    See ADR-0047.

46. **Service-Layer Microservice-Architecture Commitment.** The Service Layer
    (HTTP edge in `agent-service.service.platform` + cognitive runtime in
    `agent-service.service.runtime` + engine + orchestration SPI in
    `agent-runtime` + cross-plane control surfaces in `agent-bus`,
    post-ADR-0088 rc13; the `agent-runtime-core` kernel-shim module was
    dissolved per ADR-0088 and its types were relocated to semantic-home
    modules — see ADR-0088 for the historical narration) is deployed and
    scaled as **long-running microservices** — long-lived JVM processes, multiple replicas,
    horizontal scaling. Multiple Agent Service instances coordinate via the **Agent Bus**
    (cross-docker, cross-service); the bus is platform-owned, not middleware. **Agent Bus
    traffic split (locked at ADR-0048; substrate choice deferred to expanded ADR-0031;
    heartbeats moved to Rhythm track per ADR-0050 amendment):** data flow is **P2P**
    between Agent Service instances (heavy payloads such as LLM context, tool results,
    scraped documents flow point-to-point — gRPC streaming over mTLS or equivalent — and
    never traverse the central broker); control flow is on a **centralized event bus**
    (PAUSE/KILL/RESUME/UPDATE_CONFIG commands, scheduling decisions, capability bidding —
    Kafka / NATS JetStream / Redpanda choice deferred); heartbeats / WAKEUP pulses /
    sleep declarations live on **Track 3 (Rhythm)** per §4 #48. Collapsing data and
    control onto one broker is forbidden because it re-introduces the network-congestion
    failure mode the whitepaper §5.2 warns about. The SPI primitives (`SuspendSignal`,
    `Checkpointer`, `RunRepository`, `RunStateMachine` DFA, ADR-0024 suspension atomicity)
    stay serverless-friendly so W4+ migration to per-Run hydration as the deployment
    model remains open; the deployment commitment is at the service-layer level, not the
    SPI level. **Microservice-trap mitigation (whitepaper §1.3):** this is microservice
    for the *Service Layer* (the platform itself), NOT for individual agents. Agents
    within an Agent Service instance are in-process; cross-instance coordination uses
    the Agent Bus with the data-P2P / control-event-bus / rhythm-independent split.
    Inter-agent calls are intent-routed through the bus, never directly endpoint-addressed.
    The archived five-tier topology analysis (serverless direction) is at
    `docs/archive/2026-05-13-serverless-architecture-future-direction.md`. See ADR-0048.

47. **C/S Dynamic Hydration Protocol and Degradation Authority Red Line.** The whitepaper's
    central agent contract is C/S separation: the **C-Side** (business application) holds
    the lightweight `TaskCursor` + `BusinessRuleSubset` + `SkillPoolLimit`; the **S-Side**
    (platform runtime) hydrates the request into a `HydratedRunContext` and returns one
    of three handoff modes — `SyncStateResponse` (cursor advancement), `SubStreamFrame`
    (pass-through UI stream), or `YieldResponse` (permission suspension; composes with
    sealed `SuspendReason` per §4 #19). C-Side resume occurs via `ResumeEnvelope`.
    `RunContext` is the **internal S-Side execution context** — NOT the C/S protocol.
    `SuspendSignal` is one possible internal cause of `YieldResponse`. `Checkpointer`
    stores S-Side trajectory state, NEVER business facts. `RunRepository` stores platform
    lifecycle and accounting state, NEVER business ontology. **Degradation authority red
    line** (whitepaper §4.2): the S-Side MAY perform `ComputeCompensation` (substitute
    tools/models/routes) while preserving the C-Side task goal; the S-Side MUST issue a
    `BusinessDegradationRequest` (yield with reason code + options) when same-quality
    completion is impossible — only the C-Side can accept a degraded business outcome.
    `GoalMutationProhibition` forbids the S-Side from reinterpreting/narrowing/broadening/
    replacing the C-Side task goal under any resilience strategy. Java types and wire
    bindings deferred to W2+; protocol named at L0 contract level. See ADR-0049.

48. **Workflow Intermediary + Three-Track Cross-Service Bus (with Rhythm restored).** The
    Agent Bus is a **workflow intermediary hub**, not a work-dispatch broker. Every
    Agent Service instance hosts a local `WorkflowIntermediary` (per ADR-0050) that owns
    mailbox polling, local admission control, lease checks, and dispatch into in-process
    workers. **The bus MUST NOT force-start computation inside an Agent Service instance.**
    Admission decisions (`Accepted | Delayed | Rejected | Yielded`) are local; backpressure
    propagates via `BackpressureSignal`. Cross-service traffic is split into three physical
    tracks: **Track 1 — Control** (centralized event bus; PAUSE/KILL/RESUME/UPDATE_CONFIG/
    scheduling/cancellation), **Track 2 — Data/Compute** (P2P between instances; heavy
    payloads; pointer-based; NEVER on broker), and **Track 3 — Heartbeat/Rhythm**
    (independently protected from Track 1 congestion; carries heartbeats, `SleepDeclaration`,
    `WakeupPulse`, `TickEngine` ticks, lease renewal, `ChronosHydration` triggers). The
    Rhythm track is the cross-service restoration of the whitepaper §5.2 three-track model;
    ADR-0048's prior heartbeat placement on the control bus is amended. `ChronosHydration`
    end-to-end flow (whitepaper §5.4): sleep declaration → snapshot durable → compute
    self-destruct → `TickEngine` evaluates condition → `WakeupPulse` on Rhythm track →
    local intermediary rehydrates. Wire formats / substrate selection deferred W2+ (expanded
    ADR-0031). See ADR-0050.

49. **Memory and Knowledge Ownership Boundary.** Memory is partitioned by ownership, not
    only by category. **C-Side owned** (by default): business ontology, business entity
    state, user preferences, domain facts discovered during agent execution, business
    knowledge graph / business DB. **S-Side owned**: run trajectory, token usage, model
    version + gateway telemetry, tool-call trace, retry/failure diagnostics, execution
    snapshots for resume, platform scheduling/quota/billing. **Shared or delegated memory**:
    only via explicit `DelegationGrant` declaring `(tenantScope, retention, redactionState,
    visibilityScope, exportDeleteSemantics, placeholderPolicy)`. The S-Side emits
    `BusinessFactEvent` / `OntologyUpdateCandidate` (with `proposalSemantics ∈ {HYPOTHESIS,
    OBSERVATION, INFERENCE}`) on the Data track for the C-Side to consume; S-Side MUST
    NOT directly write to C-Side knowledge graph. **`PlaceholderPreservationPolicy`
    (first-class, ship-blocking)**: when C-Side passes placeholders (e.g. `[USER_ID_102]`),
    S-Side MUST preserve them verbatim through every LLM prompt, tool call, intermediate
    result, and final return; the LLM MUST NOT be asked to resolve placeholder identity
    unless an explicit `DelegationGrant` authorises resolution at that scope. Results
    return via `SymbolicReturnEnvelope` with placeholders unchanged. Under ADR-0034's
    M1–M6 taxonomy: M3 / M4 / M5 are **split** into platform-derived operational memory
    (S-Side) vs business-owned ontology (C-Side default). `GraphMemoryRepository` is the
    platform SPI for M4 and is **NOT** the default owner of customer business ontology.
    Java types and `DelegationGrant` template deferred W2+. See ADR-0051.

50. **Skill Topology Scheduler and Capability Bidding.** Above the Java Skill SPI
    (ADR-0030 lifecycle, ADR-0038 resource tiers), the platform defines a **distributed
    scheduling layer** (ADR-0052). **Two-axis arbitration** (whitepaper §4.1):
    horizontal = **Tenant Quota** (per-tenant caps), vertical = **Global Skill Capacity**
    (cluster-wide caps per Skill). A Run that hits the vertical-axis cap yields only the
    **dependent agent step** via `SuspendSignal` with `SuspendReason.RateLimited`
    (`SkillSaturationYield`), releasing the LLM inference thread rather than starving
    other Runs. **`CapabilityRegistry`** (extended) — capability tags bound to domain
    permission identifiers; tenant-scoped pre-authorization; rejects with
    `Rejected(INSUFFICIENT_PERMISSION)` if the requesting tenant lacks the required
    identifier. **Capability bidding** (whitepaper §5.3): only pre-authorized delegates
    see `BidRequest`; non-authorized bidders are silently dropped at the Registry. Bidders
    respond with `BidResponse(capacityAvailable, expectedStartTime, requiredSubstitutions[],
    confidence, costEstimate)`. **`PermissionEnvelope`** — short-lived, signed,
    subsumption-bounded; the S-Side issues per-task action/tool permissions to the
    winning delegate, propagated only within the declared subsumption boundary; revokes
    on yield. Java types and bidding-protocol wire format deferred W2-W3. See ADR-0052.

51. **Cohesive Agent Swarm Execution.** Agent-spawned child work MUST remain under the same
    workflow authority by default. A parent `Run` may spawn child `Run`s (aliased
    `SwarmRun` when `RunScope.SWARM`), delegated tasks, or subprocess-like work only through
    a **`SpawnEnvelope`** that preserves the 15 lifecycle dimensions enumerated in ADR-0053
    (parent ref, tenant, permission scope, budget, cancellation policy, deadline, trace
    correlation, attempt/retry, posture, session, business-rule subset, placeholder policy,
    memory ownership scope, idempotency context, observability tags). `SpawnEnvelope` is
    named at L0 contract level; the Java type is deferred to W2. **`SwarmJoinPolicy`** is
    the L0 contract alias for `JoinPolicy: ALL | ANY | N_OF` (ADR-0019). **Cross-workflow
    execution** — child work that genuinely belongs to a different workflow authority — MUST
    use an explicit **`CrossWorkflowHandoff`** contract that produces a new lifecycle
    boundary, a fresh resume contract, explicit ownership transfer, and audit-grade
    attestation; cross-workflow execution MUST NOT be implicit. Five named authority-transfer
    boundaries exist (HTTP→Runtime via `TenantContextFilter`; C-Side→S-Side via
    `HydrationRequest`/`ResumeEnvelope`; Parent→Child Run via `SpawnEnvelope`; Run→Skill via
    `PermissionEnvelope`; Cross-Workflow via `CrossWorkflowHandoff`); each boundary has a
    named carrier and implicit transfer is forbidden. Per-dimension implementation status
    is tracked in `docs/governance/architecture-status.yaml`. **Child-tenant equality
    invariant (W2 — when SpawnEnvelope Java type ships)**: every `SpawnEnvelope` MUST set
    `child.tenantId == parent.tenantId`; cross-tenant delegation is forbidden inside the same
    workflow authority and MUST flow through `CrossWorkflowHandoff` with an explicit
    authority-transfer attestation. **Parent-chain acyclicity (W2 — same Java-type trigger;
    federation reconstruction per ADR-0107)**: every `SpawnEnvelope` carries an
    `ancestor_run_ids` list (max-depth 8, parent-propagated) and the orchestrator MUST reject
    any spawn whose requested child run-id appears in `ancestor_run_ids` — closes the
    same-instance cycle case. Cross-instance federation MUST NOT trust the caller-supplied
    list; instead the receiving Agent Service instance reconstructs the ancestor chain by
    querying a central `RunRegistry` keyed by `parentRunId` (per ADR-0107). Caller-supplied
    `ancestor_run_ids` is treated as advisory at federation boundaries; the trusted chain is
    server-side state. **Error code + detection point + audit shape (R2-NEW-secondary-#2)**:
    invariant violations raise a named `OrchestratorReject(reason)` where `reason ∈
    {child_tenant_mismatch, ancestor_chain_overflow, ancestor_cycle_detected,
    cross_instance_chain_disagreement}`; detection point is the `SpawnEnvelope` builder
    (`child_tenant_mismatch`, `ancestor_chain_overflow`, `ancestor_cycle_detected`) and the
    federation receiving-orchestrator (`cross_instance_chain_disagreement`); reject emits a
    structured `WARN+` audit log carrying MDC fields `(parentRunId, requestedChildRunId,
    ancestor_run_ids_advisory, ancestor_run_ids_trusted, reason, actor, occurredAt)` —
    mirrors Rule R-J sub-clause .b cancel-mismatch audit shape. See ADR-0053 + ADR-0107.

52. **Long-Connection Containment.** Long-running agent calls MUST be admitted through a
    bounded runtime-resource model. The architecture MUST NOT assume one logical call equals
    one blocking thread, one dedicated socket, or one permanently retained physical
    connection. Logical calls are represented by runtime handles (**`LogicalCallHandle`** ≡
    `Run` + `SuspendSignal`, §4 #9, ADR-0019) that can be suspended, resumed, streamed,
    cancelled, and accounted for independently from the physical connection used at any
    moment. **`ConnectionLease`** is the L0 alias for the bounded transport-resource claim
    backed by three-track channel isolation (§4 #28, ADR-0031) and the data-P2P /
    control-event-bus split (§4 #46, ADR-0048). Admission is enforced via
    **`AdmissionDecision`** (`Accepted | Delayed | Rejected | Yielded` — ADR-0050;
    reviewer-named `LongCallAdmissionPolicy` is the same contract). Resource pressure is
    signaled via **`BackpressureSignal`** (`LOCAL_SATURATION | SKILL_SATURATION |
    TENANT_QUOTA_EXCEEDED | SHUTDOWN` — ADR-0050; reviewer-named `ConnectionPressureSignal`
    is the same contract). Idle waits MUST follow **`SuspendInsteadOfHold`**: a long wait
    becomes a suspended workflow state when useful compute is not happening, implemented
    at W0 via `SuspendReason.RateLimited` (ADR-0019) and `SuspendReason.AwaitTimer`. Three
    resource-explosion vectors remain W1+ deferred (per-tenant socket cap, file-descriptor
    bound, in-flight Runs pool cap); these are tracked in
    `docs/governance/architecture-status.yaml`. Concrete transport mechanics (Netty, epoll,
    channel pools, event-loop schedulers) are W2+ implementation guidance and MUST NOT
    appear as L0 contract. See ADR-0054.

53. **Telemetry Vertical first-class.** The Telemetry Vertical (Trace + Span + LlmCall) is a named cross-cutting concept declared in `ARCHITECTURE.md §0.5.3`. Every horizontal layer (HTTP edge, orchestration, executor, adapter, MCP) MUST emit into it via the `TraceContext` SPI or the Hook SPI — never directly. Direct telemetry emission from adapter code (LlmGateway, ToolInvoker, DB/Redis bridges) is forbidden. Enforced by ArchUnit `TelemetryVerticalArchTest` (no class outside `agent-service/src/main/java/com/huawei/ascend/service/runtime/observability` or `agent-service/src/main/java/com/huawei/ascend/service/platform/observability` may write to a `TraceWriter`-shaped sink — paths reflect the post-ADR-0078 sub-package layout; was rooted in `agent-runtime/observability` / `agent-platform/observability` pre-Phase-C). See ADR-0061.

54. **Trace ↔ Run ↔ Session identity (N:M).** Every persisted `Run` row MUST carry a non-null `trace_id` (32-char lowercase W3C hex; the column is nullable at L1.x and NOT NULL from W2 via `V2__run_trace_id_notnull.sql`). `Run.sessionId` MAY be null at L1.x; in posture=research/prod from W2 it MUST be non-null. Multiple Runs MAY share a Trace or a Session. `RunContext` MUST expose `traceId()`, `spanId()`, `sessionId()`, and `traceContext()` alongside `tenantId()`. Child Runs spawned via `SuspendForChild` inherit `sessionId` from the parent and start a new Trace whose root span attribute `parent_trace_id` points to the parent's `traceId` (ADR-0062 default policy). Enforced by ArchUnit `RunContextIdentityAccessorsTest` + integration `RunTraceSessionConsistencyIT` + (W2) Flyway schema constraint. See ADR-0062.

55. **W3C traceparent propagation at HTTP edge.** `agent-service.service.platform` (HTTP edge sub-package, formerly the standalone `agent-platform` module pre-ADR-0078) MUST extract or originate a W3C version-00 `traceparent` on every inbound request (filter order 10, before JWT/Tenant/Idempotency), populate Logback MDC with `trace_id` + `span_id` alongside `tenant_id` + `run_id`, and emit `traceresponse: 00-<trace_id>-<server_span_id>-01` on every outbound response (200/4xx/5xx) so client SDKs can correlate. Invalid `traceparent` headers MUST fall back to originating a fresh trace (never propagate an unparseable id) and increment `springai_ascend_traceparent_invalid_total`. Enforced by `TraceExtractFilterIT` + extended `LogFieldShapeIT`. See ADR-0061 §4.

56. **GENERATION span schema.** Every LLM invocation in posture=research/prod MUST emit a Span carrying attributes `gen_ai.system`, `gen_ai.request.model`, `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`, `langfuse.cost_usd`, and `langfuse.latency_ms`. Raw prompt/completion content MUST be stored in `PayloadStore` and referenced via `payload_ref://<id>` — never inline as a span attribute (negative invariant; see §4 #58). Direct LLM calls bypassing `HookChain` are a ship-blocking defect under Rule D-5 (observability category). Enforced by ArchUnit `LlmGatewayHookChainOnlyTest` (no `service.runtime.llm.*` class may import `org.springframework.ai.chat.ChatModel` outside the HookChain package; Wave C1 Spring AI shells remain design-only until W2 hook binding ships) + integration `GenerationSpanSchemaIT` (W2 trigger; class FQN locked here per Rule R-C.a contract-then-enforcer pair). See ADR-0061 §1 + ADR-0061 §7.

57. **Tenant attribute on every span.** Every Span emitted by the platform MUST carry `tenant.id` matching `RunContext.tenantId()`. MCP trace replay (`get_run_trace`, `list_runs`, `list_sessions`, `get_llm_call`) MUST fail closed on tenant mismatch — the caller's tenant (resolved from JWT) MUST match `trace.tenant_id`, returning 403 otherwise. Reconciliation with `TenantTagMeterFilter` (L1): the filter strips `tenant_id` from raw meter tags (high-cardinality protection); the span attribute is unaffected because span storage is sampled (1-10 %) and span attributes are not aggregation dimensions. Enforced by ArchUnit `SpanTenantAttributeRequiredTest` + (W2) `McpTraceLookupTenantIsolationIT`. See ADR-0061 §5–§6.

58. **No PII in span attributes.** Raw prompt, completion, tool-input, and tool-output content MUST NOT appear in Span attributes in posture=research/prod. Payloads MUST be stored in `PayloadStore` and referenced via `payload_ref://<id>`. `PiiRedactionHook` MUST be registered at boot in posture=research/prod (verified by `AppPostureGate`); startup MUST fail closed if the hook is absent. Enforced by integration `PostureBootPiiHookPresenceContractIT` (L1.x — asserts the boot-gate contract; the negative emission test `PiiSpanAttributeIT` lands at W2 alongside Hook SPI implementation; class FQN locked here per Rule R-C.a). See ADR-0061 §5.

59. **MCP-only telemetry replay surface.** Trace replay and run/session listing MUST be exposed exclusively via MCP tools (`get_run_trace`, `list_runs`, `get_llm_call`, `list_sessions`). No HTTP endpoint, no UI, no direct DB read endpoint, no Tempo/Jaeger redirect proxy. Preserves §1 exclusion (no Admin UI). Enforced by ArchUnit `McpReplaySurfaceArchTest` (negative: no `@RestController` resides under `com.huawei.ascend.service.platform.web.replay`, `…web.trace`, or `…web.session` in `agent-service/src/main/java/…`; consolidated post-ADR-0078 from the pre-Phase-C `agent-platform/web/...` paths) + ADR-0017 freeze.

60. **Business/Platform decoupling enforcement.** Platform code MUST NOT contain business-specific customizations. Business and example modules MUST extend the platform via SPI (`com.huawei.ascend..spi..`) and `@ConfigurationProperties` only — never by patching `*.impl.*` or `com.huawei.ascend.service.platform..`. The platform MUST ship a runnable quickstart at `docs/quickstart.md` referenced from `README.md` so developers reach first-agent execution without platform-team intervention. Enforced by ArchUnit `SpiPurityGeneralizedArchTest` (any `..spi..` package free of Spring/platform/inmemory/Micrometer/OTel deps) + Gate Rule R-C.b `quickstart_present`. CLAUDE.md Rule R-A. See ADR-0064.

61. **Competitive baselines (Four Pillars).** Every release MUST publish `docs/governance/competitive-baselines.yaml` declaring four pillar dimensions — `performance`, `cost`, `developer_onboarding`, `governance` — each with a named `baseline_metric` and a `current_value` (or `N/A` for not-yet-instrumented). The most recent `docs/logs/releases/*.md` release note MUST mention all four pillar names. A regression in any `current_value` MUST carry a `regression_adr:` reference (enforcer for the regression-ADR pairing is deferred per Rule R-B `deferred_sub_clauses` block in `docs/governance/rules/rule-R-B.md`). Enforced by Gate Rule R-D sub-clause .a `competitive_baselines_present_and_wellformed` + Gate Rule G-1 sub-clause .a `release_note_references_four_pillars`. CLAUDE.md Rule R-B. See ADR-0065.

62. **Independent module evolution.** Every reactor module under `<module>/pom.xml` MUST own a sibling `<module>/module-metadata.yaml` declaring `module`, `kind ∈ {platform | domain | starter | bom | sample}`, `version`, and `semver_compatibility`. Each module MUST build and test in isolation via `mvn -pl <module> -am test`. Inter-module dependency direction is governed by §4 #1 / §4 #10. Enforced by Gate Rule G-1 sub-clause .b `module_metadata_present_and_complete` + existing Gate Rule D-6 `module_dep_direction`. CLAUDE.md Rule R-C.b. See ADR-0066.

63. **SPI + DFX + TCK co-design.** Every module with `kind: domain` in `module-metadata.yaml` MUST expose at least one `*.spi.*` package containing ≥ 1 public interface, listed under `spi_packages:` in the metadata file. Every module with `kind: platform` or `kind: domain` MUST publish a `docs/dfx/<module>.yaml` covering five DFX dimensions — `releasability`, `resilience`, `availability`, `vulnerability`, `observability` — each with a non-empty body. The sibling `<module>-tck` reactor module and conformance suite are deferred per Rule R-D `deferred_sub_clauses` block in `docs/governance/rules/rule-R-D.md` (W2 trigger). Enforced by Gate Rule R-E `dfx_yaml_present_and_wellformed` + Gate Rule R-F `domain_module_has_spi_package` + ArchUnit `SpiPurityGeneralizedArchTest`. CLAUDE.md Rule R-D sub-clause .a. See ADR-0067.

64. **Layered 4+1 discipline.** Every architecture artefact — root `ARCHITECTURE.md`, `agent-*/ARCHITECTURE.md`, `docs/L2/**/*.md`, `docs/adr/*.yaml`, `docs/logs/reviews/*.md` — MUST declare front-matter `level: L0 | L1 | L2` and `view: logical | development | process | physical | scenarios`. Root `ARCHITECTURE.md` is the canonical L0 corpus; per-module `agent-*/ARCHITECTURE.md` files are L1; deep technical designs under `docs/L2/` are L2. Each level MUST organise its content under the 4+1 view headings (L2 MAY omit views not relevant to the feature). All proposals in `docs/logs/reviews/` MUST declare `affects_level:` + `affects_view:`. Phase-released L0/L1 artefacts declaring `freeze_id: <non-null>` are read-only — further edits MUST flow through a new `docs/logs/reviews/*.md` proposal in the same commit. Enforced by Gate Rule R-G `architecture_artefact_front_matter` + Gate Rule R-I `review_proposal_front_matter` + Gate Rule R-M sub-clause .b `frozen_doc_edit_path_compliance` + ArchUnit `ArchitectureLayeringTest`. CLAUDE.md Rule G-1 sub-clause .a. See ADR-0068.

65. **Architecture workspace truth (amended W5 per ADR-0147; was "Architecture-graph truth").** The machine-readable architecture authority is `architecture/workspace.dsl` and its workspace closure: `architecture/profile/`, `architecture/features/` (capabilities + function points + verification), `architecture/docs/L1/` (L1 narrative), `architecture/decisions/` (ADR imports), `architecture/generated/` (emitted fragments), `architecture/views/`. The closure encodes every architectural relationship via `saa.rel` — `operationalised_by` (principle→rule), `enforced_by` (rule→enforcer), `verifies` (test→feature/function point/rule), `implements` (module→feature/function point/contract), `depends_on` (module→module), `declares_spi` (module→SPI), `publishes_contract` (module→contract), `decides` (ADR→target), `supersedes` / `extends` / `relates_to` (ADR→ADR, DAGs), `projects_to` (any→generated projection). The authored zone is hand-edited by engineers; the generated zone is emitted by `tools/architecture-workspace/.../fragment/AllFragmentsCli` from existing authoritative YAMLs and MUST NOT be hand-edited. Generated compatibility projections — `docs/governance/architecture-workspace-graph.yaml` (workspace-native, schema `architecture-workspace-graph/v1`) and `docs/governance/architecture-graph.yaml` (legacy, until W6 yaml sunset) — MUST be regenerated byte-identical on demand. Enforced by `gate/check_architecture_workspace.sh` (blocking at W5) + the existing Rule R-H `architecture_graph_well_formed`, Rule R-J.a `enforcer_reachable_from_principle`, Rule R-K `enforcer_anchor_resolves`, Rule R-L `architecture_graph_idempotent` (kept as defence-in-depth on the legacy projection until W7 retirement). CLAUDE.md Rule G-1 sub-clause .b. See ADR-0068 + ADR-0147 + ADR-0148.

---

## 5. W0 shipped capabilities

- `GET /v1/health` — liveness probe; JSON `{status, sha, db_ping_ns, ts}`.
- `TenantContextFilter` — extracts `X-Tenant-Id` header (UUID shape), propagates via
  `TenantContextHolder` + MDC `tenant_id`. (W0: header-only; W1: JWT claim; W2: GUC+RLS.)
- `IdempotencyHeaderFilter` — validates UUID shape of `Idempotency-Key` header on
  POST/PUT/PATCH; missing key returns 400 in research/prod. (W0: validation only;
  W1: dedup + caching backed by `IdempotencyStore`. See ADR-0027.)
- `IdempotencyStore` — `@Component` present but not injected at W0 (dev: WARNING log;
  research/prod: throws `IllegalStateException`). Wired in W1.
- `GraphMemoryRepository` SPI — interface only; no implementation shipped.
- `ResilienceContract` + `YamlResilienceContract` — per-operation resilience routing (operationId → policy triple).
- `Run` entity + `RunRepository` SPI — contract-spine entity (Rule R-C.c target); `mode` field (`GRAPH`|`AGENT_LOOP`) discriminates executor type; `parentRunId` + `parentNodeKey` + `SUSPENDED` status support interrupt-driven nesting.
- `IdempotencyRecord` entity — contract-spine entity with mandatory `tenantId` (Rule R-C.c target).
- `OssApiProbeTest` — compile-time probe verifying Spring AI + Spring Boot API surface.
- `ApiCompatibilityTest` — ArchUnit rules enforcing SPI purity and dependency direction.
- `RuntimeMustNotDependOnPlatformTest` — ArchUnit Rule R-C.e (L1 generalisation per ADR-0055): no class under `com.huawei.ascend.service.runtime..` (re-consolidated into `agent-service` per ADR-0088, rc13 — the intermediate post-ADR-0079 split into `agent-runtime-core` was dissolved) may import any class under `com.huawei.ascend.service.platform..` (HTTP-edge sub-package of `agent-service`, formerly the `agent-platform` module pre-Phase-C).
- `TenantPropagationPurityTest` — ArchUnit Rule R-C.e (original narrow case per ADR-0023, preserved as defence-in-depth): no class under `com.huawei.ascend.service.runtime..` may import `TenantContextHolder` (located at `agent-service.service.platform.tenant.TenantContextHolder` post-ADR-0078).
- `Orchestrator` SPI + `GraphExecutor` + `AgentLoopExecutor` + `SuspendSignal` + `Checkpointer` — dual-mode runtime SPIs (§4 constraint #9).
- `RunStateMachine` — DFA validator enforcing §4 #20 legal transitions; `validate/allowedTransitions/isTerminal` (Rule R-C.d). `RunStatus.EXPIRED` added as 7th terminal value.
- `InMemoryCheckpointer` — dev-posture in-memory checkpoint store with posture-aware 16-KiB
  payload cap (§4 #13 / §4 #25): dev posture emits WARN on oversize; research/prod throws
  `IllegalStateException`. W2: replaced by Postgres-backed impl.
- `SyncOrchestrator` + `SequentialGraphExecutor` + `IterativeAgentLoopExecutor` + `InMemoryRunRegistry`
  — reference executors proving 3-level bidirectional graph↔agent-loop nesting via `SuspendSignal`
  interrupt. `IterativeAgentLoopExecutor` enforces W0 String-cursor contract: throws
  `IllegalStateException` (with ADR-0022 reference) when a non-String payload would be silently
  corrupted by `Object.toString()` (HD-A.8 fix). Dev-posture only; not on the production code path.

---

## 6. Roadmap pointers

- Deferred capabilities and re-introduction triggers: `deferred_sub_clauses:` block in each alphanumeric rule card under `docs/governance/rules/` (parent-rule frontmatter); legacy rules awaiting human review at `docs/governance/escalations.md` (the prior `docs/CLAUDE-deferred.md` monolith was retired 2026-05-28)
- Current per-capability state and maturity levels: `docs/governance/architecture-status.yaml` (canonical machine-readable ledger; supersedes the pre-Phase-C `docs/STATE.md` archived at `docs/archive/2026-05-19-STATE-md-archived/` per ADR-0083)
- Per-capability shipped/deferred status: `docs/governance/architecture-status.yaml`
- Design rationale for pre-C26 decisions: `docs/v6-rationale/`
- Wave delivery plan (archived): `docs/archive/2026-05-13-plans-archived/` (see ADR-0037)
- OSS BoM with per-dep verification level: `docs/cross-cutting/oss-bill-of-materials.md`

---
level: L1
view: scenarios
affects_level: L0, L1
affects_view: [scenarios, logical, process, development, physical]
proposal_status: response
date: 2026-05-23
authors: ["chao", "Claude Code (Opus 4.7)"]
responds_to:
  - docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.en.md
related_adrs:
  - ADR-0020
  - ADR-0021
  - ADR-0049
  - ADR-0078
  - ADR-0088
  - ADR-0100
  - ADR-0104
  - ADR-0112
  - ADR-0115
---

# agent-service L1 Expansion Proposal — Dedicated Response

> Source proposal: `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.en.md` (authored 2026-05-21 by LucioIT + JiJi; status `proposed`).
> This response is the proposal-specific drill-down companion to the combined `2026-05-22-architecture-design-document-review-r1-r2-response.en.md`, anchored 1:1 to the proposal's own §-numbering so reviewers can read both side-by-side.

## Verdict

The proposal advances substantial L1 design direction for `agent-service` and is **accepted as L1 direction**, formally codified in `docs/adr/0115-agent-service-l1-expansion-acceptance.yaml`. We applied strict-scrutiny disposition to the 39 leaf claims:

| Disposition | Count | Anchor |
|---|---:|---|
| ACCEPT (direct, with shipped/scheduled evidence) | 24 | ADR-0112 + ADR-0115 Parts A/C/D + already-landed code |
| PARTIAL ACCEPT (concept accepted, naming/attribution/shape reconciled) | 9 | S3 + S4 + ADR-0112 |
| DEFER (W3+ explicit) | 5 | ADR-0115 Part G + `architecture-status.yaml#shadow_tool_interceptor` |
| **STRICT REJECT** (named claim contradicts shipped ADR; substitution mandated) | **6** | ADR-0104 + Rule R-G + ADR-0078 + ADR-0088 + ADR-0100 |

The reject count is **six, not one**. ADR-0115 explicitly named the package-root reject (Part F). This response surfaces five additional strict-rejects that the broader R1+R2 closure left implicit — substrate naming (Redis Lists / Disruptor / JPA), attribution mismatches (Engine owns Run Layer / Service handles backpressure), and the codified a2a-java SDK pervasion reject — each with a specific ADR / Rule anchor.

No findings rejected as invalid. Every reject cites a shipped ADR. Every partial-accept names the reconciliation. Every defer names the wave + scope.

## Stance lens (S3 / S4 / S5)

| Stance | Statement | Applied to |
|---|---|---|
| **S3** | Run is the platform spine. Task layers above; A2A `TaskState` is wire-vocabulary on Task, not on Run | §1.4, §1.5, §3.6, §4.3, §7.1 reconciliations |
| **S4** | A2A is a protocol boundary; `a2a-java` SDK is not our bus substrate | §1.3.5, §3.4, §5.1 rejections |
| **S5** | Package root preserved at `com.huawei.ascend.{service,engine,bus,middleware,client,evolve}.*` per ADR-0104; no `.agent.` segment | §5.2 rejection |

## Strict rejections (six, each with rebuttal + substitution)

### 3.1 §5.2 — Package root `com.huawei.ascend.agent.service.*` → REJECT

**Reviewer claim**: §5.2 declares the tree under `com.huawei.ascend.agent.service.*`.

**Rebuttal**: ADR-0104 (rc22 package-root migration) is the authoritative root-namespace ADR. The settled root is `com.huawei.ascend.{service,engine,bus,middleware,client,evolve}.*` — no `.agent.` segment. A third root-namespace migration in a year is unjustified absent an ADR superseding ADR-0104.

**Substitution**: The proposal's §5.2 tree is adopted **verbatim with the `.agent.` segment dropped**. The current package layout under `agent-service/src/main/java/com/huawei/ascend/service/` confirms this is already enforced; relevant subpackages:

```
agent-service/src/main/java/com/huawei/ascend/service/
├── api/{rest, a2a/}                # a2a/ planned W2
├── dispatcher/{strategy/}          # strategy/ planned W2; PolymorphicDispatcher.java W2
├── orchestrator/                   # DualTrackRouter.java W2; handler/ W2
├── task/{spi, repository/}         # Task.java + InMemoryTaskStateStore shipped
├── session/{spi, projection/}      # Session.java + ContextProjector + InMemoryContextProjector shipped
├── engine/{spi, adapter, workflow, react, heterogeneous}
│                                   # spi/StatelessEngine + adapter/InMemoryStatelessEngine shipped
│                                   # workflow/, react/ planned W2
│                                   # heterogeneous/ DEFERRED W3+ (Part G)
├── runtime/                        # Run, RunStateMachine, RunRepository — shipped spine
└── platform/{auth, idempotency, observability, persistence, posture, probe, resilience, tenant, web}
                                    # platform-side concerns — shipped
```

If `.agent.` adds meaningful semantic value the proposal authors should counter with a fresh ADR superseding ADR-0104. Until then we hold.

### 3.2 §1.3.5 / §3.4 / §5.1 — `a2a-java` SDK as bus substrate / backpressure transport / task-lifecycle owner → REJECT

**Reviewer claim**: §1.3.5 "Harnesses Google's official `a2a-java` SDK to handle handshakes, connection tunnels, routing matrix, and session correlation". §3.4 "Hosts Google `a2a-java` SDK Peer-to-Peer structures". §5.1 "Utilizes Google's `a2a-java` SDK to handle Backpressure transport / P2P Bus Foundations / Task-Centric Lifecycle".

**Rebuttal**: ADR-0100 §non_goals (Rejection 3) and ADR-0115 Part E explicitly reject SDK pervasion. Reasoning: (a) `agent-bus` is our canonical substrate per principle P-E (three-track channel isolation: control / data / rhythm); a third-party SDK as bus substrate violates that physical-channel discipline; (b) connection-tunnels + routing-matrix + session-correlation are `agent-bus` + `agent-middleware` responsibilities, not service-side concerns; (c) Reactor + virtual-thread are our canonical backpressure substrates per Rule R-G; (d) Run.status DFA per ADR-0020 is our task-lifecycle owner, with A2A `TaskState` as a separate wire-vocabulary mapped at the boundary, not replacing the DFA.

**Substitution**: A2A is adopted as a **protocol boundary** via `docs/contracts/a2a-envelope.v1.yaml` (`adoption_policy: contract_only`, `status: design_only`). The yaml declares the envelope shape + 5-state `task_states` enum + `internal_run_state_hint` mapping back to our Run states. The `a2a-java` SDK becomes an **optional Maven dep in `agent-bus` test scope only**, used for protocol conformance testing — never imported in production scope. Current state confirmed by audit: `pom.xml` has zero `a2a-java` deps; the only references in the active corpus are explicit reject statements.

### 3.3 §1.3.4 — "Redis Lists, JPA" as Platform-Centric durable substrate → REJECT (vocabulary)

**Reviewer claim**: §1.3.4 last paragraph names "distributed caches or **semi-persistent store backends** (e.g., Redis Lists, JPA)" as the Platform-Centric internal queue substrate.

**Rebuttal**: Our durable orchestration substrate is **Postgres + Temporal**, not Redis Lists. The 3-level persistence SPI per ADR-0021 is **Memory → Postgres → Temporal**, where:
- Memory tier = Reactor Sinks (in-JVM Fast-Path),
- Postgres tier = R2DBC-backed Run state + Checkpointer,
- Temporal tier = long-horizon orchestration with event-sourced workflow history.

Redis is the **cache / idempotency** tier (we ship `IdempotencyStore` postgres + redis impls per ADR-0057) — explicitly NOT the durable orchestration substrate. JPA is used at platform-side **read paths** (idempotency replay, posture, runs read-side); **runtime write paths use R2DBC** per Rule R-G, which forbids `JdbcTemplate` / `RestTemplate` under `service/runtime/**`.

**Substitution**: read §1.3.4 last paragraph as the 3-level persistence SPI per ADR-0021. The Platform-Centric "semi-persistence" framing is correct in intent; the named substrates need this vocabulary alignment.

### 3.4 §3.3 — "Disruptor" for compact memory performance → REJECT

**Reviewer claim**: §3.3 names "Disruptor" alongside Reactor Sinks as the Business-Centric in-memory queue substrate.

**Rebuttal**: **Disruptor is not in our OSS BoM** and not adopted in production code paths. **Reactor Sinks** is the canonical Reactor-aligned in-memory queue primitive per Rule R-G + the Reactive-API design pillar in §1.3.4. Adopting Disruptor in parallel would create two substrate vocabularies for the same role.

**Substitution**: read §3.3 Business-Centric as "Reactor Sinks (or Reactor-aligned equivalent)". If Disruptor offers a measured performance advantage that warrants in-tree adoption, that's a separate ADR with a benchmark obligation per Rule R-B.

### 3.5 §3.6 — "Engine Domain: Run Layer (Workflow DAG or ReAct Loop)" → REJECT (attribution)

**Reviewer claim**: §3.6 places "Run Layer (Workflow DAG or ReAct Loop)" inside the Engine Domain alongside "Task Execution Plane".

**Rebuttal**: Run **identity / DFA / persistence** lives in `service.runtime` (Service Domain) per:
- ADR-0078 (agent-service consolidation): Run entity is under `com.huawei.ascend.service.runtime.runs`,
- ADR-0088 (post-`agent-runtime-core` dissolution): Run-related sources redistributed back to Service-Domain modules,
- Rule R-C.2 sub-clauses .a (tenantId requirement) + .b (RunStateMachine.validate gatekeeper) + .c (tenant propagation purity — `service.runtime` ↛ `service.platform`).

The Engine **drives Run computation** via `StatelessEngine.execute(TaskSpec, InjectedContext) → Mono<StateDelta>` per ADR-0112, but it does **not own** the Run entity. Engine consumes Run *content* via `engine-envelope.v1.yaml`, not the Run entity itself.

**Substitution**: §3.6 Engine Domain row reads as "Engine Domain: stateless computation per `StatelessEngine.execute(...)`; consumes `TaskSpec` + `InjectedContext`; produces `StateDelta` carrying nullable `InterruptSignal`". Service Domain owns Run + Task + Session + Memory.

### 3.6 §1.5 — "Service: ... handling queue backpressure" → REJECT (attribution)

**Reviewer claim**: §1.5 "Service: Task State Machine, Session Projection, Memory Consolidation — Maintaining the Task state machines and **handling queue backpressure**".

**Rebuttal**: Backpressure is a **Reactor-stream invariant** that crosses the Service ↔ Engine boundary via `Mono<StateDelta>` per Rule R-G + ADR-0112. Neither Service nor Engine "handles" backpressure as a responsibility; the **reactive substrate enforces it inherently**. Service owns Task state-machine + Session projection + Memory consolidation; the *fact* that backpressure works correctly is a property of using Reactor types in the SPI signature.

**Substitution**: §1.5 Service row reads as "Maintaining Task state machines, Session projection, Memory consolidation; reactive substrate (Reactor) provides backpressure semantics inherently". This is a wording fix, not an architectural disagreement.

## Partial accepts (nine, concept ✓, naming/shape/attribution reconciled)

| Proposal § | Concept accepted | Reconciliation |
|---|---|---|
| **§1.4 Layer 1 Run = single-step computation** | Run plays a *single-step computational role* per `execute()` call | The Run **entity** persists across multiple steps and owns the DFA. "Execution step" ≠ Run entity. Anchor: ADR-0020 + ADR-0112. |
| **§1.4 Layer 2 Task = orchestrable** | Task is a NEW outer-layer entity, 1:N to Runs | Already shipped: `service/task/Task.java` (record with `taskId`, `tenantId`, `sessionId`, `taskKind`, `a2aState`, `stepNumber`, `whyStopped`, `createdAt`, `updatedAt`). Anchor: ADR-0115 Part B. |
| **§1.4 Layer 3 Session = multi-turn** | Yes | Reuse existing `TraceContext.sessionId` (ADR-0061) + already-shipped `service/session/Session.java`. |
| **§1.4 Layer 4 Memory = long-term** | Yes | Reuse `GraphMemoryRepository` per ADR-0034 / ADR-0051 / ADR-0082. |
| **§1.5 Engine = pure computation** | Yes | Per ADR-0112 + Rule R-G. **(Runs-on-Engine attribution rejected — see strict reject 3.5.)** |
| **§1.3.5 A2A Symmetric Dual Roles + Spatial + Lifecycle** | Yes — agent-service is both A2A server + A2A client | **(SDK pervasion rejected — see strict reject 3.2.)** |
| **§3.4 A2A Connector — wire envelope** | Yes via `docs/contracts/a2a-envelope.v1.yaml` (already shipped, `design_only`) | **(SDK adoption rejected — see strict reject 3.2.)** |
| **§7.1 InterruptSignal interface shape** | Yes — strong-typed signal for engine yields | Production type stays `SuspendSignal` per ADR-0112 (richer field set than proposal's 3-field interface). Proposal's `InterruptSignal` interface is conceptually correct; we don't re-introduce the narrower SPI. |
| **§7.1 InterruptType 5-value enum** | Yes — A2A wire vocabulary | Maps 1:1 to `SuspendReason` 5-value enum per ADR-0112. Mapping table: `INPUT_REQUIRED↔AwaitClientCallback`, `SUB_TASK_AWAIT↔AwaitChildRun`, `TOOL_EXECUTION↔AwaitToolResult`, `DELAY_AWAIT↔AwaitTimer`, `POLICY_APPROVAL↔RequiresApproval`. |

## Defers (five subsections + consolidated W3+ rationale)

| Proposal § | Topic | W3+ scope rationale |
|---|---|---|
| §1.3.3 | Heterogeneous Compatibility & Legacy Decoupling | `agent-evolve` heterogeneous integration scope. W0/W1/W2 focus is in-house Workflow + ReAct adapters. |
| §1.3.8 | Heterogeneous Anti-Corruption + Shadow Tool Interceptor | Per ADR-0115 Part G. Pattern accepted as W3+ design direction; impl lands with agent-evolve heterogeneous integration. |
| §2.2 | Legacy/Heterogeneous Agent Integration scenario | Same scope as §1.3.3. |
| §2.5 | Heterogeneous Shadow-Plugin Interception + Sandbox Execution scenario | Same scope as §1.3.8. |
| §4.5 + §7.4 | Heterogeneous Framework Shadow Interceptor Flow + `HeterogeneousEngineAdapter` / `ShadowToolInterceptor` SPIs | SPI design pattern accepted; impl + SPI ship under `agent-evolve/.../heterogeneous` per Part G. |

Anchor: `docs/governance/architecture-status.yaml#shadow_tool_interceptor` row — "ACCEPTED as W3+ design direction; implementation lands with agent-evolve heterogeneous integration scope. W0/W1/W2 does NOT host third-party frameworks. Deferred to W3+."

§3.2 Engine Adapter is **partial defer**: Workflow + ReAct adapters ACCEPT at W2; Heterogeneous Anti-Corruption Subsystem (Framework Adapter + Shadow Tool Interceptor + Context Translator) DEFER to W3+.

## Direct accepts (twenty-four subsections with shipped/scheduled evidence)

| Proposal § | Topic | Evidence |
|---|---|---|
| §1.1.1 | Six core modules | Canonical in `ARCHITECTURE.md` §1.1; module names match `pom.xml` reactor. |
| §1.1.2 | Two deployment modes (Platform-Centric, Business-Centric) | ADR-0115 Part A; physical-view variations, not new planes. |
| §1.2 | Phased roadmap (ecosystem first, scale later; in-house Client/Service/Engine; OSS Bus/Middleware) | `ARCHITECTURE.md` §1.1 audience boundary; W0–W3 wave plan; `competitive-baselines.yaml`. |
| §1.3.1 | Dual agent form factors (Workflow + ReAct) | Already shipped per ADR-0049 / ADR-0073; both modes execute through `StatelessEngine.execute(...)`. |
| §1.3.2 | Dual integration models (embedded co-process + stateless service) | ADR-0115 Part A. Both modes preserve `client → service → engine` chain per Rule R-I.1 (ArchUnit `EdgeToComputeDirectLinkArchTest`). |
| §1.3.4 (most) | Backpressure + reactive + stateless execution | Rule R-G + ADR-0021 + ADR-0112. **(Redis/JPA substrate naming rejected — see strict reject 3.3.)** |
| §1.3.6 | Task-centric state machine + explicit InterruptSignal | Already shipped per ADR-0112 value-based yield. `Mono<StateDelta>` SPI signature; no checked exception. |
| §1.3.7 | Dual-Track Fast/Slow-Path Routing | ADR-0115 Part D. `architecture-status.yaml#dual_track_router` row exists; `DualTrackRouter.java` planned W2 at `service/orchestrator/`. |
| §1.6 | Message (data plane) vs Task (control plane) separation | ADR-0115 Part C. `architecture-status.yaml#message_vs_task_plane_separation` row exists. Inbound NL→Task compilation via `ContextProjector` SPI (shipped at `service/session/spi/ContextProjector.java`). |
| §2.1 | Embedded scenario (Co-process Mode) | Part A Business-Centric physical view. |
| §2.3 | Cross-Node Async A2A Collaboration scenario | Aligns with ADR-0115 Part E + `a2a-envelope.v1.yaml`. |
| §2.4 | Ultra-fast Fast-Path Low Latency Execution scenario | Part D `DualTrackRouter` Fast-Path. |
| §3.1 | Polymorphic Dispatcher | `service/dispatcher/package-info.java` stub exists; `PolymorphicDispatcher.java` planned W2. |
| §3.3 (Reactor Sinks part) | Internal Event Queue — Memory-based (Reactor Sinks) | Rule R-G + Reactor adoption. **(Disruptor naming rejected — see strict reject 3.4.)** |
| §3.5 | Task-Centric State Control + Interrupt Interceptor + Dual-Track Router | ADR-0115 Part D. Composes with existing `RunStateMachine` (ADR-0020) + `SuspendSignal` (ADR-0112). |
| §4.1 | Asynchronous Task Loop | Rule R-F cursor flow + Rule R-G reactive substrate. |
| §4.2 | A2A collaboration + interrupt lifecycle | ADR-0112 value-based yield + suspension. |
| §4.3 | 4-layer lifecycle flow | Per §1.4 reconciliation — Task as outer entity, Run as persistence spine. |
| §4.4 | Dual-Track Fast/Slow-Path dispatch loop | Part D. Slow-Path = SyncOrchestrator + Checkpointer + Temporal per ADR-0021. |
| §5.1 (in-house focus) | In-house: local polymorphic routing + semantic projection + REST/gRPC + Temporal persistence SPI adapters | Matches ADR-0115 alternative consideration. **(a2a-java SDK depth rejected — see strict reject 3.2.)** |
| §6.1 | Embedded Co-process Topology | Part A Business-Centric physical view. |
| §6.2 | Decoupled Service-level Topology | Part A Platform-Centric physical view. Multi-instance statelessness over Redis (cache) + Postgres (durable Run state) + NATS/RabbitMQ (bus). |
| §6.3 | Dual-Track Compute and Storage Boundaries | Part D. Fast-Path = JVM heap; Slow-Path = Postgres + Temporal per ADR-0021. |
| §7.2 + §7.3 | `StatelessEngineExecutor` SPI (proposed label only; not current) + `DualTrackRouter` + `SlowPathExecutor` SPI | Already shipped (ADR-0112): `service/engine/spi/StatelessEngine.java`. `DualTrackRouter` planned W2 per Part D; `SlowPathExecutor` implemented by `SyncOrchestrator` + `Checkpointer` per ADR-0021. |

## What this acceptance triggers (W1/W2/W3+ engineering follow-ups, reference-only)

The following items are **referenced**, not committed, in this wave:

| Wave | Item | Path |
|---|---|---|
| W2 | `PolymorphicDispatcher.java` impl (replaces package-info stub) | `agent-service/src/main/java/com/huawei/ascend/service/dispatcher/PolymorphicDispatcher.java` |
| W2 | `DualTrackRouter.java` impl (replaces package-info stub) | `agent-service/.../service/orchestrator/DualTrackRouter.java` |
| W2 | `dual-track-routing-policy.yaml` (per-InterruptType policy table) | `docs/governance/dual-track-routing-policy.yaml` (NEW) |
| W2 | `Task` → `Run` 1:N relationship persistence + JPA/R2DBC repository | `service/task/repository/` (planned) |
| W2 | Promote `a2a-envelope.v1.yaml` `design_only` → `runtime_enforced` | gated on first A2A interop test landing |
| W2 | `service/api/a2a/` A2A server adapter | inbound A2A protocol endpoint |
| W2 | `service/engine/workflow/` + `service/engine/react/` adapters | per §3.2 (non-heterogeneous part) |
| W3+ | `agent-evolve/ARCHITECTURE.md` Heterogeneous-Integration section | Shadow Tool Interceptor + Anti-Corruption layer |
| W3+ | `service/engine/heterogeneous/` adapter + shadow + translation subpackages | per §1.3.8 / §3.2 (heterogeneous part) / §7.4 |

**NO new architectural commitments in this response** — these are reference-only pointers so the reviewer sees forward trajectory.

## Audit trail (this wave)

A categorised self-audit was executed across six root-cause buckets before this response was written. Results:

| Bucket | Root cause | Live-corpus leaks found |
|---|---|---|
| A | Vocabulary/identity collision (Run-as-spine vs Run-as-single-step) | **0** |
| B | a2a-java SDK pervasion | **0** (zero deps in `pom.xml`; all corpus references are explicit reject statements or the contract-only adoption note) |
| C | Package root `.agent.` segment leak | **0** (all 7 hits are inside the proposal or in reject-with-rationale docs) |
| D | Shadow Tool Interceptor / heterogeneous-host scope-eagerness | **0** (architecture-status.yaml row explicitly W3+) |
| E | Redis/Disruptor/JPA as durable substrate vocabulary leak | **0** |
| F | Engine-owns-Run / Service-handles-backpressure attribution leak | **0** |

The corpus is internally consistent with our stated stances. The strict-scrutiny pass widens the rejection surface from one (ADR-0115 Part F) to six, but each new reject is **named drift in the proposal's vocabulary / attribution** — not drift in our shipped code.

No new recurring-defect family promoted from this wave. The "external proposal contradicts shipped ADR" pattern is a 1-instance observation; promotion requires 2nd recurrence per ADR-0097 cool-down. Pattern documented here only.

## Out of scope (explicit non-goals)

- **No new ADR.** ADR-0115 + ADR-0112 + ADR-0100 + ADR-0104 already encode the formal architectural decisions.
- **No code changes.** All affected Java surfaces are already shipped under main.
- **No new contracts.** `a2a-envelope.v1.yaml` already exists with `adoption_policy: contract_only`.
- **No `architecture-status.yaml` row additions.** The 5 ledger rows tied to ADR-0115 already exist (`four_layer_state_model`, `message_vs_task_plane_separation`, `dual_track_router`, `a2a_protocol_boundary`, `shadow_tool_interceptor`).
- **No update to the combined R1+R2-response file.** It stays as the wider closure narrative; this file is the proposal-specific drill-down.
- **No `.cn.md` Chinese companion in this wave.** The proposal has both `.en.md` and `.cn.md`; if `.cn.md` parity is wanted, it ships as a follow-up doc-translation task.
- **No `agent-evolve/ARCHITECTURE.md` Heterogeneous-Integration section** — W3+ deliverable; only referenced here.

## Composes with

- `/review-mode` — the phase contract that drove this response.
- `/design-mode` — the phase contract for the W2 follow-up implementations (DualTrackRouter, PolymorphicDispatcher, dual-track-routing-policy.yaml).
- ADR-0115 — the formal acceptance vehicle. This response is its reviewer-facing companion.

## References

- `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.en.md` — source proposal (LucioIT + JiJi).
- `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.cn.md` — Chinese-language source proposal.
- `docs/logs/reviews/2026-05-22-architecture-design-document-review-r1-r2-response.en.md` — combined R1+R2 + L1-proposals closure (this file is its proposal-specific drill-down).
- `docs/adr/0115-agent-service-l1-expansion-acceptance.yaml` — formal acceptance ADR with Parts A–G reconciliations.
- `docs/adr/0112-engine-stateless-executor-value-based-yield.yaml` — value-based yield contract.
- `docs/adr/0104-rc22-package-root-migration-to-com-huawei-ascend.yaml` — package-root authority (S5 anchor).
- `docs/adr/0100-rc22-agent-service-l1-runtime-role-decomposition.yaml` — §non_goals Rejection 3 (S4 anchor).
- `docs/adr/0078-agent-service-consolidation.yaml` — Run-in-Service-Domain anchor.
- `docs/adr/0088-agent-runtime-core-dissolution.yaml` — post-dissolution source distribution.
- `docs/adr/0021-layered-spi-taxonomy.md` — 3-level persistence SPI (Memory → Postgres → Temporal).
- `docs/adr/0020-run-status-dfa.yaml` — Run.status DFA authority.
- `docs/contracts/a2a-envelope.v1.yaml` — contract-only A2A adoption.
- `agent-service/ARCHITECTURE.md` — L1 architecture (current shipped state).

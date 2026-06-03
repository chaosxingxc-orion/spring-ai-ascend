---
level: L1
view: logical
module: agent-runtime
status: consolidated-run-owning-runtime
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0159 (agent-runtime consolidation + agent-service serviceization refounding; supersedes ADR-0158 §Decision.5 engine tenant-neutrality); ADR-0072 (Engine Envelope + Strict Matching); ADR-0126 (Planner SPI); Layer-0 principle P-M (Heterogeneous Engine Contract); Rule R-M.a (Engine Envelope Single Authority, formerly Rule 43), Rule R-M.b (Strict Engine Matching, formerly Rule 44)"
---

# agent-runtime — L1 architecture (run-owning runtime kernel)

> Owner: AgentRuntime team | Plane: compute_control | Maturity: design-phase scaffolding — engine + dispatch + access + session/task-control SPI surfaces; Run domain impl deferred

## Status

`agent-runtime` is the **run-owning runtime SDK**: the self-contained,
independently-bootable runtime that developers integrate against to drive
Agent instances built on heterogeneous agent frameworks. Per **ADR-0159**
it consolidates the former `agent-execution-engine` (whose module identity is
dissolved) with the runtime internals of the former `agent-service` —
everything except the serviceization façade, which remains in `agent-service`.
The package root is `com.huawei.ascend.runtime.*`.

ADR-0159 supersedes only **ADR-0158 §Decision.5** (engine tenant-neutrality):
as the full run-owning runtime, `agent-runtime` owns Run / session / tenant.
The neutral execution **port** stays transport-agnostic and homed in `agent-bus`
(see below); only the claim that the engine must itself be tenant-neutral is
withdrawn.

**Design-phase note.** The repository is intentionally in the design phase.
This module ships SPI surfaces, `@Configuration` / `AutoConfiguration` wiring,
and protocol-access scaffolding. The Run domain kernel
(`Run` / `RunStateMachine` / `IdempotencyRecord` persistence) is a **design
target, not yet materialized** — its contract is fixed by the L0 §4 constraint
corpus (#9 dual-mode runtime, #11 northbound handoff, #20 RunStatus DFA, …) and
the executable kernel lands in a later implementation phase. It MUST NOT be
stubbed to "fill the box" before the design phase exits.

### What lives in this module

| Package | Role |
|---|---|
| `runtime.engine.spi` | engine-adapter SPI: `ExecutorAdapter`, `GraphExecutor`, `AgentLoopExecutor`, `EngineHookSurface`, `EngineMatchingException` |
| `runtime.engine.planner.spi` | planner SPI (`Planner` / `Plan` / `PlanStep` / `PlanningRequest` / `PlanningResult` / `BranchPoint` / `LoopAnnotation`) per ADR-0126 |
| `runtime.engine.runtime` | engine implementation home: `EngineRegistry` (single `resolve(envelope)` authority), `EngineEnvelope` (mirrors `engine-envelope.v1.yaml`), `InProcessEnginePort` (in-process realization of the neutral `EnginePort`) |
| `runtime.engine.exec` | reference executors |
| `runtime.dispatch` | engine dispatch: `AgentHandler` / `AgentResultAdapter` (SPI in `runtime.dispatch.spi`) + `AccessLayerClient` / `TaskControlClient` callback ports (`runtime.dispatch.port`) |
| `runtime.access` | A2A protocol access layer (`A2aJsonRpcController`, `A2aWellKnownAgentCardController`, submission + notification ports) |
| `runtime.session` | session management |
| `runtime.taskcontrol` | task-centric control |
| `runtime.queue` | internal event queue |
| `runtime.schema` | runtime schema / response types |
| `runtime.bootstrap` | the bootable runtime application `AgentRuntimeApplication` |

### The neutral EnginePort stays in agent-bus

The neutral orchestration/engine SPI (`Orchestrator`, `RunContext`,
`SuspendSignal`, `Checkpointer`, `TraceContext`, `RunMode`,
`ExecutorDefinition`, `ExecutionContext`) lives in **agent-bus** under
`com.huawei.ascend.bus.spi.engine` (transport-agnostic EnginePort boundary,
ADR-0158). `agent-runtime` **consumes** that vocabulary and **realizes** the
port via `InProcessEnginePort`; it does not own the neutral SPI. Treating the
engine as a real instance behind a neutral port is what lets a single Run cross
in-process, RPC, and A2A transports without changing the runtime kernel.

### Dependency direction

`agent-runtime → agent-bus` (neutral `bus.spi.engine` EnginePort + `bus.spi.s2c`
callback transport consumed by the engine registry) and
`agent-runtime → agent-middleware` (`HookPoint` enum + `RuntimeMiddleware`
listener). Never `agent-runtime → agent-service`: the serviceization façade is
downstream. `agent-service → agent-runtime` is the only legal cross edge
(Rule 10 / ArchUnit); there is no reverse edge.

## 0.4 Layered 4+1 view map

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | run-owning runtime kernel + heterogeneous engine contract surface |
| §2 Envelope schema | logical | `docs/contracts/engine-envelope.v1.yaml` |
| §3 Matching strictness | process | Rule R-M.b — `engine_type=X` is executed only by adapter X |

## 1. Role

`agent-runtime` owns, as one self-contained runtime:

- the **engine contract surface** — `EngineEnvelope` (execution-engine request
  shape: `envelope_version`, `engine_type`, `payload_class_ref`, `schema_ref`),
  `EngineRegistry` (single authority for `resolve(envelope)` /
  `resolveByPayload(def)`; pattern-matching on `ExecutorDefinition` subtypes
  OUTSIDE the registry is forbidden — Rule R-M.a), the `ExecutorAdapter` +
  engine-type-specific executor SPIs (`GraphExecutor`, `AgentLoopExecutor`), and
  the planner SPI (ADR-0126);
- **engine dispatch** (`runtime.dispatch`) — routing an accepted Run to the
  matched adapter, with the access-layer and task-control client ports;
- the **access layer** (`runtime.access`) — A2A protocol ingress that hands work
  to the runtime;
- **session / task-control / internal event queue** scaffolding for run-state
  coordination;
- the **bootable application** (`AgentRuntimeApplication`) — boots the access +
  bootstrap component scan; session, queue, task-control and engine contribute
  through their `AutoConfiguration` imports.

## 2. Envelope schema (authority)

`docs/contracts/engine-envelope.v1.yaml` is the single source of truth. The
`EngineEnvelope` Java record mirrors the schema (required fields validated on
construction). `known_engines` membership is enforced by
`EngineRegistry.resolve(...)` + registry boot validation; constructor-level
membership validation is deferred per Rule M-2.a.c (formerly Rule 48.c).

## 3. Strict matching (Rule R-M.b, formerly Rule 44)

A Run with `engine_type=X` executes only on the adapter registered under `X`.
Mismatch → `EngineMatchingException` → `Run.FAILED` with reason
`engine_mismatch`. **No fallback policy.** No silent reinterpretation of payloads
as another engine's configuration.

## 4. Forbidden imports

`com.huawei.ascend.runtime.engine.spi.*` imports only `java.*` + `agent-middleware`
SPI (for `HookPoint` reference) + the neutral `bus.spi.engine` carriers it
consumes. Enforced by `SpiPurityGeneralizedArchTest` (E48). No SPI package
imports Spring, Micrometer, OTel, or reference implementations.

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. `docs/contracts/engine-envelope.v1.yaml` — envelope schema.
3. `docs/contracts/engine-hooks.v1.yaml` — hook surface this engine fires
   (consumed via `agent-middleware`).
4. ADR-0159 — consolidation + refounding authority; ADR-0072 — engine authority.
5. `docs/dfx/agent-runtime.yaml` — Design-for-X declarations.

---

## 5. Development View (Rule G-1.1.a)

Current namespace (`com.huawei.ascend.runtime.*`):

```text
agent-runtime/
└── src/main/java/com/huawei/ascend/runtime/
    ├── engine/
    │   ├── spi/                  # ExecutorAdapter, GraphExecutor, AgentLoopExecutor,
    │   │                         #   EngineHookSurface, EngineMatchingException
    │   ├── planner/spi/          # Planner, Plan, PlanStep, PlanningRequest, ... (ADR-0126)
    │   ├── runtime/              # EngineRegistry, EngineEnvelope, InProcessEnginePort
    │   └── exec/                 # reference executors
    ├── dispatch/                 # engine dispatch
    │   ├── spi/                  #   AgentHandler, AgentResultAdapter
    │   └── port/                 #   AccessLayerClient, TaskControlClient
    ├── access/                   # A2A protocol ingress (A2aJsonRpcController,
    │                             #   A2aWellKnownAgentCardController, submission/notification ports)
    ├── session/                  # session management
    ├── taskcontrol/              # task-centric control
    ├── queue/                    # internal event queue
    ├── schema/                   # runtime schema / response types
    └── bootstrap/                # AgentRuntimeApplication (bootable runtime app)
```

The neutral orchestration/engine SPI (`Orchestrator`, `RunContext`,
`SuspendSignal`, `Checkpointer`, `TraceContext`, `RunMode`,
`ExecutorDefinition`, `ExecutionContext`) lives in **agent-bus** under
`com.huawei.ascend.bus.spi.engine`; this module realizes the `EnginePort`
boundary rather than owning the SPI vocabulary.

Deployment loci (ADR-0101): `agent-runtime` is location-agnostic — same SPI,
same engine envelope — and supports both platform-centric and business-centric
loci (`deployment_loci: [platform_centric, business_centric]`).

## *SPI Interface Appendix* (Rule G-1.1.b)

`agent-runtime` produces three internal SPI packages (cross-validated against
`module-metadata.yaml#spi_packages`, `docs/contracts/contract-catalog.md`,
`docs/dfx/agent-runtime.yaml`):

| Interface FQN | SPI package | Purpose |
|---|---|---|
| `com.huawei.ascend.runtime.engine.spi.ExecutorAdapter` | `runtime.engine.spi` | Unified engine adapter contract |
| `com.huawei.ascend.runtime.engine.spi.GraphExecutor` | `runtime.engine.spi` | Workflow-graph execution |
| `com.huawei.ascend.runtime.engine.spi.AgentLoopExecutor` | `runtime.engine.spi` | ReAct-loop execution |
| `com.huawei.ascend.runtime.engine.spi.EngineHookSurface` | `runtime.engine.spi` | Engine-side hook declaration (cooperates with `agent-middleware` HookPoint) |
| `com.huawei.ascend.runtime.engine.spi.EngineMatchingException` | `runtime.engine.spi` | Throw on `engine_type` mismatch (Rule R-M.b) |
| `com.huawei.ascend.runtime.engine.planner.spi.Planner` | `runtime.engine.planner.spi` | Engine-side plan generator (ADR-0126) |
| `com.huawei.ascend.runtime.dispatch.spi.AgentHandler` | `runtime.dispatch.spi` | Engine dispatch entry for an accepted Run |
| `com.huawei.ascend.runtime.dispatch.spi.AgentResultAdapter` | `runtime.dispatch.spi` | Adapt an engine result back into the dispatch outcome |

Implementation homes (NOT SPI):
- `com.huawei.ascend.runtime.engine.runtime.EngineRegistry` — the only authority
  for `resolve(envelope)`; pattern-matching on `ExecutorDefinition` outside this
  class is forbidden (Rule R-M.a).
- `com.huawei.ascend.runtime.engine.runtime.EngineEnvelope` (record) — mirrors
  `engine-envelope.v1.yaml`.
- `com.huawei.ascend.runtime.engine.runtime.InProcessEnginePort` — in-process
  realization of the neutral `bus.spi.engine.EnginePort`.

## *L2 Constraint Linkage* (Rule G-1.1.c)

Vacuously green at design phase. The Run-kernel implementation phase (RunStatus
DFA, idempotency claim/replay, suspend/resume durability per L0 §4 #9/#11/#20)
will produce an L2 design; when authored it MUST include Boundary Contracts.

## Deployment loci

`deployment_loci: [platform_centric, business_centric]` — the runtime is
location-agnostic; it supports both loci behind the same engine envelope.

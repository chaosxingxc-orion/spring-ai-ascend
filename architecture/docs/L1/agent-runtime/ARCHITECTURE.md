---
level: L1
view: logical
module: agent-runtime
status: consolidated-run-owning-runtime
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0159 (agent-runtime consolidation + agent-service serviceization refounding; supersedes ADR-0158 §Decision.5 engine tenant-neutrality); Layer-0 principle P-M (Heterogeneous Engine Contract)"
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
| `runtime.engine.spi` | framework-neutral runtime SPI: `AgentRuntimeHandler` (run one agent, surface its output), `StreamAdapter` (adapt a framework's native result stream into the neutral `AgentExecutionResult` stream), optional `AgentCardProvider`, reserved `SetState` / `MemoryProvider`, carrier `AgentExecutionResult` |
| `runtime.engine` (root) | engine dispatch + internals all flattened into the root: `EngineDispatcher` (routes a command to the matched handler), `EngineWorker` (internal command worker), the command events (`EngineCommandEvent` / `EngineCommandEventFactory` / `EngineCommandGateway` / `InternalEngineCommandGateway`), `EngineExecutionScope`, `EngineInput` / `EngineOutput`, the single `EngineEvent` record + `EngineEventKind` enum, and the outbound ports `TaskControlClient` + `AccessLayerClient` (engine → control / access; intra-service, not SPI) |
| `runtime.engine.api` | inbound `EngineExecutionApi` (enqueue execute / resume / cancel) |
| `runtime.engine.openjiuwen` | concrete `AgentRuntimeHandler` adapter for openJiuwen ReAct agents |
| `runtime.engine.agentscope` | concrete `AgentRuntimeHandler` adapters for AgentScope SDK, Harness, and REST/SSE runtime-client integration |
| `runtime.access` | A2A protocol access layer flattened into the root (`AccessSubmissionService`, `AccessLayerConfiguration`, `AgentNotification` egress/notification ports) with the wire-protocol controllers + mappers under `runtime.access.a2a` (`A2aJsonRpcController`, `A2aWellKnownAgentCardController`, …) |
| `runtime.session` | session management (`RuntimeSessionRepository` + in-memory impl) |
| `runtime.control` | task-centric control — the single run-lifecycle authority (`TaskControlApi`) |
| `runtime.queue` | internal event queue |
| `runtime.common` | shared runtime types (`AgentRequest` / `RunStatus`; live response model is the streaming `AgentResponseEvent` — the former monolithic `AgentResponse` model was deleted) |
| `runtime.app` | framework-neutral bootable entry `RuntimeApp` / `RuntimeHost` + Spring-backed `LocalA2aRuntimeHost`; cross-layer wiring `RuntimeWiringConfiguration` |

### The neutral EnginePort stays in agent-bus

The neutral orchestration/engine SPI (`Orchestrator`, `RunContext`,
`SuspendSignal`, `Checkpointer`, `TraceContext`, `RunMode`,
`ExecutorDefinition`, `ExecutionContext`) lives in **agent-bus** under
`com.huawei.ascend.bus.spi.engine` (transport-agnostic boundary, ADR-0158).
`agent-runtime` **consumes** that vocabulary (e.g. `RunContext` / `SuspendSignal`);
it does not own the neutral SPI.

### Dependency direction

`agent-runtime → agent-bus` (neutral `bus.spi.engine` RunContext / SuspendSignal
vocabulary consumed by the engine). Never `agent-runtime → agent-service`: the
serviceization façade is downstream. `agent-service → agent-runtime` is the only
legal cross edge (Rule 10 / ArchUnit); there is no reverse edge.

## 0.4 Layered 4+1 view map

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | run-owning runtime kernel + heterogeneous engine contract surface |
| §2 Handler dispatch | logical | `AgentRuntimeHandler` registered per `agentId`; `EngineDispatcher` routes |
| §3 Single-write authority | process | engine → one `TaskControlClient` port; `control` gates egress |

## 1. Role

`agent-runtime` owns, as one self-contained runtime:

- the **framework-neutral runtime SPI** (`runtime.engine.spi`) —
  `AgentRuntimeHandler` runs one agent and surfaces its output; `StreamAdapter`
  adapts a framework's native result stream into the neutral `AgentExecutionResult`
  stream; framework-specific decoration stays inside each adapter. The shipped
  adapters include `engine.openjiuwen` (openJiuwen ReAct) and
  `engine.agentscope` (AgentScope SDK, Harness, and REST/SSE runtime client);
- **engine dispatch** (`runtime.engine`) — `EngineDispatcher` routes an accepted
  command to the handler registered for its `agentId` (an unknown `agentId`
  converges to a terminal `AGENT_ID_INVALID` through the control authority, never
  a hung task); `EngineWorker` and the command-event types (`EngineCommandEvent`,
  `EngineCommandGateway`, …) live in the engine root and drive it off the internal
  queue behind the inbound `engine.api.EngineExecutionApi`; outcomes are emitted as
  the single `EngineEvent` record discriminated by `EngineEventKind`;
- the **access layer** (`runtime.access`) — A2A protocol ingress (controllers +
  mappers under `runtime.access.a2a`) that hands work to the runtime and streams
  output back (task-scoped egress);
- **session / task-centric control / internal event queue** for run-state
  coordination — `control` is the single run-lifecycle authority and the only
  writer of caller-facing egress;
- the **bootable application** (`runtime.app`) —
  `RuntimeApp.create(handler).run(LocalA2aRuntimeHost.port(p))`; `RuntimeApp` /
  `RuntimeHost` are Spring-free, Spring Boot is confined to `LocalA2aRuntimeHost`.

## 2. Handler registration + dispatch

Each agent is served by an `AgentRuntimeHandler` registered under its `agentId`
in `AgentRuntimeHandlerRegistry`. `EngineDispatcher` resolves the handler for an
accepted command and runs it; resolution and execution are guarded so any failure
— including an unknown `agentId` — converges to a terminal outcome (errorCode
`AGENT_ID_INVALID`) through the control authority rather than hanging the task.

## 3. Single-write authority

The engine reports every outcome to exactly one outbound port
(`engine.TaskControlClient`); `control` is the sole run-lifecycle authority and gates
caller-facing egress on its own accepted transitions. The engine never writes egress
directly. Single-write authority: only on an ACCEPTED state transition does `control`
fan caller-facing egress out through `engine.AccessLayerClient`, so each outcome is
written exactly once — there is no double-write between the engine and the access layer.

## 4. Forbidden imports

`com.huawei.ascend.runtime.engine.spi.*` imports only `java.*` + the neutral
`bus.spi.engine` carriers it consumes. Enforced by `SpiPurityGeneralizedArchTest`
(E48). No SPI package imports Spring, Micrometer, OTel, or reference implementations.

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. `runtime.engine.spi.AgentRuntimeHandler` — the framework-neutral runtime SPI.
3. `runtime.app.RuntimeApp` / `LocalA2aRuntimeHost` — the bootable entry.
4. ADR-0159 — consolidation + refounding authority.
5. `docs/dfx/agent-runtime.yaml` — Design-for-X declarations.

---

## 5. Development View (Rule G-1.1.a)

Current namespace (`com.huawei.ascend.runtime.*`):

```text
agent-runtime/
└── src/main/java/com/huawei/ascend/runtime/
    ├── engine/                   # engine ROOT: EngineDispatcher (routes a command to
    │   │                         #   the registered handler), EngineWorker, command events
    │   │                         #   (EngineCommandEvent / EngineCommandGateway / …),
    │   │                         #   EngineExecutionScope, EngineInput / EngineOutput,
    │   │                         #   EngineEvent record + EngineEventKind enum, and the
    │   │                         #   outbound ports TaskControlClient + AccessLayerClient
    │   ├── spi/                  # AgentRuntimeHandler, StreamAdapter,
    │   │                         #   AgentCardProvider, SetState, MemoryProvider,
    │   │                         #   AgentExecutionResult
    │   ├── api/                  # EngineExecutionApi (inbound enqueue)
    │   ├── openjiuwen/           # openJiuwen ReAct AgentRuntimeHandler adapter
    │   └── agentscope/           # AgentScope SDK / Harness / REST-SSE runtime adapters
    ├── access/                   # A2A protocol access ROOT (AccessSubmissionService,
    │   └── a2a/                  #   AgentNotification egress) + wire controllers/mappers
    │                             #   (A2aJsonRpcController, A2aWellKnownAgentCardController, …)
    ├── session/                  # session management (RuntimeSessionRepository)
    ├── control/                  # task-centric control (single lifecycle authority)
    ├── queue/                    # internal event queue
    ├── common/                   # shared runtime types (AgentRequest / RunStatus /
    │                             #   AgentResponseEvent; old AgentResponse model deleted)
    └── app/                      # RuntimeApp / RuntimeHost / LocalA2aRuntimeHost + RuntimeWiringConfiguration
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
| `com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler` | `runtime.engine.spi` | The single framework-neutral runtime SPI: run one agent, surface its output through concrete adapters such as openJiuwen and AgentScope |
| `com.huawei.ascend.runtime.engine.spi.StreamAdapter` | `runtime.engine.spi` | Adapt a framework's native result stream into the neutral `AgentExecutionResult` stream |
| `com.huawei.ascend.runtime.engine.spi.AgentCardProvider` | `runtime.engine.spi` | Optional A2A Agent Card metadata provider for a runtime-hosted business Agent |
| `com.huawei.ascend.runtime.engine.spi.SetState` | `runtime.engine.spi` | Reserved narrow state-write SPI for frameworks without native checkpointing |
| `com.huawei.ascend.runtime.engine.spi.MemoryProvider` | `runtime.engine.spi` | Reserved narrow memory init/search SPI |

Carrier (NOT an SPI interface):
- `com.huawei.ascend.runtime.engine.spi.AgentExecutionResult` — neutral execution-result carrier.
- Engine dispatch internals live in the `runtime.engine` ROOT (`EngineDispatcher`, `EngineWorker`,
  the command events `EngineCommandEvent` / `EngineCommandGateway`, `EngineEvent` + `EngineEventKind`)
  behind the inbound `engine.api.EngineExecutionApi`; the outbound ports are
  `engine.TaskControlClient` + `engine.AccessLayerClient` (engine root, not `engine.port`) — intra-service, not SPI.

## *L2 Constraint Linkage* (Rule G-1.1.c)

Vacuously green at design phase. The Run-kernel implementation phase (RunStatus
DFA, idempotency claim/replay, suspend/resume durability per L0 §4 #9/#11/#20)
will produce an L2 design; when authored it MUST include Boundary Contracts.

## Deployment loci

`deployment_loci: [platform_centric, business_centric]` — the runtime is
location-agnostic; it supports both loci behind the same engine envelope.

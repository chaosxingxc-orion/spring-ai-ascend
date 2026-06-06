---
level: L1
view: logical
module: agent-runtime
status: neutral-core-runtime
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0160 (neutral execution core: RunCoordinator / AgentDriver / OutputConverter; retires EnginePort / ExecutorAdapter / dual-mode contract mechanism); ADR-0159 (agent-runtime consolidation)"
---

# agent-runtime — L1 architecture (neutral execution core)

> Owner: AgentRuntime team | Plane: compute_control | Maturity: neutral core implemented + e2e verified

## Status

`agent-runtime` is the **run-owning runtime SDK**: the self-contained,
independently-bootable runtime that developers integrate against to drive
Agent instances built on heterogeneous agent frameworks. Per **ADR-0160**
the engine layer is rebuilt on a narrow, neutral I/O seam —
`AgentDriver` / `RunCoordinator` / `OutputConverter` — which replaced the
former `EnginePort` / `ExecutorAdapter` / dual-mode contract mechanism
(retired in ADR-0160). The package root is `com.huawei.ascend.runtime.*`.

### What lives in this module

| Package | Role |
|---|---|
| `runtime.engine.spi` | neutral engine I/O seam: `AgentDriver` (per-framework invoke + stream hook), `AbstractAgentDriver` (base), `OutputConverter` (framework stream → `RunEvent` stream) |
| `runtime.engine` | `RunCoordinator` — wraps one `AgentDriver`, emits `Flow.Publisher<RunEvent>` |
| `runtime.engine.registry` | `AgentDriverRegistry` / `DefaultAgentDriverRegistry` — multi-driver index by agentId + frameworkId |
| `runtime.engine.adapters.openjiuwen` | `OpenJiuwenAgentDriver` + `OpenJiuwenOutputConverter` — in-process openJiuwen ReActAgent adapter |
| `runtime.engine.adapters.dify` | `DifyAgentDriver` + `DifyOutputConverter` — remote Dify REST+SSE adapter; sessionId→conversationId mapping |
| `runtime.common` | neutral value types: `RunEvent`, `RunEventType`, `RunPhase`, `InvocationRequest` |
| `runtime.dispatch` | engine dispatch: `EngineDispatcher` drives `AgentDriverRegistry` → `RunCoordinator` and routes `RunEvent` to access/task-control ports; `AccessLayerClient` / `TaskControlClient` callback ports (`runtime.dispatch.port`) |
| `runtime.access` | A2A protocol access layer (`A2aJsonRpcController`, `A2aWellKnownAgentCardController`, submission + notification ports) |
| `runtime.session` | session management |
| `runtime.taskcontrol` | task-centric control |
| `runtime.queue` | internal event queue |
| `runtime.schema` | runtime schema / response types (`AgentRequest`, `AgentResponse`, `Message`, `Role`, `RunStatus`, `Content`) |
| `runtime.bootstrap` | the bootable runtime application `AgentRuntimeApplication` |

### Neutral execution core (ADR-0160)

The engine layer uses a narrow I/O boundary only:

```
InvocationRequest
  → AgentDriver.invoke()            (any framework, any transport)
  → OutputConverter.convert()       (framework stream → Flow.Publisher<RunEvent>)
  → RunCoordinator.stream()         (emits RunEvent: ACCEPTED / CHUNK / COMPLETED / FAILED / WAITING_INPUT)
  → EngineDispatcher.runDriver()    (routes each RunEvent → AccessLayerClient / TaskControlClient)
```

Every non-cancel dispatch is guaranteed to route exactly one terminal event
(COMPLETED, FAILED, or WAITING_INPUT): a driver stream that errors, times out,
is interrupted, or completes without a terminal `RunEvent` is converted into a
`EngineFailedEvent`, so a run is never left RUNNING after `markRunning`.

### Two adapter shapes

- **In-process framework adapters** (`adapters/openjiuwen`, future: `agentscope-java`, `langchain4j`): embed a Java agent framework, `invoke()` returns the framework's native result object, `OutputConverter` bridges it to `Flow.Publisher<RunEvent>`.
- **Remote protocol adapters** (`adapters/dify`, future: MCP): `invoke()` makes an HTTP call and returns the response body string; `OutputConverter` parses the protocol events (e.g., SSE) into `RunEvent` items.

### Dependency direction

`agent-runtime → agent-bus` (ingress + S2C transport consumed by dispatch/access).
Never `agent-runtime → agent-service`: the serviceization façade is downstream.
`agent-service → agent-runtime` is the only legal cross edge (Rule 10 / ArchUnit).

## 0.4 Layered 4+1 view map

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | neutral execution core + multi-framework adapter registry |
| §2 RunEvent stream | logical | `RunEvent` / `RunEventType` / `RunPhase` — neutral execution events |
| §3 Terminal guarantee | process | EngineDispatcher guarantees exactly one terminal per non-cancel dispatch |

## 1. Role

`agent-runtime` owns, as one self-contained runtime:

- the **neutral execution core** (`AgentDriver` + `RunCoordinator` + `OutputConverter` +
  `RunEvent` stream) — the framework-agnostic I/O seam every adapter implements;
- the **framework adapter registry** (`AgentDriverRegistry`) — multi-driver index by
  agentId and frameworkId; non-null/non-blank validation on registration;
- the **engine dispatcher** (`runtime.dispatch`) — routing accepted Runs to the
  registered `AgentDriver`, collecting `RunEvent` items, guaranteeing a terminal
  event, and forwarding to the access-layer and task-control client ports;
- the **access layer** (`runtime.access`) — A2A protocol ingress that hands work
  to the runtime;
- **session / task-control / internal event queue** scaffolding for run-state
  coordination;
- the **bootable application** (`AgentRuntimeApplication`) — boots the access +
  bootstrap component scan; session, queue, task-control and engine contribute
  through their `AutoConfiguration` imports.

## 2. RunEvent stream

`RunEvent` is the neutral currency of the execution core:

| `RunEventType` | `RunPhase` | Semantics |
|---|---|---|
| `ACCEPTED` | `RUNNING` | driver accepted the request and is processing |
| `CHUNK` | `RUNNING` | incremental output chunk |
| `COMPLETED` | `COMPLETED` | terminal — run succeeded; `content` is the final answer |
| `FAILED` | `FAILED` | terminal — run failed; `error` carries the reason |
| `CHUNK` | `WAITING_INPUT` | terminal (suspension) — run waiting for human input |

## 3. Terminal guarantee (EngineDispatcher)

The `StreamCollection` pattern in `EngineDispatcher.collect()` captures four
end conditions: normal completion, stream error (`onError`), timeout, and thread
interrupt. For each condition, the dispatcher synthesizes the appropriate
`EngineFailedEvent` if no terminal `RunEvent` was already routed. Errors of type
`Throwable` (including `Error` subclasses such as `NoSuchMethodError` from broken
adapter classpaths) are caught; only `VirtualMachineError` is re-thrown.

## 4. Forbidden imports

`com.huawei.ascend.runtime.engine.spi.*` imports only `java.*` plus same-module
common types. No Spring, Micrometer, OTel, or framework-specific imports.
Enforced by `SpiPurityGeneralizedArchTest` (E48).

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. ADR-0160 — neutral core authority; ADR-0159 — consolidation authority.
3. `docs/contracts/contract-catalog.md` — `AgentDriver` + `OutputConverter` SPI rows.
4. `docs/dfx/agent-runtime.yaml` — Design-for-X declarations.

---

## 5. Development View (Rule G-1.1.a)

Current namespace (`com.huawei.ascend.runtime.*`):

```text
agent-runtime/
└── src/main/java/com/huawei/ascend/runtime/
    ├── common/                   # RunEvent, RunEventType, RunPhase, InvocationRequest
    ├── engine/
    │   ├── RunCoordinator.java   # wraps AgentDriver, emits Flow.Publisher<RunEvent>
    │   ├── spi/                  # AgentDriver, AbstractAgentDriver, OutputConverter
    │   ├── registry/             # AgentDriverRegistry, DefaultAgentDriverRegistry
    │   └── adapters/
    │       ├── openjiuwen/       # OpenJiuwenAgentDriver, OpenJiuwenOutputConverter
    │       └── dify/             # DifyAgentDriver, DifyOutputConverter
    ├── dispatch/                 # engine dispatch
    │   ├── dispatch/             #   EngineDispatcher (StreamCollection pattern)
    │   ├── api/                  #   EngineDispatchApi
    │   ├── event/                #   EngineStartedEvent, EngineCompletedEvent, ...
    │   ├── model/                #   EngineExecutionScope, EngineInput, EngineOutput
    │   └── port/                 #   AccessLayerClient, TaskControlClient
    ├── access/                   # A2A protocol ingress
    ├── session/                  # session management
    ├── taskcontrol/              # task-centric control
    ├── queue/                    # internal event queue
    ├── schema/                   # AgentRequest, Message, Role, RunStatus, Content
    └── bootstrap/                # AgentRuntimeApplication
```

## *SPI Interface Appendix* (Rule G-1.1.b)

`agent-runtime` produces two public engine SPI interfaces:

| Interface FQN | SPI package | Purpose |
|---|---|---|
| `com.huawei.ascend.runtime.engine.spi.AgentDriver` | `runtime.engine.spi` | Per-framework I/O-boundary SPI: `invoke()` + stream hook via `OutputConverter` |
| `com.huawei.ascend.runtime.engine.spi.OutputConverter` | `runtime.engine.spi` | Converts framework-native stream into `Flow.Publisher<RunEvent>` |

Internal dispatch ports (NOT externally consumed SPI):
- `AccessLayerClient` — outbound port: engine → access-layer execution events.
- `TaskControlClient` — outbound port: engine → task-centric-control execution events.

## *L2 Constraint Linkage* (Rule G-1.1.c)

Vacuously green at design phase. The Run-kernel implementation phase (RunStatus
DFA, idempotency claim/replay, suspend/resume durability per L0 §4 #9/#11/#20)
will produce an L2 design; when authored it MUST include Boundary Contracts.

## Deployment loci

`deployment_loci: [platform_centric, business_centric]` — the runtime is
location-agnostic; it supports both loci with the same neutral core.

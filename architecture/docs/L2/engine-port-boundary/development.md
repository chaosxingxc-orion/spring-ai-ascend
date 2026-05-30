---
level: L2
view: development
status: active
authority: "ADR-0158 (Engine Boundary / EnginePort) + ADR-0088 (module dependency map) + ADR-0157 (EngineeringFrame ontology)"
relates_to:
  - architecture/docs/L1/agent-bus/ARCHITECTURE.md
  - architecture/docs/L1/agent-execution-engine/ARCHITECTURE.md
  - architecture/docs/L1/agent-service/ARCHITECTURE.md
extends:
  - ADR-0158
---

# EnginePort Boundary — Development View

> L1 names the boundary package (`bus.spi.engine`) and the frame
> (`EF-ENGINE-PORT`). This view carries the L2 detail L1 omits: the concrete type
> inventory of the contract, the three transport-adapter homes inside
> `agent-service`, and the orchestration-SPI re-namespacing migration. None of the
> identifiers below are minted here — each is owned by the contract, the DSL, or a
> generated code fact and is only cited.

## 1. The neutral contract — `bus.spi.engine` (owner: agent-bus)

The boundary identity is the SPI package
`com.huawei.ascend.bus.spi.engine`, a sibling of `bus.spi.ingress` (C2S),
`bus.spi.s2c` (suspend-over-boundary), and `bus.spi.federation` (A2A). It depends
on nothing and is on the path of **both** `agent-service` and
`agent-execution-engine`, which is what keeps the contract neutral and lets the
engine stay extractable with no reverse dependency.

Type inventory (the durable surface — full signatures live in the generated code
facts under `architecture/facts/generated/code-symbols.json`, not here):

| Type | Kind | Role at the boundary |
|---|---|---|
| `EnginePort` | interface | The port: `execute(ExecutionContext, ExecuteRequest) -> Flow.Publisher<AgentEvent>` and `describe() -> EngineDescriptor`. |
| `ExecutionContext` | interface | Neutral engine-facing context: opaque correlation (`runId` / `traceId` / `spanId`) + `Checkpointer`. **No tenant / session.** |
| `RunContext` | interface | Service-side subtype of `ExecutionContext` adding tenant / session + the child-run suspend capability. |
| `ExecuteRequest` | record | Request envelope: `runId`, `engineType`, `definitionRef`, `input`, `startCheckpointRef`, trace + identity refs. |
| `DefinitionRef` | record (`Serializable`) | The wire-form of a definition — a capability name a remote engine resolves to its own `ExecutorDefinition`. |
| `ExecutorDefinition` | sealed type | The runnable definition (`GraphDefinition` \| `AgentLoopDefinition`); carries JVM lambdas, so it never crosses a transport. |
| `DefinitionResolver` | interface | Bidirectional bridge: `resolve(DefinitionRef)` (engine-facing) ↔ `referenceFor(ExecutorDefinition)` (service-facing). |
| `AgentEvent` | sealed type | The event stream element; terminal kinds `Finished` / `Failed` / `InterruptRequest`. |
| `EngineDescriptor` | record | `describe()` result: engine types served + health. |
| `Checkpointer` | interface | Engine-owned opaque checkpoint bytes. |
| `SuspendSignal` | checked exception | The in-process interrupt primitive (carries the `forClientCallback(...)` variant per ADR-0074). |
| `Orchestrator` | interface | The Service-side driver that calls the port. |
| `RunMode` | enum | `GRAPH` \| `AGENT_LOOP` discriminator. |
| `TraceContext` | interface | Trace-id / span carrier. |

The boundary rule that makes this work: an `ExecutorDefinition` cannot travel a
transport (its node / reasoner functions are lambdas); only a `DefinitionRef`
crosses, and the engine resolves it against its own registry. That is why
`execute` takes a `definitionRef`, not an inline definition.

## 2. The engine realization — `agent-execution-engine` (compute_control)

The engine *implements* the port and depends only on the neutral contract, never
on `agent-service`. Homes in `com.huawei.ascend.engine.runtime`:

| Type | Role |
|---|---|
| `InProcessEnginePort` | The in-process `EnginePort` realization (Forms 2/3): resolves `DefinitionRef` via the shared `DefinitionResolver`, runs through `EngineRegistry` strict dispatch, emits exactly one terminal `AgentEvent`. |
| `EngineRegistry` | Strict engine-type matching (ADR-0072, engine-internal). |
| `EngineEnvelope` | Engine-internal dispatch envelope. |
| `EngineOutcomeChannel` | In-JVM stash for a `SuspendSignal` / failure, retrieved by the terminal event's handle. |
| `SingleEventPublisher` | A `Flow.Publisher<AgentEvent>` emitting the one terminal element. |

The reference **executors** (`SequentialGraphExecutor`,
`IterativeAgentLoopExecutor`) belong here too: ADR-0158 REVERSES the ADR-0140
placement — they ARE the engine, not the Service's driving adapters.
`EngineRegistry` / `EngineEnvelope` / `EngineHookSurface` stay engine-internal.

## 3. The transport adapters — `agent-service` (the engine adapter layer)

The Service selects a transport by deployment form. The three `EnginePort`
realizations the Service owns live in
`com.huawei.ascend.service.runtime.orchestration`:

| Adapter | Form | Status | Mechanism |
|---|---|---|---|
| `InProcessEnginePort` (in `agent-execution-engine`, driven by `SyncOrchestrator`) | 2 / 3 | shipped | Direct in-JVM call. |
| `RpcEnginePort` | 1 (engines you own) | design_only | Serialize → round-trip → dispatch over internal RPC (mock-functional until a live wire transport is provisioned). |
| `A2aEnginePort` | 1 (external / federated) | design_only | Drives `bus.spi.federation.FederationGateway`; A2A envelope round-trip (mock-functional). |

Selection key: `app.engine.transport` (`in_process` \| `internal_rpc` \| `a2a`).

Supporting Service-side homes (also `...service.runtime.orchestration`):

- `CompositeDefinitionResolver` — the reference `DefinitionResolver`.
- `SyncOrchestrator` — the reference `Orchestrator` driving the port in-process.
- `RunContextImpl` — the Service-side `RunContext` (adds tenant / session).
- `InMemoryCheckpointer`, `InMemoryRunRegistry` — kept in `agent-service` under the
  Rule 12 posture guard (in-memory reference stores).
- `transport/` mocks — `MockEngineChannel`, `A2aEnvelopeMock`,
  `ReserializingPublisher` — exercise the serialize / round-trip path so the
  networked adapters are functional (not stubs) before a live transport exists.

## 4. Dependency direction (all three forms)

```text
agent-service ──drives──▶ agent-bus (bus.spi.engine) ◀──implements── agent-execution-engine
```

No Maven cycle exists (the reactor forbids engine → service); ADR-0158 also
removes the *semantic* weld by moving tenant / session ownership to the Service
and neutralizing `ExecutionContext`. No new Maven module is created — the six
domain modules are unchanged.

## 5. Orchestration-SPI re-namespacing migration (L2 / sequencing detail)

ADR-0158 re-homes the orchestration SPI from `agent-execution-engine` to
`agent-bus` (`EF-ORCHESTRATION-SPI` is re-homed to `agent-bus` per ADR-0157 /
ADR-0158). The neutral execution model — `Orchestrator`, `ExecutionContext`,
`RunContext`, `SuspendSignal`, `Checkpointer`, `ExecutorDefinition`, `RunMode` —
now lives under `com.huawei.ascend.bus.spi.engine`.

This is the wide-but-mechanical part of the migration (the platform's public SPI
surface totals the 47 in the contract catalog headline; the orchestration types
moving package re-points every referencing import). Sequencing:

1. Land the types in `bus.spi.engine` and re-emit the generated `spi-catalog.dsl`
   + `modules.dsl` byte-identically via AllFragmentsCli (Rule G-13 / R-L). *Owned
   by the lockstep/reconcile wave, not by this view.*
2. Re-point referencing imports `agent-execution-engine.*` → `bus.spi.engine.*`.
   The golden in-process suite (`EnginePortConformanceTck`,
   `NestedDualModeIT`) is the regression guard.
3. Reconcile the two rival `ExecutorAdapter` surfaces under EnginePort on the
   streaming model (`Flow.Publisher<AgentEvent>`, errors as terminal events); the
   losing reactive-push surface is retired.

This migration map is mechanical guidance for the implementation phase. It is NOT
a standing normative clause — the standing authority is the type inventory in §1
and the frame element `efEnginePort` in the DSL.

## 6. Cross-references

- Suspend/resume + error-as-terminal-event mechanics: [`process.md`](process.md).
- Per-form physical placement + checkpoint-store ownership: [`physical.md`](physical.md).
- End-to-end sequences (execute / suspend-over-wire / TCK): [`scenarios.md`](scenarios.md).
- Wire shape: [`docs/contracts/engine-port.v1.yaml`](../../../../docs/contracts/engine-port.v1.yaml).
- Frame: `EF-ENGINE-PORT` in [`architecture/features/engineering-frames.dsl`](../../../features/engineering-frames.dsl).

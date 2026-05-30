---
level: L2
view: process
status: active
authority: "ADR-0158 (Engine Boundary / EnginePort) + ADR-0074 (S2C suspend-over-a-boundary) + ADR-0112 (stateless executor value-based yield)"
relates_to:
  - architecture/docs/L1/agent-bus/ARCHITECTURE.md
  - architecture/docs/L1/agent-service/ARCHITECTURE.md
extends:
  - ADR-0158
---

# EnginePort Boundary — Process View

> The over-the-wire suspend/resume realization is the single highest-risk item in
> ADR-0158, and its normalized view delegates the mechanics here
> (`docs/adr/normalized/ADR-0158.yaml#l2_refs`). This view homes that detail: the
> checkpoint-token protocol, the in-process `SuspendSignal` mapping, and the
> error-as-terminal-event handling — none of which appear at L0 or L1.

## 1. Execution leg — terminal-event contract

`EnginePort.execute(ExecutionContext, ExecuteRequest)` returns a
`Flow.Publisher<AgentEvent>` that emits **exactly one terminal element, last**.
The terminal kinds (per `engine-port.v1.yaml#operations.execute.response`):

| Terminal kind | Meaning | Payload |
|---|---|---|
| `Finished` | The leg completed. | The result value. |
| `Failed` | The leg failed. | An error-class code (`error-class.v1`) + message + a retrieval handle. |
| `InterruptRequest` | The leg suspended. | An opaque checkpoint token + suspend reason + a correlation handle. |

The contract's load-bearing rule: **errors and suspension are terminal events,
never thrown across the boundary.** A thrown exception cannot cross a network
boundary unchanged, so the port normalizes both into stream elements. This is the
behavioral difference from the pre-ADR-0158 in-process `Orchestrator`, which
caught a thrown `SuspendSignal` directly.

## 2. Suspend/resume — the checkpoint-token model

Suspension is a **returned** `InterruptRequest` terminal event, not a parked
thread:

- it carries an opaque **checkpoint token** (`checkpoint-record.v1#engineStateRef`),
- the **suspend reason**, and
- a **correlation handle** (`correlation-record.v1`: `LocalChildHandle` |
  `RemoteAgentHandle`).

Resume is a **fresh `execute`** with `startCheckpointRef` set to the token — a
durable continuation, not a resumed call frame. On resume the `traceparent` MUST
equal the suspending leg's value (`engine-port.v1.yaml#operations.execute.request.traceparent`)
so the resumed leg correlates to the same run.

```text
execute(ctx, req{startCheckpointRef=null})
        │
        ├──▶ Finished(result)                         terminal — done
        ├──▶ Failed(code, msg, handle)                terminal — error class, not a throw
        └──▶ InterruptRequest(token, reason, handle)  terminal — suspended
                     │
                     ▼  (Service persists token against the durable Run row)
        execute(ctx, req{startCheckpointRef=token})   ── fresh leg, same traceparent
                     │
                     └──▶ Finished / Failed / InterruptRequest (may suspend again)
```

`ExecutorDefinition` becomes a serializable `DefinitionRef` precisely so this
fresh-execute resume can cross a JVM: the engine re-resolves the reference against
its own registry instead of receiving an inline lambda.

## 3. Two realizations of the one capability

The checkpoint-token model has two transport realizations that are
**semantically identical** (proven by the cross-transport TCK, see
[`scenarios.md`](scenarios.md) §3):

### 3.1 In-process (Forms 2/3) — `InProcessEnginePort`

The engine still runs the leg synchronously through `EngineRegistry` strict
dispatch. When a node throws the existing checked `SuspendSignal`:

1. `InProcessEnginePort` catches it and stashes it in the in-JVM
   `EngineOutcomeChannel`, which returns a **handle**.
2. It maps the suspension onto a terminal `InterruptRequest(runId, handle,
   "suspend", handle)` — the handle stands in for the checkpoint token in-JVM.
3. A `RuntimeException` becomes `Failed(runId, errorClass, message, handle)`;
   an `EngineMatchingException` becomes `Failed(runId, "engine_mismatch", …)`.

In-process the `ExecutionContext` passed in IS the Service-side `RunContext`
subtype (`RunContextImpl`), so tenant / session are available to node functions
without travelling the boundary. The `SyncOrchestrator` reads the outcome channel
by handle and unwinds the suspension, preserving the legacy in-process behavior
bit-for-bit (the behavior-preserving guarantee for Forms 2/3 every wave).

### 3.2 Over-the-wire (Form 1) — `RpcEnginePort` / `A2aEnginePort`

The networked realization generalizes the **runtime_enforced** `s2c-callback.v1`
protocol — the proven suspend-over-a-boundary mechanism (ADR-0074). There is no
thrown exception to catch; the engine emits `InterruptRequest` carrying a real
serialized checkpoint token, the Service persists it, and resume is a fresh
`execute` with `startCheckpointRef`. The transport mocks (`MockEngineChannel`,
`ReserializingPublisher`, `A2aEnvelopeMock`) exercise the serialize / round-trip
path so the adapters are functional ahead of a live wire transport.

## 4. Checkpoint-store ownership across the boundary

| Concern | Owner |
|---|---|
| Durable Run row + checkpoint-token index | **Service** (`InMemoryRunRegistry` reference; durable store in prod). |
| Opaque checkpoint bytes | **Engine**, via `Checkpointer`. |

Atomicity posture by form:

- **In-process / co-deployed**: shared store → same-transaction atomicity between
  the Run state write and the checkpoint write.
- **Separate microservices (Form 1)**: transactional-outbox for cross-store
  atomicity (no distributed transaction across the boundary).

## 5. Concurrency / threading posture

The port is transport-neutral about threading: the publisher contract
(`Flow.Publisher<AgentEvent>`, one terminal element) is the only concurrency
promise. The in-process realization runs the leg on the caller's thread
(synchronous dispatch + a single-element publisher); networked realizations run it
on the transport's I/O threads. Neither leaks tenant / session onto the boundary,
which is what keeps the contract identical across both.

## 6. Cross-references

- Type inventory + adapter homes: [`development.md`](development.md).
- Per-form placement + outbox atomicity: [`physical.md`](physical.md).
- Suspend-over-wire + resume sequence, and the TCK that proves the two
  realizations agree: [`scenarios.md`](scenarios.md).
- Wire shape: [`docs/contracts/engine-port.v1.yaml`](../../../../docs/contracts/engine-port.v1.yaml)
  (`#suspend_resume`, `#operations.execute`).
- The proven boundary protocol this generalizes:
  [`docs/contracts/s2c-callback.v1.yaml`](../../../../docs/contracts/s2c-callback.v1.yaml).

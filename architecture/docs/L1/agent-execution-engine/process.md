---
level: L1
view: process
status: active
authority: "ADR-0072 (Engine Envelope + Strict Matching); Rule R-M.a (Engine Envelope Single Authority) + Rule R-M.b (Strict Engine Matching)"
---

# `agent-execution-engine` — Process View

> This view narrates engine dispatch at L1 altitude — the boundary policy
> and the failure disposition. The normative, field-level outcome chain
> (how a mismatch terminates a run, the exact reason token) is owned by
> [`engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml)
> and the run-event contract; the per-FunctionPoint sequence is L2
> material.

## Concurrency model

The engine holds no durable state of its own and runs in-process with the
runtime kernel on the compute-control plane. Run state is sourced from the
bus-hosted orchestration SPI (`agent-bus`), not from engine-local fields,
so the engine adds no independent concurrency surface beyond the adapter's
own compute.

## Async/sync boundaries

The engine fires hook points to the middleware chain at its LLM, tool, and
memory boundaries; suspension and resume hooks are fired by the
orchestrator. Suspension crosses into the orchestrator via the checked
`SuspendSignal` carrier (defined in `agent-bus`), not via a return value.

## Execution flow

- **Resolution.** The registry is the single authority that maps an
  envelope to its adapter (Rule R-M.a). Pattern-matching on
  `ExecutorDefinition` subtypes outside the registry is forbidden.
- **Strict matching.** Dispatch is strictly type-matched: a run declares
  one engine type, and the registry admits it only to the adapter
  registered for that type. There is no fallback adapter and no
  reinterpretation of the payload as another engine kind (Rule R-M.b).
- **Matching failure.** On a type mismatch the registry refuses the
  dispatch and the run is driven to its failed state, carrying an
  engine-mismatch reason. The reason vocabulary and the precise transition
  are owned by the run-event and engine-envelope contracts (forward-declared
  L2 migration target: the engine-dispatch frame).
- **Boot self-validation.** At startup the registry validates itself
  against the envelope contract's `known_engines` invariant (every known
  engine has a registered adapter; every registered adapter is known).

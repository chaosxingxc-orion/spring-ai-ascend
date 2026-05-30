---
level: L1
view: process
status: active
authority: "ADR-0073 (Engine Hooks + Runtime Middleware SPI); Rule R-M.c (Runtime-Owned Middleware via Engine Hooks)"
---

# `agent-middleware` — Process View

> This view narrates hook dispatch at L1 altitude — the boundary policy
> and the failure disposition. The normative dispatch ordering and the
> failure-propagation policy are owned by the hook contract
> [`engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml);
> the outcome-to-run-state transition is a forward-declared L2 / contract
> concern.

## Concurrency model

The module is stateless and SPI-pure: it declares contracts and a
dispatcher, and holds no persistence or network endpoint of its own. It
runs in-process with the runtime kernel on the compute-control plane,
adding no independent concurrency surface — listeners execute on the
caller's thread within the engine's own compute.

## Async/sync boundaries

Hook points fired at the engine's LLM, tool, and memory boundaries are
synchronous to the engine call; the suspension and resume hooks are fired
by the orchestrator around the checked `SuspendSignal` boundary (defined
in `agent-bus`). The middleware itself introduces no async handoff.

## Execution flow

- **Dispatch.** Listeners attached to a hook point fire in a declared
  sequence, and the first non-proceeding outcome stops the remaining
  listeners for that hook point (fail-fast). The normative ordering and
  the failure-propagation policy are owned by the hook contract.
- **Error hooks.** Best-effort error hooks run across the chain.
- **Outcome consumption.** Outcome consumption — a failing outcome
  transitioning a run to its failed state, or a short-circuit bypassing
  the engine — is deferred to a later wave and is not wired today (Rule
  R-M sub-clause .c.b). When it lands it is an L2 / contract concern.

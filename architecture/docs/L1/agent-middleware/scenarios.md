---
level: L1
view: scenarios
status: active
authority: "ADR-0073 (Engine Hooks + Runtime Middleware SPI); Rule R-M.c"
---

# `agent-middleware` — Scenarios

> Scenarios are described at L1 altitude. The normative hook list,
> ordering, and failure-propagation policy are owned by
> [`engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml).

## Scenario 1 — a cross-cutting policy injected at a hook point (happy path)

1. The engine reaches one of its boundaries (for example, before invoking
   the LLM) and fires the corresponding hook point.
2. The dispatcher fans the hook point out to its attached listeners in a
   declared sequence.
3. Each listener returns a proceeding outcome, and the engine continues
   past the boundary.

## Scenario 2 — a listener short-circuits the chain (fail-fast)

1. The engine fires a hook point; the dispatcher invokes the attached
   listeners in sequence.
2. A listener returns a non-proceeding outcome (fail or short-circuit).
3. The remaining listeners for that hook point are skipped (fail-fast).
   The run-state effect of the outcome (failing the run, or bypassing the
   engine) is a later-wave concern and is not wired today — see
   [`process.md`](process.md).

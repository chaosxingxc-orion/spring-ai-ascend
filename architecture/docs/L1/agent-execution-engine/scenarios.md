---
level: L1
view: scenarios
status: active
authority: "ADR-0072 (Engine Envelope + Strict Matching); Rule R-M.a + Rule R-M.b"
---

# `agent-execution-engine` — Scenarios

> Scenarios are described at L1 altitude (what happens, which boundary
> owns it). The wire-level request/response detail is owned by
> [`engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml).

## Scenario 1 — dispatch to the matching engine (happy path)

1. A run carries an engine envelope naming its target engine and a typed
   payload reference.
2. The registry resolves the envelope to the adapter registered for that
   engine type (the single resolution authority, Rule R-M.a).
3. The adapter executes, firing hook points to the middleware chain at its
   LLM / tool / memory boundaries.
4. The run advances under the orchestration SPI; engine-local state is not
   retained.

## Scenario 2 — engine-type mismatch (failure path)

1. A run carries an envelope whose engine type has no registered adapter,
   or whose payload does not correspond to the declared engine.
2. The registry refuses the dispatch under strict matching — no fallback,
   no payload reinterpretation (Rule R-M.b).
3. The run is driven to its failed state, carrying an engine-mismatch
   reason; the reason vocabulary is owned by the run-event and
   engine-envelope contracts.

## Scenario 3 — boot-time registry self-validation

1. At startup the registry checks itself against the envelope contract's
   `known_engines` invariant.
2. If a known engine has no adapter (or an adapter is not known), boot
   fails closed rather than admitting an unresolvable engine type later.

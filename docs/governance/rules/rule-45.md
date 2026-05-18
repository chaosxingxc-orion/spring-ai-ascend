---
rule_id: 45
title: "Runtime-Owned Middleware via Engine Hooks"
level: L1
view: development
principle_ref: P-M
authority_refs: [ADR-0073]
enforcer_refs: [E78, E79, E80]
status: active
kernel_cap: 8
kernel: |
  **Cross-cutting policies (model gateway, tool authz, memory governance, tenant policy, quota, observability, sandbox routing, checkpoint, failure handling) MUST be expressed as `RuntimeMiddleware` listening on the canonical `HookPoint` events declared in `docs/contracts/engine-hooks.v1.yaml` (9 hooks: before/after LLM/tool/memory + before_suspension + before_resume + on_error). Engines MUST NOT depend on concrete middleware implementations. Hook ordering is declared (registration order); default failure propagation is fail-fast; `on_error` is best-effort.**
---

## Motivation

Authority: ADR-0073 / P-M. Part of the W2.x Engine Contract Structural Wave. Runtime-owned middleware attaches via engine-declared lifecycle hooks so that cross-cutting policy (observability, quota, sandbox routing, etc.) can be applied uniformly across heterogeneous engines without engines themselves depending on concrete middleware implementations.

## Details

### W2.x scope clarification (post-release review fix plan D / P0-3)

At W2.x the dispatcher fires hooks and middlewares may return `HookOutcome.Fail` / `HookOutcome.ShortCircuit`, but **the orchestrator does NOT consume outcomes** — outcomes are logged. The fail-fast property applies inside the dispatcher chain (a non-`Proceed` outcome stops subsequent middlewares from firing for the same `HookPoint`), NOT to the Run lifecycle. Run-state consumption of outcomes (Fail → `Run.FAILED`, ShortCircuit → engine bypass) is deferred to W2 Telemetry Vertical per `CLAUDE-deferred.md` 45.b — ADR-0073 §Consequences line "Outcomes are LOGGED, NOT acted upon at Phase 2" is the controlling design. `on_error` remains best-effort across the chain.

## Cross-references

- Enforced by Gate Rule 57 (`engine_hooks_yaml_present_and_wellformed` — bidirectional yaml↔HookPoint-enum consistency, enforcer E78), ArchUnit E79 (`EveryEngineDeclaresHookSurfaceTest`), integration test E80 (`RuntimeMiddlewareInterceptsHooksIT`).
- W2.x Phase 2 ships SPI surface only; consumer hooks (TokenCounterHook, PiiRedactionHook, etc.) land in W2 Telemetry Vertical.
- Run-state consumption of outcomes deferred per `CLAUDE-deferred.md` 45.b.
- Schema source: `docs/contracts/engine-hooks.v1.yaml`.
- Companion rule: Rule 48 ([`rule-48.md`](rule-48.md)) — Schema-First Domain Contracts (HookPoint enum is one of the first taxonomies to follow the schema-first shape).

## Deferred sub-clauses

Rule 45.b (see `docs/CLAUDE-deferred.md` for the deferred-runtime obligation(s) and re-introduction trigger(s)). Rule 96 (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.

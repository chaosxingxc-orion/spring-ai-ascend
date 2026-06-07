---
rule_id: R-M
title: "Engine Contract (envelope / matching / hooks / S2C / scope / historical)"
level: L1
view: process
principle_ref: P-M
authority_refs: [ADR-0071, "ADR-0072 (retired)", "ADR-0073 (retired)", "ADR-0074 (retired)", ADR-0075, ADR-0077]
enforcer_refs: [E73, E81, E82, E83, E84, E89, E90, E92, E113]  # E74/E75/E76/E78 removed: retired with the .b/.c envelope/registry/hook design (no current Java type or enforcers.yaml row)
status: active
product_claim: "PC-004"
scope_phase: design
kernel_cap: 8
kernel: |
  **Engine dispatch: `EngineDispatcher` routes each accepted `EngineCommandEvent` to the `AgentRuntimeHandler` registered for its `agentId` (via `AgentRuntimeHandlerRegistry`); an unknown `agentId` converges to a terminal failure with reason `AGENT_ID_INVALID`, and the engine reports every outcome to a single `TaskControlClient` port while `control` is the sole authority that gates caller-facing egress (sub-clause .a — Engine Dispatch). RETIRED (design_only — see `docs/contracts/engine-envelope.v1.yaml`): the pre-rebuild envelope-matching design (`EngineRegistry.resolve(envelope)`, typed `ExecutorAdapter` per `engine_type`, `EngineMatchingException`) has no current Java type (sub-clause .b — Strict Matching, historical). RETIRED (design_only — see `docs/contracts/engine-hooks.v1.yaml`): the pre-rebuild hook-middleware design (cross-cutting policies as `RuntimeMiddleware` listening on `HookPoint` events) has no current Java type (sub-clause .c — Hooks, historical). Server-to-Client capability invocation goes through `S2cCallbackEnvelope` + `S2cCallbackTransport` SPI (under `com.huawei.ascend.bus.spi.s2c`, owned by `agent-bus` per ADR-0088); the waiting Run suspends via `SuspendSignal.forClientCallback(...)` checked variant; client responses validated against `docs/contracts/s2c-callback.v1.yaml` (sub-clause .d — S2C). Every emitted `RunEvent` declares an `EvolutionExport` value (`IN_SCOPE | OUT_OF_SCOPE | OPT_IN`); out-of-scope events MUST NOT be persisted by the evolution plane (sub-clause .e — Evolution Scope). The deleted Java type name `S2cCallbackSignal` MUST appear only inside paragraphs marked historical via tokens listed in `gate/historical-marker-vocabulary.txt` (sub-clause .f — Historical-Only).**
deferred_sub_clauses:
  - id: ".b.b"
    title: "Run.engineType Field Persistence [Deferred to W2]"
    re_introduction_trigger: "first W2+ orchestrator implementation that persists `Run` to Postgres and requires a discriminator column independent of `Run.mode` (target: W2; promoted when a third engine type ships or when `Run.mode` ceases to be 1:1 with engine identity)."
    deferred_body: |
      **Rule (draft)**: Promote `Run.mode` (`GRAPH | AGENT_LOOP`) to a first-class `Run.engineType` field. Flyway migration V3+ adds `engine_type VARCHAR(64) NOT NULL` with a check constraint against the `known_engines[].id` set declared in `docs/contracts/engine-envelope.v1.yaml`. A backfill statement maps `mode='GRAPH' → engine_type='spring_ai_graph_v1'` and `mode='AGENT_LOOP' → engine_type='iterative_agent_loop_v1'` (or whichever ids ship). The Java `Run` record gains `String engineType()` as a non-null accessor; `RunMode` is retained for backward-compatible reads but deprecated. Dispatch routing prefers `Run.engineType` over `Run.mode`.

      Composes with: ARCHITECTURE.md (Run record §source-tree); ADR-0072 §Consequences (the deferral was first declared here); Rule R-M sub-clause .b; Rule R-C.c (Contract Spine Completeness).
    relates_to: ["ADR-0072", "Rule R-M sub-clause .b", "Rule R-C.c"]
  - id: ".b.c"
    title: "Parent-Run Propagation on Child Failure [Deferred to W2]"
    re_introduction_trigger: "first W2 async orchestrator implementation that processes child-run failures asynchronously across JVM-boundary (target: W2 Postgres-backed orchestrator)."
    deferred_body: |
      **Rule (draft)**: When a child Run terminates with `FAILED` (including `engine_mismatch` per Rule R-M sub-clause .b), the parent Run MUST also transition to `FAILED` with a propagated reason that names the failing child (`child_failed:<childRunId>:<originalReason>`). The current `SyncOrchestrator.executeLoop()` only transitions the originating child Run; the parent remains in `SUSPENDED` waiting for a child result that will never arrive. W2 async orchestrator MUST add a parent-propagation hook that fires on every terminal child transition.

      Composes with: Rule R-C.d (Run State Transition Validity); Rule R-M sub-clause .b (Strict Engine Matching); ARCHITECTURE.md §4 #20.
    relates_to: ["Rule R-C.d", "Rule R-M sub-clause .b", "ARCHITECTURE.md §4 #20"]
  - id: ".c.b"
    title: "HookOutcome Run-State Consumption [Deferred to W2 Telemetry Vertical]"
    re_introduction_trigger: "first consumer hook (TokenCounterHook / PiiRedactionHook / CostAttributionHook / LlmSpanEmitterHook) lands in W2 Telemetry Vertical. At that point a real middleware will return `HookOutcome.Fail` / `ShortCircuit` and the orchestrator must consume the outcome."
    deferred_body: |
      **Rule (draft)**: When a middleware returns `HookOutcome.Fail(reason)`, the orchestrator MUST transition the Run to `RunStatus.FAILED` with `finishedAt` set, fire `HookPoint.ON_ERROR` carrying the failure reason, and re-throw a typed exception so the caller observes failure. When a middleware returns `HookOutcome.ShortCircuit(value)`, the orchestrator MUST skip the wrapped engine call and treat `value` as the engine's return. The fail-fast property within the dispatcher chain (already enforced by `HookDispatcher` at W2.x) is preserved.

      Authority: post-release architecture review §P0-3 (plan D); ADR-0073 §Consequences ("Outcomes are LOGGED, NOT acted upon at Phase 2 (W2 wires Fail → Run.FAILED, ShortCircuit → return result)"); CLAUDE.md Rule R-M sub-clause .c W2.x scope clarification paragraph.

      Composes with: Rule R-M sub-clause .c (Runtime-Owned Middleware via Engine Hooks); Rule R-C.d (Run State Transition Validity); ADR-0073.
    relates_to: ["ADR-0073", "Rule R-M sub-clause .c", "Rule R-C.d"]
  - id: ".d.b"
    title: "ResilienceContract s2c.client.callback Wiring [Deferred to W2]"
    re_introduction_trigger: "first production S2C deployment with > 1 concurrent client (target: W2; conditioned on the first non-in-memory `S2cCallbackTransport` implementation shipping)."
    deferred_body: |
      **Rule (draft)**: `ResilienceContract.resolve(tenant, "s2c.client.callback")` MUST consult the `s2c.client.callback` row in `docs/governance/skill-capacity.yaml` at runtime. When per-tenant or global capacity is exhausted, the second concurrent caller MUST be SUSPENDED (Chronos Hydration per Rule R-H) carrying `SuspendReason.RateLimited(S2C_CALLBACK_CAPACITY_EXCEEDED)`, NOT failed. The in-memory transport at W2.x consults the matrix but does not yet enforce it because there is only one client; production transports (webhook, SSE, WebSocket) must enforce on every dispatch.

      **Post-review strengthening (plan G):** Over-capacity skill use MUST suspend **only the dependent step**, NOT the whole run nor unrelated LLM inference threads in the same Run. The W2 orchestrator-admission path is the contract surface: a step blocked on skill capacity is suspended via `SuspendSignal` carrying the step key + skill key; the parent Run stays `RUNNING` so unrelated branches continue. This is the 2D defence net (Tenant Quota × Global Skill Capacity per Rule R-K) applied at sub-Run granularity.

      Composes with: Rule R-M sub-clause .d (S2C Callback Envelope + Lifecycle Bound); Rule R-K (Skill Capacity Matrix); ADR-0074; ADR-0069 / LucioIT W1 §7.3; post-release review §4 skill-capacity orchestration binding.
    relates_to: ["ADR-0074", "ADR-0069", "Rule R-M sub-clause .d", "Rule R-K", "Rule R-H"]
  - id: ".d.c"
    title: "S2C Non-Blocking Lifecycle Promotion [Deferred to W2]"
    re_introduction_trigger: "W2 async orchestrator lands (target: W2 scheduler wave). The synchronous `SyncOrchestrator.handleClientCallback` is replaced by a non-blocking equivalent that suspends the parent Run via the bus and resumes on the response wake-pulse without holding an OS thread."
    deferred_body: |
      **Rule (draft)**: `SyncOrchestrator.handleClientCallback` (or its W2 successor) MUST NOT block the orchestrator thread on the S2C response future. The waiting Run is suspended via the existing `SuspendSignal.forClientCallback(...)` checked-suspension path (already in place as of v2.0.0-rc3 per cross-constraint audit α-2 / β-5), but the *thread* must be released back to the scheduler instead of awaiting `.toCompletableFuture().join()`. Resume happens when the bus delivers the response wake-pulse.

      **Background**: ADR-0074 §Consequences accepted a synchronous W2.x bridge for the SPI. The v2.0.0-rc1 cross-constraint audit (P0-1) noted this directly contradicts Rule R-M sub-clause .d's "MUST suspend, must not block a thread" and Principles P-F/P-G/P-H. The rc3 response: narrow Rule R-M sub-clause .d's prose to acknowledge the W2.x bridge as a deferred exception (this sub-clause); the structural fix lands when the async orchestrator ships.

      **Why deferral, not immediate fix**: A non-blocking S2C resume requires the bus-level wake-pulse machinery that lands in W2 alongside `SuspendSignal` Chronos Hydration (Rule R-H / Rule R-K.c W2 scheduler admission — the rc11-narrowed R-K kernel + rc16 reconciliation per ADR-0093 leave R-K.c as the surviving deferred companion). Retrofitting `SyncOrchestrator` alone would require either (a) reimplementing the wake-pulse in-memory (massive scope creep into W1) or (b) a half-measure that still blocks on a different primitive. Better to land the whole non-blocking story together when the W2 async orchestrator ships.

      Composes with: Rule R-M sub-clause .d (S2C Callback Envelope + Lifecycle Bound); Rule R-H (No Thread.sleep in Business Code); Principle P-F (Cursor Flow); Principle P-G (Absolute Non-Blocking I/O); Principle P-H (Chronos Hydration); ADR-0074 §Consequences.
    relates_to: ["ADR-0074", "ADR-0093", "Rule R-M sub-clause .d", "Rule R-H", "Rule R-K.c", "P-F", "P-G", "P-H"]
  - id: ".legacy17a"
    title: "(legacy Rule 17) Degradation Authority (means-vs-ends boundary)"
    re_introduction_trigger: "first soft-fallback path committed (composes with legacy Rule 7 trigger — W2 LLM gateway)."
    deferred_body: |
      **Rule (means-vs-ends degradation half)**: S-side (system) may substitute means only (alternative tool/model/provider) without C-side (caller) approval. Ends-modification (changing the goal, expanding scope, dropping a required action) is surfaced as a typed `BusinessDegradationRequest` to C-side for explicit approval before proceeding.

      **Note**: The resume re-authorization half of the original legacy Rule 17 (every resume on a `SUSPENDED` Run MUST re-validate `(request.tenantId == Run.tenantId)`; mismatch returns HTTP 403) is NOT carried here — it is already operationalized by Rule R-J sub-clause .b (Cancel Re-Authorization, active, enforcers E105/E106) and its W2 widening to resume/retry per Rule R-J.b.d.

      Composes with: ARCHITECTURE.md §4 #14 (`suspend_reason_taxonomy`); legacy Rule 7 (resilience signal masking, escalated 2026-05-28 to `docs/governance/escalations.md`).
    relates_to: ["ADR-0086", "legacy Rule 17", "Rule R-M sub-clause .d", "Rule R-J sub-clause .b"]
---

# Rule R-M — Engine Contract (envelope / matching / hooks / S2C / scope / historical)

Operationalises across 6 sub-clauses. See `## Sub-clauses` below for the per-sub-clause assertion + enforcer mapping. Authority: [ADR-0071, ADR-0072, ADR-0073, ADR-0074, ADR-0075, ADR-0077].

> **Status (agent-runtime pure rebuild, ADR-0159):** sub-clause **.a** is rewritten to the current `EngineDispatcher` → `AgentRuntimeHandler` dispatch (routes by `agentId`; unknown `agentId` → terminal `AGENT_ID_INVALID`; single `control` write authority). Sub-clauses **.b (Strict Matching)** and **.c (Hooks)** are **RETIRED / design_only** — the `EngineRegistry` / `ExecutorAdapter` / `EngineEnvelope` / `EngineMatchingException` / `HookPoint` / `RuntimeMiddleware` / `HookDispatcher` Java types were removed and `docs/contracts/engine-envelope.v1.yaml` / `engine-hooks.v1.yaml` are `design_only`. The per-sub-clause "Enforced by Gate Rule 55/56/57 … EngineRegistry" notes and the `.b`/`.c` enforcers (E74, E75, E78) below describe that retired design and are historical. Sub-clauses **.d (S2C)**, **.e (Evolution Scope)**, **.f (Historical-Only)** remain active.

## Sub-clauses

### .a — (was sub-clause .a)

## Motivation

Authority: ADR-0072 / P-M. Part of the W2.x Engine Contract Structural Wave that absorbs the 2026-05-15 L0 proposal "Runtime-Engine Contract for Heterogeneous Agent Execution". Follows the wave's structural invariant: every new domain contract ships as `yaml schema → Java type that validates REQUIRED FIELDS on construction → runtime self-validates membership and other invariants at registry boot / dispatch`.

## Cross-references

- RETIRED / historical (sub-clause .a is now `EngineDispatcher` → `AgentRuntimeHandler`): the envelope/registry design was formerly enforced by Gate Rule 55 (`engine_envelope_yaml_present_and_wellformed`, E76) + ArchUnit E74 (`EnginePayloadDispatchOnlyViaRegistryTest`). No current `EngineRegistry` Java type; `engine-envelope.v1.yaml` is `design_only`. Current dispatch is verified by `EngineDispatcherTest` / `EngineClosedLoopIntegrationTest`.
- Strict construction-time membership validation for `EngineEnvelope` deferred to Rule M-2 sub-clause .a.c (re-introduction trigger: first envelope built outside the Spring-boot test harness).
- Schema source: `docs/contracts/engine-envelope.v1.yaml`.
- Companion rule: Rule R-M sub-clause .b ([`rule-R-M.md`](rule-R-M.md)) — Strict Engine Matching.
- Companion rule: Rule M-2 sub-clause .a ([`rule-M-2.md`](rule-M-2.md)) — Schema-First Domain Contracts (the cross-cutting invariant Rule R-M sub-clause .a instantiates).

### .b — (was sub-clause .b)

## Motivation

Authority: ADR-0072 / P-M. Part of the W2.x Engine Contract Structural Wave that absorbs the 2026-05-15 L0 proposal "Runtime-Engine Contract for Heterogeneous Agent Execution". Strict matching prevents silent reinterpretation of engine-specific payloads — the silent-reinterpretation failure mode is the most dangerous one in heterogeneous-engine systems because it surfaces as undefined behaviour rather than a crisp error.

## Cross-references

- RETIRED / historical: strict matching was formerly enforced by Gate Rule 56 (`engine_registry_covers_all_known_engines`, E77) + integration test E75 (`EngineMatchingStrictnessIT`). No current `EngineRegistry` / `ExecutorAdapter` / `EngineMatchingException` Java type.
- Additional enforcer E88 (W2.x post-release closure work) tightens registry-boot validation.
- Companion rule: Rule R-M sub-clause .a ([`rule-R-M.md`](rule-R-M.md)) — Engine Envelope Single Authority.
- Companion rule: Rule R-C.2 sub-clause .b ([`rule-R-C.2.md`](rule-R-C.2.md)) — Run State Transition Validity (was Rule R-C.d pre-rc17 per ADR-0094; `engine_mismatch` is a legal RUNNING → FAILED transition).
- Deferred sub-clauses: Rule R-M sub-clause .b.b (Run.engineType field persistence), Rule R-M sub-clause .b.c (parent-run propagation on child failure) — see `docs/CLAUDE-deferred.md`. Rule G-3 sub-clause .d (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.

### .c — (was sub-clause .c)

## Motivation

Authority: ADR-0073 / P-M. Part of the W2.x Engine Contract Structural Wave. Runtime-owned middleware attaches via engine-declared lifecycle hooks so that cross-cutting policy (observability, quota, sandbox routing, etc.) can be applied uniformly across heterogeneous engines without engines themselves depending on concrete middleware implementations.

## Details

### W2.x scope clarification (post-release review fix plan D / P0-3)

At W2.x the dispatcher fires hooks and middlewares may return `HookOutcome.Fail` / `HookOutcome.ShortCircuit`, but **the orchestrator does NOT consume outcomes** — outcomes are logged. The fail-fast property applies inside the dispatcher chain (a non-`Proceed` outcome stops subsequent middlewares from firing for the same `HookPoint`), NOT to the Run lifecycle. Run-state consumption of outcomes (Fail → `Run.FAILED`, ShortCircuit → engine bypass) is deferred to W2 Telemetry Vertical per `CLAUDE-deferred.md` 45.b — ADR-0073 §Consequences line "Outcomes are LOGGED, NOT acted upon at Phase 2" is the controlling design. `on_error` remains best-effort across the chain.

## Cross-references

- RETIRED / historical: the hook surface was formerly enforced by Gate Rule 57 (`engine_hooks_yaml_present_and_wellformed`, E78), ArchUnit E79 (`EveryEngineDeclaresHookSurfaceTest`), integration test E80 (`RuntimeMiddlewareInterceptsHooksIT`). No current `HookPoint` / `RuntimeMiddleware` / `HookDispatcher` Java type; `engine-hooks.v1.yaml` is `design_only`.
- W2.x Phase 2 ships SPI surface only; consumer hooks (TokenCounterHook, PiiRedactionHook, etc.) land in W2 Telemetry Vertical.
- Run-state consumption of outcomes deferred per `CLAUDE-deferred.md` 45.b.
- Schema source: `docs/contracts/engine-hooks.v1.yaml`.
- Companion rule: Rule M-2 sub-clause .a ([`rule-M-2.md`](rule-M-2.md)) — Schema-First Domain Contracts (HookPoint enum is one of the first taxonomies to follow the schema-first shape).

## Deferred sub-clauses

Rule R-M sub-clause .c.b (see `docs/CLAUDE-deferred.md` for the deferred-runtime obligation(s) and re-introduction trigger(s)). Rule G-3 sub-clause .d (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.

### .d — (was sub-clause .d)

## Motivation

Authority: ADR-0074 / P-M. Part of the W2.x Engine Contract Structural Wave. Server-to-client capability invocation is an explicit asynchronous protocol bound to the suspend/resume loop — the platform's "server-sovereign" boundary requires that S2C callbacks travel through a typed envelope and that the waiting Run participates in the same SUSPENDED/RUNNING state machine as every other long-horizon wait. The v2.0.0-rc3 cross-constraint audit unified the original S2cCallbackSignal into SuspendSignal as a checked-suspension variant so that ADR-0019's compile-time-visible-suspension doctrine remains a single source of truth.

## Details

### Envelope-propagation matrix

The Phase 3a cross-rule co-design audit matrix in [`docs/logs/reviews/2026-05-16-engine-contract-structural-response.en.md`](../../logs/reviews/2026-05-16-engine-contract-structural-response.en.md) §5 is the canonical reference for how this rule interlocks with Rules 20/35/38/41/42 — six mandatory request fields (callback_id, server_run_id, capability_ref, request_payload, trace_id, idempotency_key) propagate at every layer to prevent the Class-3 envelope-propagation gap (fourteenth-cycle SpawnEnvelope 11-dim precedent).

## Cross-references

- Enforced by Gate Rule 58 (`s2c_callback_yaml_present_and_wellformed`, enforcer E81), integration test E82 (`S2cCallbackRoundTripIT`), ArchUnit E83 (`S2cCallbackRespectsRule38Test` — no Thread.sleep in s2c..).
- Additional enforcers E89, E90, E92 cover post-release closure work including SuspendSignal sealed-checked-variant unification and the s2c.spi package move.
- Runtime ResilienceContract integration for `s2c.client.callback` skill capacity deferred to Rule R-M sub-clause .d.b (W2) per ADR-0074 §Consequences and `docs/CLAUDE-deferred.md` 46.b.
- Deferred sub-clauses (canonical names — aligned with `docs/CLAUDE-deferred.md` 2026-05-19 rc9 wave): Rule R-M sub-clause .d.b / legacy 46.b (`ResilienceContract s2c.client.callback` runtime admission wiring — W2), Rule R-M sub-clause .d.c / legacy 46.c (non-blocking lifecycle for the W2.x synchronous bridge — W2 async orchestrator). Invalid-response handling itself is **already shipped at L1.x** through the kernel's `BEFORE-resume` validation clause and is enforced by `S2cCallbackEnvelopeValidationTest` (enforcer E89) — it is not a deferred sub-clause.
- Schema source: `docs/contracts/s2c-callback.v1.yaml`.
- Inter-rule cross-citations: Rule R-C.2 sub-clause .b (was Rule R-C.d pre-rc17 per ADR-0094; `SuspendReason.AwaitClientCallback` as a legal RUNNING → SUSPENDED transition), Rule R-E (S2C traffic rides the `data` channel by default with control intents on `control`), Rule R-H (no Thread.sleep — checked-suspension variant), Rule R-K (`s2c.client.callback` skill capacity), Rule R-L (logical-to-physical authority discipline at the callback boundary).

### .e — (was sub-clause .e)

## Motivation

Authority: ADR-0075 / P-M. Part of the W2.x Engine Contract Structural Wave. The evolution mechanism manages only server-controlled execution scope by default — production agent runs must not silently feed an evolution / ML training plane without explicit declaration. The discriminator (IN_SCOPE | OUT_OF_SCOPE | OPT_IN) makes that declaration a first-class type-level decision rather than a runtime configuration knob.

## Cross-references

- Enforced by Gate Rule 59 (`evolution_scope_yaml_present_and_wellformed` — 3-discriminator-block + telemetry-export-ref schema check, enforcer E86) and ArchUnit E87 (`EveryRunEventDeclaresEvolutionExportTest`, armed-empty until W2 RunEvent variants ship).
- Schema source: `docs/governance/evolution-scope.v1.yaml`.
- Future contract placeholder: `telemetry-export.v1.yaml` (W3, referenced at `evolution-scope.v1.yaml#opt_in_export.contract_required`).
- Companion rule: Rule M-2 sub-clause .a ([`rule-M-2.md`](rule-M-2.md)) — Schema-First Domain Contracts (EvolutionExport is a domain enum that must obey schema-first shape).
- Related architecture: ADR-0022 (RunEvent variants ship in W2).

### .f — (was sub-clause .f)

# Rule R-M sub-clause .f — S2cCallbackSignal Historical-Only in Authority

## Motivation

The v2.0.0-rc3 cross-constraint audit (alpha-2 / beta-5, 2026-05-17) deleted the parallel unchecked `S2cCallbackSignal` `RuntimeException` subtype and unified the executor-side S2C trigger into the checked-suspension variant `SuspendSignal.forClientCallback(callbackId, envelope)`. This preserves ADR-0019's compile-time-visible-suspension doctrine as a single source of truth — there is no longer a parallel `RuntimeException` hierarchy for suspension semantics.

The 2026-05-18 rc4 cross-constraint architecture review (`docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md` finding P0-1) found that the rc3 refactor swept `CLAUDE.md`, Rule R-M sub-clause .d, `SuspendSignal.java`, `SyncOrchestrator.java`, `agent-service/ARCHITECTURE.md`, and `architecture-status.yaml#allowed_claim`, but did NOT sweep:

- `docs/adr/0074-s2c-capability-callback.yaml` (still described the unchecked design as the current ship)
- `docs/contracts/s2c-callback.v1.yaml` (still said `SyncOrchestrator` catches `S2cCallbackSignal`)
- `docs/governance/enforcers.yaml` row E82 (still described the deleted exception path)

Because ADR-0074 is `status: accepted`, a downstream engine or transport implementer reading the ADR could legitimately re-introduce the unchecked exception path, contradicting Rule R-M sub-clause .d and the actual Java SPI. This is a direct authority conflict — not a cosmetic stale comment. ADR-0074 was amended in place on 2026-05-18 with a top-level `amendments:` block; this rule freezes that closure as a permanent invariant so the next drift wave cannot reopen it.

## Details

### Scanned files

Rule R-M sub-clause .f's gate scans the following corpus for the literal token `S2cCallbackSignal`:

- `docs/adr/*.yaml` and `docs/adr/*.md` where `status:` is one of `accepted | proposed | superseded` (any active state).
- `CLAUDE.md` (root) and `docs/CLAUDE-deferred.md`.
- `README.md` (root) and `agent-*/README.md` per-module READMEs.
- `agent-*/ARCHITECTURE.md` per-module L1 architecture documents.
- `docs/contracts/*.v1.yaml` schema contracts (including yaml comment blocks).

### Historical-marker regex

A `S2cCallbackSignal` mention is admissible only when one of the following markers appears in the same paragraph (markdown) or within a `±5` line window (yaml + multi-line markdown blocks):

```
historical | deleted | refactored from | rc3-unification | amendments
```

Markers are case-insensitive. Yaml comment blocks (`# ...`) and yaml block scalars (`|`, `>`) are scanned as text. The `amendments:` key in ADR yaml front-matter is recognised as a structural marker — anything inside its block scalar is automatically admissible.

### Failure mode

Live current-state claims like `SyncOrchestrator catches S2cCallbackSignal` or `the S2C transport throws S2cCallbackSignal` outside a historical paragraph fail the gate with a finding pointing to the violating file:line range. The remediation is to rewrite the paragraph in terms of `SuspendSignal.forClientCallback(...)` and `isClientCallback()`, optionally with a parenthetical historical note.

## Activation

Activated 2026-05-18 by the v2.0.0-rc4 cross-constraint architecture review response wave. Enforcer E113. Closes P0-1 of `docs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md`.

## Cross-references

- ADR-0074 — accepted ADR for the S2C capability callback protocol. The `amendments:` block (2026-05-18) records the rc3 unification and is the authoritative narrative; Rule R-M sub-clause .f protects it against re-introduction drift.
- Rule R-M sub-clause .d (S2C Callback Envelope + Lifecycle Bound) — substantive rule defining the SPI surface; Rule R-M sub-clause .f is the corpus-text-truth complement that prevents Rule R-M sub-clause .d's prose from being contradicted by stale authority.
- Rule G-2 sub-clause .a (Architecture-Text Truth) — Rule R-M sub-clause .f specialises Rule G-2 sub-clause .a to the single deleted Java identifier; Rule G-2 sub-clause .a keeps the general invariant.
- `docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md` finding P0-1 — origin of the rule.
- `docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md` finding P0-1 — origin of the rule.
- ADR-0019 — compile-time-visible-suspension doctrine that the rc3 unification protects.

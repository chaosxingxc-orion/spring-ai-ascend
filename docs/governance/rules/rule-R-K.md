---
rule_id: R-K
title: "Skill Capacity Matrix"
level: L1
view: physical
principle_ref: P-K
authority_refs: [ADR-0069, "ADR-0070 (retired)", ADR-0085]
enforcer_refs: [E70, E73]
status: active
scope_phase: design
kernel_cap: 8
kernel: |
  **`docs/governance/skill-capacity.yaml` MUST exist and declare, per skill, both `capacity_per_tenant` and `global_capacity` fields plus a `queue_strategy` (`suspend` or `fail`). The runtime `ResilienceContract.resolve(tenant, skill)` MUST consult this matrix; over-capacity resolution MUST return `SkillResolution.reject(SuspendReason.RateLimited)` rather than admit-or-fail. The actual `Run`/dependent-step suspension transition is deferred to Rule R-K.c (W2 scheduler admission). Chronos Hydration interlock with Rule R-H.**
deferred_sub_clauses:
  - id: ".c"
    title: "Run/Step Suspension Transition [Deferred to W2]"
    re_introduction_trigger: "first W2 async orchestrator implementation that consumes a `SkillResolution.reject(SuspendReason.RateLimited)` and emits a corresponding `Run.withSuspension(...)` (or dependent-step suspension) transition. Conditioned on the same trigger as Rule R-M sub-clause .d.c (W2 async orchestrator landing)."
    deferred_body: |
      **Rule (draft)**: When `ResilienceContract.resolve(tenant, skill)` returns `SkillResolution.admitted = false` with `SuspendReason.RateLimited(SKILL_CAPACITY_EXCEEDED)`, the W2 orchestrator MUST translate the rejection into an actual `RunStatus.SUSPENDED` transition (per Rule R-C.d state-machine validity) on the dependent step that owns the skill call. The parent `Run` stays `RUNNING` so unrelated sub-branches continue (sub-Run granularity — composes with Rule R-M sub-clause .d.b "post-review strengthening"). The suspension MUST carry the `SuspendReason.RateLimited` payload into the persisted `Run.suspendReason` field so observability surfaces the saturating skill key. Failure to translate the rejection is a ship-blocking defect under Rule D-5.

      **Background**: At W1.x scope, the active Rule R-K kernel only commits to the decision-envelope behaviour — `ResilienceContract.resolve` returns a `SkillResolution` carrying the reason, and the caller (today: no W2 orchestrator yet) is responsible for the actual `Run` transition. ADR-0070 §Consequences explicitly notes: *"Run-row carries no suspendReason field — the reason lives on SkillResolution. W2 orchestrator wiring will add Run.suspendReason when it actually transitions runs to SUSPENDED."* The rc10 post-corrective architecture review (finding P1-1) flagged the W1.x Rule R-K kernel for overclaiming end-state behaviour; rc11 narrows the kernel and lands Rule R-K.c as the deferred companion.

      **Why deferral, not immediate fix**: The runtime translation requires the W2 async orchestrator's bus-level suspension primitive (`SuspendSignal` Chronos Hydration per Rule R-H) plus a durable `Run.suspendReason` column (Flyway V3+). Retrofitting `SyncOrchestrator` alone would either (a) require reimplementing the bus wake-pulse in-memory (massive W1 scope creep) or (b) emit a half-measure that block-waits on a different primitive. Better to land the whole non-blocking story together when the W2 async orchestrator ships — same logic as Rule R-M sub-clause .d.c (S2C non-blocking lifecycle promotion).

      Composes with: Rule R-K (Skill Capacity Matrix); Rule R-C.d (Run State Transition Validity); Rule R-H (Chronos Hydration); Rule R-M sub-clause .d.b ("post-review strengthening" — sub-Run granularity for skill saturation, identical pattern); Rule R-M sub-clause .d.c (W2 async orchestrator landing — shared trigger); ADR-0070 §Consequences; ADR-0085 (rc11 corpus-truth wave authority).
    relates_to: ["ADR-0070", "ADR-0085", "Rule R-K", "Rule R-C.d", "Rule R-H", "Rule R-M sub-clause .d.b", "Rule R-M sub-clause .d.c", "Rule D-5"]
  - id: ".legacy16"
    title: "(legacy Rule 16) Cognitive Resource Arbitration"
    re_introduction_trigger: "first `ResilienceContract` consumer that invokes an external tool or skill (not just LLM) (target: W2)."
    deferred_body: |
      **Rule**: Every skill invocation MUST declare:
      - (a) `operationId` in `skill:<name>` namespace.
      - (b) Tenant-scoped quota key (prevents one tenant from exhausting shared capacity).
      - (c) Global skill capacity key (caps concurrent invocations platform-wide).
      - (d) Saturation policy: skill-full suspends the Run (`SUSPENDED + suspendedAt + reason=RateLimited`), not fails it.
      - (e) Call-tree budget: parent Run's remaining token/cost budget is propagated through `RunContext` to child Runs.

      Composes with: ARCHITECTURE.md §4 #12 (`skill_capacity_matrix`, `call_tree_budget_propagation`); legacy Rule 13 (P1 cost-of-use, escalated 2026-05-28).
    relates_to: ["ADR-0070", "ADR-0086", "legacy Rule 16", "Rule R-K"]
---

## Motivation

The L0 motivation (LucioIT W1 §7.3): a single high-frequency skill (slow external API) can exhaust the cluster's connection pool and CPU. The 2D defence net (Tenant Quota × Global Skill Capacity) lets the scheduler suspend only the Agent processes blocked on that specific skill, leaving lightweight reasoning tasks free to proceed on freed OS threads.

## What the active kernel guarantees vs. what it defers

The kernel was narrowed in v2.0.0**decision envelope** (`SkillResolution.reject(SuspendReason.RateLimited)`), not a `Run` state transition. The rc11 narrowing separates two obligations:

- **Active (Rule R-K kernel)** — schema presence + runtime resolver consults matrix + over-capacity returns the right decision envelope. Asserted today by `SkillCapacityResolutionIT.rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne` (W1.x Phase 9, enforcer E73, gate Rule 54; method renamed in rc15 per ADR-0091 to remove terminal-state overclaim).
- **Deferred (Rule R-K.c — W2 scheduler admission)** — translating the rejected `SkillResolution` into an actual `Run`/dependent-step `SUSPENDED` transition. Re-introduction trigger: first W2 async orchestrator that consumes `SkillResolution.reject(...)` and emits a `Run.withSuspension(...)` transition.

## Cross-references

- Enforced by Gate Rule 51 (`skill_capacity_yaml_present_and_wellformed`) — schema check.
- Enforced by Gate Rule 54 (`skill_capacity_runtime_resolver_present`) — runtime envelope behaviour (`SkillResolution.reject(SuspendReason.RateLimited)` on over-capacity).
- Architecture reference: ADR-0069 / LucioIT W1 §7.3.
- Runtime enforcement activated in W1.x Phase 9 (`SkillCapacityResolutionIT.rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne`, enforcer E73, gate Rule 54 per ADR-0070; method renamed in rc15 per ADR-0091); the original 41.b/R-K.b deferral closed — R-K.c is the surviving deferred clause (W2 scheduler admission).
- **Rule R-K.c** (deferred to W2 per `docs/CLAUDE-deferred.md`) — Run/Step Suspension Transition: maps the rejected `SkillResolution` to a `Run.SUSPENDED` transition in the W2 orchestrator.
- Cross-cited by Rule R-M sub-clause .d ([`rule-R-M.md`](rule-R-M.md)) envelope-propagation matrix — S2C callbacks consume the `s2c.client.callback` skill capacity.
- Companion rule: Rule R-H ([`rule-R-H.md`](rule-R-H.md)) — No Thread.sleep in Business Code (Chronos Hydration interlock).

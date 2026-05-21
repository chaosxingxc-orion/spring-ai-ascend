---
rule_id: R-K
title: "Skill Capacity Matrix"
level: L1
view: physical
principle_ref: P-K
authority_refs: [ADR-0069, ADR-0070, ADR-0085]
enforcer_refs: [E70, E73]
status: active
scope_phase: design
kernel_cap: 8
kernel: |
  **`docs/governance/skill-capacity.yaml` MUST exist and declare, per skill, both `capacity_per_tenant` and `global_capacity` fields plus a `queue_strategy` (`suspend` or `fail`). The runtime `ResilienceContract.resolve(tenant, skill)` MUST consult this matrix; over-capacity resolution MUST return `SkillResolution.reject(SuspendReason.RateLimited)` rather than admit-or-fail. The actual `Run`/dependent-step suspension transition is deferred to Rule R-K.c (W2 scheduler admission). Chronos Hydration interlock with Rule R-H.**
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

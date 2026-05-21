---
principle_id: P-K
title: "Skill-Dimensional Resource Arbitration"
level: L0
view: physical
authority: "Layer 0 governing principle (CLAUDE.md); LucioIT W1 L0 §6-§7"
enforced_by_rules: [R-K]   # formerly Rule 41 (numeric pre-ADR-0086)
kernel: |
  P-K — Skill-Dimensional Resource Arbitration.
  A 2D defence net — Tenant Quota × Global Skill Capacity — protects the
  cluster.
  When a skill capacity pool fills, the scheduler suspends only the Agent
  processes blocked on that specific skill,
  freeing OS threads for unrelated work.
  Enforced by Rule R-K.
scope_phase: design
---

## Motivation

This principle exists because **a single high-frequency skill (a slow external API, a saturated model endpoint, an over-subscribed vector DB) can exhaust the cluster's connection pool and CPU** even when only one tenant is misbehaving — and tenant-quota alone is insufficient because the global pool is the bottleneck. The 2D net composes both dimensions: tenant quota stops one tenant from monopolising globally, global skill capacity stops one skill from monopolising across tenants. When a skill pool fills, the scheduler **suspends** (Chronos Hydration interlock with P-H) only the agents blocked on that specific skill, leaving lightweight reasoning tasks free to proceed on freed OS threads. This is the operational expression of "never block, always suspend".

## Operationalising rules

- Rule R-K — Skill Capacity Matrix ([`docs/governance/rules/rule-R-K.md`](../rules/rule-R-K.md))

## Cross-references

- ADR-0069 (origin of Rules 35–42 and the LucioIT W1 §7.3 skill-capacity doctrine)
- Matrix source of truth: [`docs/governance/skill-capacity.yaml`](../skill-capacity.yaml)
- Rule R-K kernel activated in W1.x Phase 9 (`SkillCapacityResolutionIT.rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne`, enforcer E73, gate Rule 54) per ADR-0070; method renamed in rc15 per ADR-0091 to remove terminal-state overclaim; the original 41.b/R-K.b deferral closed — R-K.c (Run/Step Suspension Transition) is the surviving deferred clause (W2 scheduler admission)
- Related: P-H (Chronos Hydration) — suspension is the mechanism by which capacity overflow becomes non-blocking
- Related: Rule R-M sub-clause .d (S2C Callback) — `s2c.client.callback` skill capacity is declared in this matrix

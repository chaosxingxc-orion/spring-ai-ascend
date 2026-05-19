---
rule_id: R-B
title: "Competitive Baselines Required"
level: L0
view: scenarios
principle_ref: P-B
authority_refs: [ADR-0065]
enforcer_refs: [E50, E51]
status: active
kernel_cap: 8
kernel: |
  **Every release MUST publish `docs/governance/competitive-baselines.yaml` declaring four pillar dimensions — `performance`, `cost`, `developer_onboarding`, `governance` — each with a named `baseline_metric` and a `current_value` (or `N/A` for not-yet-instrumented). The most recent `docs/logs/releases/*.md` release note MUST mention all four pillar names. A regression in any `current_value` MUST be paired with a `regression_adr:` reference in the row.**
---

## Motivation

Rule R-B is the in-repo enforceable expression of governing principle P-B (Four Competitive Pillars). Without published baselines, "we got faster" and "we got cheaper" are unfalsifiable claims that decay across releases. By forcing every release to declare a current value (even `N/A` for not-yet-instrumented dimensions) and to acknowledge regressions with an ADR reference, the rule converts pillar competitiveness into a versioned series that future releases can be measured against.

## Details

Enforced by Gate Rule R-D sub-clause .a (`competitive_baselines_present_and_wellformed`) and Gate Rule G-1 sub-clause .a (`release_note_references_four_pillars`).

## Cross-references

- ADR-0065 — origin decision record.
- P-B — governing principle Rule R-B operationalises.
- Architecture reference: §4 #61.
- Deferred sub-clauses 30.b (git-diff regression-ADR pairing) and 30.d (measurement automation, W2/W3 trigger).
- [`docs/governance/competitive-baselines.yaml`](../competitive-baselines.yaml) — the artefact this rule polices.

## Deferred sub-clauses

Rule R-B.b, Rule R-B.d (see `docs/CLAUDE-deferred.md` for the deferred-runtime obligation(s) and re-introduction trigger(s)). Rule G-3 sub-clause .d (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.

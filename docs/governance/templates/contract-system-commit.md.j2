---
level: L0
view: scenarios
scope_phase: commit
status: active
authority: "ADR-0098 (rc21 — 6-phase scenario-loaded contracts)"
---

# Phase Contract — System Commit

## When you enter this phase

You are about to:

- Author a release note under `docs/logs/releases/`.
- Author an ADR under `docs/adr/` that closes a release.
- Run the pre-commit checklist (Rule D-3.a) — smoke + lint on touched
  modules.
- Lockstep-update baseline surfaces (per `feedback_lockstep_baseline_surfaces.md`):
  `architecture-status.yaml#baseline_metrics` + `allowed_claim` +
  README baseline line + freeze prior release note with SHA marker.
- Refresh the recurring-defect-families ledger (Rule G-9 + the
  `/refresh-defect-archive` skill).
- Open or finalize a PR; write the merge commit message.

Invoke `/commit-mode` (Wave 3) at phase entry. Until Wave 3 ships, Read
this file directly before starting.

## Active rules — commit phase

Markers: **P** = primary · **X** = cross-reference.

| Rule | Title | Marker | Card |
|---|---|---|---|
| D-1 | Root-Cause + Strongest-Interpretation Before Plan | **X** | [`rule-D-1.md`](../rules/rule-D-1.md) |
| D-3 | Pre-Commit Checklist + Evidence-First Debug | **X** | [`rule-D-3.md`](../rules/rule-D-3.md) |
| D-4 | Three-Layer Testing, With Honest Assertions | **X** | [`rule-D-4.md`](../rules/rule-D-4.md) |
| D-5 | Self-Audit is a Ship Gate, Not a Disclosure | **P** | [`rule-D-5.md`](../rules/rule-D-5.md) |
| D-9 | No Version / Log Metadata in Code | **X** | [`rule-D-9.md`](../rules/rule-D-9.md) |
| G-1 | Layered 4+1 Discipline + Architecture-Graph Truth | **X** | [`rule-G-1.md`](../rules/rule-G-1.md) |
| G-2 | Authority-Text Reality (doc / status / path / numeric truth) | **P** | [`rule-G-2.md`](../rules/rule-G-2.md) |
| G-2.1 | Deleted-Module Scope Prevention | **P** | [`rule-G-2.1.md`](../rules/rule-G-2.1.md) |
| G-3 | Kernel-Card-Implementation Coherence | **P** | [`rule-G-3.md`](../rules/rule-G-3.md) |
| G-3.1 | Kernel-Implementation Disjunction Truth | **P** | [`rule-G-3.1.md`](../rules/rule-G-3.1.md) |
| G-4 | Always-Loaded Context Budget | **X** | [`rule-G-4.md`](../rules/rule-G-4.md) |
| G-5 | Gate Self-Consistency (parity / coverage / manifest / freshness) | **X** | [`rule-G-5.md`](../rules/rule-G-5.md) |
| G-6 | Gate Machinery Integrity (duration + config) | **X** | [`rule-G-6.md`](../rules/rule-G-6.md) |
| G-7 | Linux-First Dev Environment | **X** | [`rule-G-7.md`](../rules/rule-G-7.md) |
| G-8 | Cross-Authority Parity (graph baseline / SPI path / module topology / current-claim grammar / structural-carrier parity) | **P** | [`rule-G-8.md`](../rules/rule-G-8.md) |
| G-9 | Recurring-Defect Family Truth | **P** | [`rule-G-9.md`](../rules/rule-G-9.md) |
| G-10 | Parallel-Linux-Scripts Mandate | **X** | [`rule-G-10.md`](../rules/rule-G-10.md) |
| G-11 | Phase-Contract Rule-Allocation Coherence | **P** | [`rule-G-11.md`](../rules/rule-G-11.md) |
| G-13 | Single-Source Rendering Coherence | **P** | [`rule-G-13.md`](../rules/rule-G-13.md) |
| M-2 | Domain Contract Discipline (schema-first + design-only registration + DFX-stem truth) | **X** | [`rule-M-2.md`](../rules/rule-M-2.md) |
| R-B | Competitive Baselines Required | **P** | [`rule-R-B.md`](../rules/rule-R-B.md) |
| G-19 | Auto-Load Tier Integrity | **P** | [`rule-G-19.md`](../rules/rule-G-19.md) |

## Forbidden patterns (this phase)

- Committing without smoke + lint on touched modules when the change
  touches server entry points, runtime adapters, or dependency-wiring
  modules (Rule D-3.a).
- Splitting baseline lockstep update across multiple commits — the 4
  surfaces (`baseline_metrics`, `allowed_claim`, README baseline line,
  prior release note SHA freeze) MUST land in ONE commit per the rc17
  lockstep lesson.
- Writing a release note that contains absolute graph node/edge counts
  inconsistent with `architecture-status.yaml#baseline_metrics` without
  marking the paragraph historical (Rule G-2.g).
- Committing when self-audit has open findings in a downstream-correctness
  category (Rule D-5).
- Bypassing branch protection or pre-commit / pre-push hooks (Rule G-7,
  Rule D-3.a).
- Authoring a release note without all four Rule R-B competitive pillar
  names + values.

## Exit criteria

- Pre-commit checklist green (smoke + lint on touched modules).
- Release note frozen with SHA marker if this commit closes a wave: prior
  release note carries `Historical artifact frozen at SHA <merge-sha>`
  marker per Rule 28 release-note convention.
- Family ledger refreshed via `/refresh-defect-archive` (Rule G-9.b
  content-diff freshness PASS).
- Branch protection status checks all green (Maven build + Quickstart
  smoke per rc9 enforcement).
- 4-surface baseline lockstep in single commit.
- `/refresh-architecture-doc` reports all stages PASS — every templated
  + hybrid surface re-renders byte-identical against the live authority
  surfaces (Rule G-13.b). Editing an authority surface without
  re-rendering the dependent templates blocks at this gate.

## Composes with

- The verify phase must have completed green before entering →
  `/verify-mode`.
- If a reviewer challenges the commit content → `/review-mode`.
- After merge, the next iteration's design phase opens → `/design-mode`.

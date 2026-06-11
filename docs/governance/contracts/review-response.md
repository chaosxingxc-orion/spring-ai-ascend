---
level: L0
view: scenarios
scope_phase: review
status: active
authority: "ADR-0098 (rc21 — 6-phase scenario-loaded contracts)"
---

# Phase Contract — Review Response

## When you enter this phase

You are about to:

- Process reviewer findings from `docs/logs/reviews/*.md` or PR comments.
- Apply the `reviewer-feedback-self-check` methodology
  (categorize → sweep → batch-fix → prevention).
- Identify whether reviewer-cited defects represent a recurring family
  (see `docs/governance/recurring-defect-families.yaml`) — if yes,
  trigger `/refresh-defect-archive`.
- Decide whether the response is a corrective commit on the same
  branch, a new PR, or a follow-up wave.
- Author a rebuttal for any reviewer finding you reject; never silently
  ignore findings.

Invoke `/review-mode` (Wave 3) at phase entry. Until Wave 3 ships, Read
this file directly before starting.

## Active rules — review phase

Markers: **P** = primary · **X** = cross-reference. G-9 carries **dual-P**
(also primary under `system-commit.md`); this is the only multi-P rule per
ADR-0098 §rule-allocation-map.

| Rule | Title | Marker | Card |
|---|---|---|---|
| D-1 | Root-Cause + Strongest-Interpretation Before Plan | **X** | [`rule-D-1.md`](../rules/rule-D-1.md) |
| D-2 | Simplicity & Surgical Changes | **X** | [`rule-D-2.md`](../rules/rule-D-2.md) |
| D-3 | Pre-Commit Checklist + Evidence-First Debug | **X** | [`rule-D-3.md`](../rules/rule-D-3.md) |
| D-5 | Self-Audit is a Ship Gate, Not a Disclosure | **X** | [`rule-D-5.md`](../rules/rule-D-5.md) |
| D-9 | No Version / Log Metadata in Code | **X** | [`rule-D-9.md`](../rules/rule-D-9.md) |
| G-2 | Authority-Text Reality (doc / status / path / numeric truth) | **X** | [`rule-G-2.md`](../rules/rule-G-2.md) |
| G-2.1 | Deleted-Module Scope Prevention | **X** | [`rule-G-2.1.md`](../rules/rule-G-2.1.md) |
| G-3 | Kernel-Card-Implementation Coherence | **X** | [`rule-G-3.md`](../rules/rule-G-3.md) |
| G-8 | Cross-Authority Parity (graph baseline / SPI path / module topology / current-claim grammar / structural-carrier parity) | **X** | [`rule-G-8.md`](../rules/rule-G-8.md) |
| G-9 | Recurring-Defect Family Truth | **P** | [`rule-G-9.md`](../rules/rule-G-9.md) |

## Forbidden patterns (this phase)

- Silently ignoring a reviewer finding — every finding MUST resolve to
  either a closing commit OR a documented rejection-with-rationale.
- Closing a finding by editing only the cited file when other corpus
  locations share the same defect class — apply categorize → sweep →
  batch-fix → prevention (reviewer-feedback-self-check methodology).
- Not invoking `/refresh-defect-archive` when a reviewer-cited defect
  matches an existing family in `recurring-defect-families.yaml`.
- Adding a corrective commit to an already-frozen wave's release note (use
  a new release note for the corrective wave, freeze the prior one).
- Rejecting a finding without articulating WHY (the rejection record is
  itself a corpus document the next reviewer will read).

## Exit criteria

- Every reviewer finding has either a closing commit OR a documented
  rebuttal (in the response release note or PR description).
- Family ledger updated if any finding matches an existing family OR
  defines a new family (Rule G-9 dual-P).
- Prevention rule landed for any NEW recurring family (Rule 110 META
  scope_surfaces + ≥2 fixtures required).
- Hidden-defect sweep done: sweeping the same defect class across the
  full corpus, not just the reviewer-cited locations.

## Composes with

- A reviewer finding may force a return to `/design-mode` (architecture
  change), `/impl-mode` (code change), or `/verify-mode` (gate fix).
- When findings are closed, return to `/commit-mode` for the
  closeout commit.

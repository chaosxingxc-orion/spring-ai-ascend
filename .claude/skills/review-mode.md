---
name: review-mode
description: |
  Load the review-response phase contract. Use this skill when:
  - Processing reviewer findings from `docs/logs/reviews/*.md` or PR
    comments.
  - Applying the reviewer-feedback-self-check methodology
    (categorize → sweep → batch-fix → prevention).
  - Identifying whether a reviewer-cited defect represents a
    recurring family (see `docs/governance/recurring-defect-families.yaml`).
  - Deciding whether the response is a corrective commit on the same
    branch, a new PR, or a follow-up wave.
  - Authoring a rebuttal for any reviewer finding you reject.
  Reads `docs/governance/contracts/review-response.md` and emits the
  phase entry checklist per ADR-0098 (rc21).
scope: project
---

# /review-mode — Load Review Response Phase Contract

## Purpose

This skill is the review-response phase entry point. The phase
contract names the rules that bind feedback-cycle work — most
importantly, Rule G-9 (Recurring-Defect Family Truth) which carries
**dual-P** under both `system-commit.md` and `review-response.md`
(ADR-0098 §rule-allocation-map).

The methodology baked in: categorize → sweep → batch-fix →
prevention. This is the rc14–rc18 institutionalised pattern — cite
the family, sweep the corpus, batch-fix the family's locations,
land a prevention rule.

## When to invoke

- Just received reviewer findings (PR comments, review doc under
  `docs/logs/reviews/`, or external reviewer report).
- About to write a response document.
- About to decide whether a finding accept or reject.
- About to apply the corpus sweep that follows a finding (categorize
  → sweep step).
- About to author a prevention rule for a newly-identified recurring
  family.

## What this skill does

1. **Read** `docs/governance/contracts/review-response.md` and
   surface its content.
2. **Highlight** Rule G-9 (dual-P) — if any reviewer finding
   matches an existing family in
   `docs/governance/recurring-defect-families.yaml`, the ledger MUST
   be updated; if a new family emerges, it MUST be added.
3. **Walk through** the 4-step methodology:
   - Categorize: each finding by defect class.
   - Sweep: each class across the corpus, not just cited file.
   - Batch-fix: every match in the same wave.
   - Prevention: a Rule 110-compliant gate rule for any new family.
4. **Surface** the forbidden-patterns block (no silent ignore, no
   reject without rationale, no corrective commit on a frozen wave's
   release note).
5. **Suggest** `/refresh-defect-archive` if a recurring family is
   touched; suggest `/commit-mode` for the closeout commit.

## What this skill does NOT do

- Does NOT auto-accept findings — every finding needs a fix OR a
  documented rebuttal.
- Does NOT skip the sweep — the cited file is usually one instance
  of a broader defect class.
- Does NOT replace the user's judgment on accept-vs-reject — the
  skill loads the rules that govern HOW you respond, not WHAT you
  decide.

## Composes with

- `/refresh-defect-archive` — invoke when a recurring family is
  touched.
- `/impl-mode` — for the corrective code edits.
- `/verify-mode` — verify the corrective commits.
- `/commit-mode` — for the closeout commit + frozen-prior-note
  marker.

## See also

- ADR-0098 — rc21 6-phase scenario-loaded contracts.
- `docs/governance/contracts/review-response.md` — the contract this
  skill loads.
- `docs/governance/recurring-defect-families.yaml` + `.md` — the
  family ledger.
- reviewer-feedback-self-check skill (top-level skill, project-scope
  if installed) — the categorize → sweep → batch-fix → prevention
  methodology this skill operationalises in phase-contract form.

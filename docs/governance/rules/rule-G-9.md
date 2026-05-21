---
rule_id: G-9
title: "Recurring-Defect Family Truth"
level: L0
view: scenarios
principle_ref: P-D
authority_refs: [ADR-0094]
enforcer_refs: [E156, E157, E158]
status: active
scope_phase: commit
kernel_cap: 8
scope_surfaces:
  - docs/governance/recurring-defect-families.yaml
  - docs/governance/recurring-defect-families.md
  - docs/adr/*.yaml
  - docs/governance/architecture-status.yaml#baseline_metrics
  - docs/logs/releases/*.md (latest)
  - CLAUDE.md (#### Rule heading set)
kernel: |
  **Architecture-refresh signals — new `docs/adr/*.yaml`, change in `architecture-status.yaml#baseline_metrics`, new `docs/logs/releases/*.md`, or change in the `#### Rule X` heading set in `CLAUDE.md` — MUST be accompanied by a content-diff change to `docs/governance/recurring-defect-families.yaml`. Sub-clause .b enforces freshness by comparing the current yaml against `git show {signal_sha}^1:{yaml_path}` (first-parent semantics; squash-merge required on `main`); a no-op edit (trailing newline, whitespace, or `last_updated:` field bump without family-state change) fails. The yaml MUST be well-formed (every family has `id`, `title`, `first_observed_rc`, `last_observed_rc`, `occurrences`, `root_cause`, `surfaces`, `prevention_rules`, `cleanup_status`, `open_residual` — 9 required fields, sub-clause .a). The companion `recurring-defect-families.md` MUST list the SAME set of family ids AND match `cleanup_status` per id (sub-clause .c — yaml↔md parity, status-text included per rc20 Wave 1 / ADR-0097).**
---

# Rule G-9 — Recurring-Defect Family Truth

Added in the rc17 wave (per ADR-0094) as the structural backstop for the
F-recurring-family-discipline meta-pattern documented across rc10/rc11/rc16.

## Motivation

Across rc4 → rc16 the same root-cause classes recurred 3-9 times each.
The rc10 / rc11 / rc16 release notes documented "reviewer scope ≠ defect
scope" as a meta-lesson, and rc16 added Rule 110 META to gate
`scope_surfaces:` frontmatter on prevention rules. But Rule 110 only
ensures each new rule declares its surfaces — it does not surface the
HISTORICAL family ledger to future reviewers.

A reviewer landing on rc18 should be able to ask "has this class been
seen before?" without re-reading 16 release notes. The answer lives in
`docs/governance/recurring-defect-families.yaml` (machine form) +
`recurring-defect-families.md` (human form). Rule G-9 enforces that this
ledger stays fresh — every architecture refresh MUST sync it.

## Sub-clauses

### .a — YAML Well-Formedness

**Enforcer**: E156 (Gate Rule 111 sub-check a).

`docs/governance/recurring-defect-families.yaml` MUST parse as valid
YAML. Every entry under `families:` MUST declare all 9 required fields:

1. `id` — kebab-case slug starting with `F-`
2. `title` — one-line human title
3. `first_observed_rc` — `rcN` string
4. `last_observed_rc` — `rcN` string
5. `occurrences` — list of `rcN` strings
6. `root_cause` — paragraph
7. `surfaces` — list of paths/globs
8. `prevention_rules` — list of Rule IDs
9. `cleanup_status` — enum: `closed | structurally_addressed | partial | incomplete | monitoring`
10. `open_residual` — paragraph (may be empty string for `closed` status)

(rc20 Wave 2 / ADR-0097 added `monitoring` per rc19 Wave 3 schema; the rule
card and the validator enum `CLEANUP_STATUS_ENUM` agree.)

Top-level keys `schema_version` (integer) and `last_updated` (ISO date)
are required.

### .b — Content-Diff Freshness

**Enforcer**: E157 (Gate Rule 111 sub-check b).

An "architecture refresh signal" is any of:

- New `docs/adr/*.yaml` file (added since last `recurring-defect-families.yaml` commit)
- Change in `docs/governance/architecture-status.yaml#baseline_metrics` block (any field)
- New `docs/logs/releases/*.md` file (latest by `gate/lib/latest_release.sh`)
- Change in the `^#### Rule X` heading set in `CLAUDE.md`
- Change in `docs/governance/rules/*.md` (new card, edited card)

If ANY refresh signal is present in `git log` since the last commit that
modified `recurring-defect-families.yaml`, the implementation in
`gate/lib/validate_recurring_families.py` resolves the signal-touching
commit SHA and compares the current yaml content against
`git show {signal_sha}^1:{yaml_rel}` (first-parent semantics; squash-merge
is therefore required on `main` for the parent path to be deterministic).

A trivial edit (trailing newline, whitespace-only change, or a
`last_updated:` field bump with no family-state change) does NOT satisfy
freshness — the gate fails with a "no-op edit" message. Authors MUST
update a real family field (`occurrences`, `cleanup_status`,
`open_residual`, `prevention_rules`, or `surfaces`) when the refresh
signal lands.

The `last_updated` field remains a human-readable timestamp for
auditors; the gate does NOT compare it to git mtime (rc18 Wave 1 +
rc19 Wave 1 closed ADV-RC18-1 by replacing mtime proxy with
content-diff).

### .c — YAML ↔ MD Family-ID Parity

**Enforcer**: E158 (Gate Rule 111 sub-check c).

Extract the set of `id:` values from `recurring-defect-families.yaml`
and the set of `### F-...` H3 headings (or family-id table rows) from
`recurring-defect-families.md`. The two sets MUST be equal.

A family added to yaml but not md → FAIL (incomplete human view).
A family in md but not yaml → FAIL (stale human view).

## Why three sub-clauses

The three sub-clauses cover three orthogonal failure modes:

- .a catches **structural corruption** — author edited the yaml with a
  typo or missing field.
- .b catches **drift** — author refreshed the architecture without
  remembering to update the ledger.
- .c catches **half-update** — author updated the yaml but forgot the
  md (or vice versa).

Each has its own self-test fixture (positive + negative).

## Why latest release note only

The `gate/lib/latest_release.sh::latest_release_path` resolver (rc12
Rule 102 fix) returns the rc-number-numeric latest release note. Earlier
release notes are frozen historical snapshots. Treating them all as
"signals" would lock the family ledger into permanent staleness as
historical waves are re-read.

## Why this rule cannot be skipped via skill

The companion `/refresh-defect-archive` skill (project-scoped) is a
developer convenience that re-runs the family-derivation pipeline and
bumps `last_updated`. But the skill is opt-in. Rule G-9 is the gate
that catches the case where the skill was never run. The skill writes;
the rule asserts.

## Activation

Activated 2026-05-21 by the v2.0.0-rc17 wave per ADR-0094. Enforcers
E156, E157, E158 cover the three sub-clauses with three self-test
fixtures.

## Self-tests (satisfying Rule 110 META requirements)

Per Rule 110 META, every prevention rule must declare `scope_surfaces:`
frontmatter AND carry ≥2 self-test fixtures across distinct surfaces.
Rule G-9 declares 6 surfaces and ships 3 fixtures (one per sub-clause):

- Fixture 1 (sub-clause .a) — synthetic yaml missing one required field
  → Rule 111 FAILS.
- Fixture 2 (sub-clause .b) — synthetic refresh signal (touched ADR file)
  without family-ledger update → Rule 111 FAILS.
- Fixture 3 (sub-clause .c) — synthetic yaml with family id missing
  from md (or vice versa) → Rule 111 FAILS.

All three fixtures live under `gate/test_architecture_sync_gate.sh`
following the existing `test_rule_NNN_*` naming pattern.

## Cross-references

- ADR-0094 — rc17 recurring-defect-family-truth + rule-consolidation.
- Rule 110 META (`prevention_rule_scope_completeness`) — Rule G-9 is a
  test case for Rule 110: it declares `scope_surfaces:` frontmatter and
  ships fixtures across multiple surfaces. Failing Rule 110 on G-9
  itself would be the canonical kernel-vs-impl drift catch.
- Rule G-1.a (Layered 4+1 Discipline) — Rule G-9 protects an L0
  scenarios-view artefact (`recurring-defect-families.md`).
- Rule G-8 (Cross-Authority Parity) — Rule G-9 is the freshness analogue
  of G-8.a (parity catches disagreement; G-9 catches staleness).
- [`recurring-defect-families.yaml`](../recurring-defect-families.yaml) — the SSOT.
- [`recurring-defect-families.md`](../recurring-defect-families.md) — the human view.
- [`/refresh-defect-archive`](../../../.claude/skills/refresh-defect-archive.md) — companion skill.

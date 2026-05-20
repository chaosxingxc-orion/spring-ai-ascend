---
name: refresh-defect-archive
description: |
  Re-evaluate the recurring-defect-family archive after an architecture
  refresh. Use this skill when: a new ADR has landed, `architecture-status.yaml#baseline_metrics`
  has changed, a new release note has been published under
  `docs/logs/releases/`, or the `#### Rule X` heading set in `CLAUDE.md`
  has changed. Updates `docs/governance/recurring-defect-families.yaml`
  (machine SSOT) and `recurring-defect-families.md` (human view), bumps
  `last_updated`, and verifies Rule G-9 (Gate Rule 111) passes.
scope: project
---

# /refresh-defect-archive — Re-evaluate recurring-defect family ledger

## Purpose

This skill is the developer-facing companion to **Rule G-9**
(`docs/governance/rules/rule-G-9.md`, Gate Rule 111). Rule G-9 catches
the case where a developer forgot to sync the family ledger; this skill
is what they run BEFORE pushing, so the gate passes.

## When to invoke

- Just landed an ADR in `docs/adr/` → run this.
- Changed `architecture-status.yaml#baseline_metrics` → run this.
- Wrote a release note under `docs/logs/releases/` → run this.
- Added or renamed an `#### Rule X` heading in `CLAUDE.md` → run this.
- Periodic hygiene: weekly even without an architecture refresh.

## What the skill does

1. **Diff scan** — `git log` for changes since the last commit that
   modified `recurring-defect-families.yaml`. Surface every refresh
   signal (ADR adds, baseline_metrics changes, new release notes, kernel
   heading changes).

2. **Classify** — for each refresh signal, decide whether it introduces
   a NEW family member (recurrence of an existing class) or just a
   one-off finding. The 8 existing families are listed in the yaml; ask
   the user if uncertain.

3. **Update yaml** — for each family that recurred, append the new `rcN`
   to `occurrences`, update `last_observed_rc`, and append any new
   `prevention_rules`. For brand-new families (root-cause class not yet
   in the ledger), add a new entry with all 9 required fields and ask
   the user for `root_cause` text.

4. **Update md** — mirror the yaml changes in the human view. Each
   family in yaml MUST appear in md (Rule G-9.c parity check).

5. **Bump `last_updated`** — set to today's ISO date.

6. **Run Rule 111** — invoke
   `bash gate/check_architecture_sync.sh` filtered to Rule 111 (or
   the targeted rule extractor). Confirm PASS.

7. **Suggest commit** — show the user the diff and propose a commit
   message of the form `docs(rcN): refresh recurring-defect-family ledger`.

## What the skill does NOT do

- Does not write a release note (release notes live under
  `docs/logs/releases/` and have their own process).
- Does not write an ADR (the user authors ADRs explicitly).
- Does not commit on its own — the user reviews the diff and commits
  manually.
- Does not invent new families without user confirmation (low-recall
  failure mode: false-positive family additions clutter the ledger).

## Failure modes

- **Yaml malformed after edit** → revert the edit, surface the parse
  error to the user, retry once. If the second attempt fails, bail and
  ask for help.
- **Md / yaml parity violation** → fix the lagging surface (usually md
  lags yaml) without asking; verify Rule 111 passes; report what was
  done.
- **Gate Rule 111 fails after sync** → surface the failure to the user
  verbatim; do NOT try to mask it.

## Composes with

- **Rule G-9** (`docs/governance/rules/rule-G-9.md`) — the gate this
  skill helps developers pass.
- **Rule 110 META** — Rule G-9 itself must satisfy Rule 110
  (`scope_surfaces:` frontmatter + ≥2 self-test fixtures); this skill
  does not change that.
- **`docs/governance/recurring-defect-families.md` §3** — META-Lessons
  table; new lessons documented in release notes should be appended
  here.

## See also

- ADR-0094 — rc17 recurring-defect-family-truth + rule-consolidation.
- `docs/governance/recurring-defect-families.yaml` — the SSOT.
- `docs/governance/recurring-defect-families.md` — human view.
- `gate/check_architecture_sync.sh` Rule 111 — the gate.

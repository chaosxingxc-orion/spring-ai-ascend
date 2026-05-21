---
name: commit-mode
description: |
  Load the system-commit phase contract. Use this skill when:
  - About to author a release note under `docs/logs/releases/`.
  - About to author an ADR that closes a release.
  - About to run the pre-commit checklist (Rule D-3.a: smoke + lint).
  - About to lockstep-update baseline surfaces (`baseline_metrics` +
    `allowed_claim` + README + freeze prior release note SHA marker).
  - About to refresh the recurring-defect-families ledger
    (Rule G-9 + `/refresh-defect-archive`).
  - About to open or finalize a PR; about to write a merge commit.
  Reads `docs/governance/contracts/system-commit.md` and emits the
  phase entry checklist per ADR-0098 (rc21).
scope: project
---

# /commit-mode — Load System Commit Phase Contract

## Purpose

This skill is the commit / release phase entry point. The phase
contract names the rules that bind commit work (D-3.a, D-5, G-2,
G-2.1, G-3, G-3.1, G-8, G-9, R-B primary).

The rc17 lockstep lesson encoded here: when bumping baselines, the 4
surfaces (`baseline_metrics`, `allowed_claim`, README baseline line,
prior release note SHA freeze) MUST land in ONE commit — splitting
them across commits triggers a corrective spiral.

## When to invoke

- About to write a release note.
- About to write an ADR that closes a release.
- About to commit a release-critical change (server entry points,
  runtime adapters, dependency wiring) — Rule D-3.a mandates smoke
  + lint.
- About to lockstep-update baselines.
- About to open a PR (the PR description is also commit-phase
  output).

## What this skill does

1. **Read** `docs/governance/contracts/system-commit.md` and surface
   its content.
2. **Highlight** the lockstep rule — 4 surfaces in 1 commit.
3. **Surface** the forbidden-patterns block (no version/log
   metadata leaking into code per D-9, no open self-audit findings
   per D-5, no skipping smoke+lint per D-3.a).
4. **State** the exit criteria (release note frozen with SHA marker,
   family ledger refreshed, branch protection PASS).
5. **Suggest** running `/refresh-defect-archive` if this commit
   closes a wave; suggest `/review-mode` if a reviewer challenges
   the commit.

## What this skill does NOT do

- Does NOT commit on your behalf — you stage and commit; the skill
  loads the rules that govern WHAT you commit.
- Does NOT bypass pre-commit hooks or branch protection — both are
  hard gates per Rule G-7 + Rule D-3.a.

## Composes with

- `/verify-mode` — must have completed green before entering commit
  phase.
- `/refresh-defect-archive` — run before committing if any
  architecture-refresh signal landed (Rule G-9).
- `/review-mode` — if a reviewer challenges the commit content
  post-PR-open.

## See also

- ADR-0098 — rc21 6-phase scenario-loaded contracts.
- `docs/governance/contracts/system-commit.md` — the contract this
  skill loads.
- `feedback_lockstep_baseline_surfaces.md` — the rc17 lockstep
  lesson encoded in this phase's exit criteria.
- `feedback_release_note_baseline_truth.md` — Rule 28 release-note
  convention (frozen prior note, 2-col Count + Delta format).

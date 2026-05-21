---
name: verify-mode
description: |
  Load the integration-verification phase contract. Use this skill when:
  - About to run the gate (`bash gate/check_parallel.sh` on Linux/WSL).
  - About to run Maven tests or verify (`./mvnw verify`, `./mvnw.cmd`
    on Windows hosts per Rule G-7).
  - About to run smoke or integration tests against a deployed
    environment.
  - Debugging a regressing test, a failing Run, or a self-audit
    finding — Rule D-3.b mandates evidence capture BEFORE consulting
    architecture docs.
  - Validating that a CI run is green and the change is mergeable.
  Reads `docs/governance/contracts/integration-verification.md` and
  emits the phase entry checklist per ADR-0098 (rc21).
scope: project
---

# /verify-mode — Load Integration Verification Phase Contract

## Purpose

This skill is the verification phase entry point. The phase contract
names the rules that bind verification work (D-3.b primary —
evidence-first debug; G-5, G-6 primary — gate self-consistency and
machinery integrity; D-4, D-5, G-7 as cross-references).

## When to invoke

- About to run `bash gate/check_parallel.sh` on Linux/WSL.
- About to run `./mvnw verify` (or `./mvnw.cmd` on Windows).
- About to run smoke tests against a deployed environment.
- About to debug a regressing test — invoke BEFORE reading
  `ARCHITECTURE.md` or ADRs (Rule D-3.b: evidence first).
- About to write a self-audit finding under Rule D-5.

## What this skill does

1. **Read** `docs/governance/contracts/integration-verification.md`
   and surface its content.
2. **Highlight** Rule D-3.b — observable evidence (failing test FQN,
   trace ID, MDC slice with `runId`, `tenantId`, `fromStatus →
   toStatus`, stack frame line numbers) MUST be captured BEFORE
   architectural consultation.
3. **Surface** the forbidden-patterns block (no Git Bash for
   verification, no Surefire-only `mvn test` when integration tests
   exist, no victory declaration without all three D-4 layers green).
4. **State** the exit criteria (parallel gate green on Linux/WSL,
   three test layers green, evidence captured for any regression).
5. **Suggest** the next phase at exit — typically `/commit-mode`
   once verification is green.

## What this skill does NOT do

- Does NOT run the gate or tests — you invoke them; the skill loads
  the rules that govern interpreting their output.
- Does NOT skip evidence capture — Rule D-3.b is non-negotiable on
  regression debugging.

## Composes with

- `/impl-mode` — branch back if verification surfaces a code defect.
- `/commit-mode` — natural next phase after verification is green.
- `/review-mode` — if verification surfaces a regression that
  matches a recurring family.

## See also

- ADR-0098 — rc21 6-phase scenario-loaded contracts.
- `docs/governance/contracts/integration-verification.md` — the
  contract this skill loads.
- `docs/runbooks/debug-first-evidence.md` — the runbook Rule D-3.b
  operationalises.
- `feedback_release_verify_runs_failsafe.md` — `mvn verify` not
  `mvn test`.

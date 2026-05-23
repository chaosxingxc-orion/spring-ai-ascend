---
level: L0
view: scenarios
scope_phase: verify
status: active
authority: "ADR-0098 (rc21 — 6-phase scenario-loaded contracts)"
---

# Phase Contract — Integration Verification

## When you enter this phase

You are about to:

- Run the gate (`bash gate/check_parallel.sh` on Linux/WSL per Rule G-7).
- Run the Maven test or verify suite (`./mvnw.cmd ... test` on Windows,
  `./mvnw ... verify` for Failsafe integration tests).
- Run smoke or integration tests against a deployed environment.
- Debug a regressing test or a failing Run — Rule D-3.b mandates
  evidence capture BEFORE consulting architecture docs.
- Validate that a CI run is green and the change is mergeable.

Invoke `/verify-mode` (Wave 3) at phase entry. Until Wave 3 ships, Read
this file directly before starting.

## Active rules — verify phase

Markers: **P** = primary · **X** = cross-reference.

| Rule | Title | Marker | Card |
|---|---|---|---|
| D-1 | Root-Cause + Strongest-Interpretation Before Plan | **X** | [`rule-D-1.md`](../rules/rule-D-1.md) |
| D-3 | Pre-Commit Checklist + Evidence-First Debug | **P** | [`rule-D-3.md`](../rules/rule-D-3.md) |
| D-4 | Three-Layer Testing, With Honest Assertions | **X** | [`rule-D-4.md`](../rules/rule-D-4.md) |
| D-5 | Self-Audit is a Ship Gate, Not a Disclosure | **X** | [`rule-D-5.md`](../rules/rule-D-5.md) |
| D-9 | No Version / Log Metadata in Code | **X** | [`rule-D-9.md`](../rules/rule-D-9.md) |
| G-1 | Layered 4+1 Discipline + Architecture-Graph Truth | **X** | [`rule-G-1.md`](../rules/rule-G-1.md) |
| G-5 | Gate Self-Consistency (parity / coverage / manifest / freshness) | **P** | [`rule-G-5.md`](../rules/rule-G-5.md) |
| G-6 | Gate Machinery Integrity (duration + config) | **P** | [`rule-G-6.md`](../rules/rule-G-6.md) |
| G-7 | Linux-First Dev Environment | **X** | [`rule-G-7.md`](../rules/rule-G-7.md) |
| G-10 | Parallel-Linux-Scripts Mandate | **X** | [`rule-G-10.md`](../rules/rule-G-10.md) |
| G-12 | Whitebox Quality Baseline | **P** | [`rule-G-12.md`](../rules/rule-G-12.md) |
| R-A | Business/Platform Decoupling Enforcement | **X** | [`rule-R-A.md`](../rules/rule-R-A.md) |
| R-C | Code-as-Contract | **X** | [`rule-R-C.md`](../rules/rule-R-C.md) |
| R-C.2 | Run Contract Spine | **X** | [`rule-R-C.2.md`](../rules/rule-R-C.2.md) |
| R-D | SPI + DFX + TCK Co-Design + Catalog Integrity | **X** | [`rule-R-D.md`](../rules/rule-R-D.md) |
| R-F | Cursor Flow Mandate | **X** | [`rule-R-F.md`](../rules/rule-R-F.md) |
| R-G | Reactive External I/O | **X** | [`rule-R-G.md`](../rules/rule-R-G.md) |
| R-H | No Thread.sleep in Business Code | **X** | [`rule-R-H.md`](../rules/rule-R-H.md) |
| R-J | Storage-Engine Tenant Isolation + Cancel Re-Authorization | **X** | [`rule-R-J.md`](../rules/rule-R-J.md) |
| R-M | Engine Contract (envelope / matching / hooks / S2C / scope / historical) | **X** | [`rule-R-M.md`](../rules/rule-R-M.md) |

## Forbidden patterns (this phase)

- Consulting `ARCHITECTURE.md` / ADR before capturing observable evidence
  (failing test FQN, trace ID, MDC slice, stack frame line numbers) — Rule
  D-3.b mandates evidence-FIRST.
- Running gate on Git Bash for Windows instead of Linux/WSL (Rule G-7).
- Marking a regression "investigated" without a runbook-style evidence
  block under `docs/runbooks/debug-first-evidence.md` (Rule D-3.b sub-clause).
- Skipping Failsafe integration tests because `mvn test` (Surefire-only)
  was green — `mvn verify` is the release-verify command.
- Declaring victory when only some of D-4's three test layers are green.

## Exit criteria

- Parallel gate green: `bash gate/check_parallel.sh` exits 0 on Linux/WSL.
- Whitebox quality green: `./mvnw -Pquality verify` produces SpotBugs, PMD, and Checkstyle reports; gate Rule 121 has no hard failures.
- All three Rule D-4 test layers green (unit, integration, smoke); test
  scopes documented in the test class's javadoc per `feedback_release_verify_runs_failsafe.md`.
- Evidence captured under `docs/runbooks/` for any regression (Rule D-3.b).
- Per-rule duration baseline check passes (Rule G-6.a) — no rule's duration
  exceeds 2× rolling median AND 200ms absolute.

## Composes with

- After verification is green, the commit phase starts → `/commit-mode`.
- If verification surfaces a defect, branch back to `/impl-mode`.
- When verification fails due to a reviewer-flagged regression →
  `/review-mode`.

# v2.0.0-rc36 — Exhaustive correctness + cross-authority-truth wave

**Date:** 2026-05-24
**Branch:** `rc36/exhaustive-correctness-and-cross-authority-truth`
**Authority:** ADR-0116

## Summary

A 7-surface parallel adversarial review ran against the post-#61 `main` tip
(runtime-spine correctness, engine/middleware/bus SPI, cross-authority parity,
gate-script correctness, test quality, numeric/doc truth, and whitebox static
analysis). The corpus gate was green (135/135 rules, 226/226 self-tests,
whitebox 0 SpotBugs-high-confidence / 0 Checkstyle-errors / 7 PMD review-triggers)
yet the review surfaced defects the serial gate cannot see — the recurring lesson
that **green gates do not imply a bug-free corpus**.

## Headline: two kernel-truth gate rules were silent-passing for ~20 waves

Rule 96 (`kernel_deferred_clause_coherence`) and Rule 99
(`kernel_terminal_verb_vs_shipped_decision_check`) — the two enforcers whose JOB
is detecting kernel-vs-implementation drift — had themselves drifted. Their
heading-discovery regexes stayed numeric (`^#### Rule [0-9]+`,
`^## Rule [0-9]+\.[a-z]`) and matched **0 of the 42** namespaced `#### Rule X-N`
kernels after the rc16 D-/R-/G-/M- migration, so both rules iterated empty sets
and never fired. This is `F-kernel-vs-implementation-drift` at its worst — a
drift-detector that detected nothing while reporting PASS.

**Fix:** namespace-aware longest-prefix parent resolution + a **non-vacuity guard**
on each rule (the rule now FAILS if it resolves 0 sub-clauses/parents). Seven
deferred sub-clauses the dead rule was hiding — acknowledged only by stale legacy
ids (`40.b`, `46.c`, `48.b`) — are corrected to current namespaced ids in their
parent cards (R-J.a.b, R-M.d.c, M-2.a.b/.a.c, R-I.c/.d/.e).

## Findings closed

- **Runtime correctness (P0/P1):** `RunController.cancel` did stale-read →
  validate-against-stale → blind-save, so a parallel orchestrator terminal write
  (SUCCEEDED/FAILED) could be silently overwritten to CANCELLED — the missing
  controller half of the rc35 cancel-vs-complete CAS fix. Closed by an atomic
  `RunRepository.updateIfNotTerminal` (default + `computeIfPresent` override).
  `SyncOrchestrator.mutateIfNotTerminal` now catches the lost-race
  `IllegalStateException` so it cannot mask a caller's original error; the
  SUCCEEDED return path no longer reports success for a cancelled run. Dead code
  (`currentOrLocal`) removed; unused `handleClientCallback` param dropped; a
  swallowed `DuplicateKeyException` in `JdbcIdempotencyStore` now logs.
- **Gate-script (P0/P1):** Rules 28b/28d + Rule 14 scanned a **doubled single
  path** (`agent-service/src/main/java` twice) and so covered only 1 of 6 modules;
  widening to `agent-*/src/main/java` immediately caught a real out-of-scope-name
  leak in `agent-bus/package-info.java` (genericised). `r38/r42` mktemp fallbacks
  hardened with `$$`. Rule 82 phrase-map dead key `active_engineering_rules_post_rc6`
  → `active_engineering_rules`.
- **Cross-authority (P1/P2):** Rule R-J.b kernel+card overclaimed shipped 403 +
  WARN audit; ADR-0108 documents 404 as W0-shipped and 403+audit as the
  W1-widening direction — kernel (byte-matched across CLAUDE.md + card) + body
  aligned. contract-catalog HookPoint 9-value → 10-value (adds `on_yield`).
- **Numeric/path truth:** gate/README executable-section count 133→135; 5 stale
  `service.runtime.orchestration.spi` paths → `engine.orchestration.spi`
  (relocated per ADR-0088); enforcers.yaml `asserts:` prose corrected.

## Tests

- New `InMemoryRunRegistryUpdateIfNotTerminalTest` (5 cases incl. a 200-trial
  concurrency race proving terminal-state immutability under the CAS).
- New `RunHttpContractIT` cases: cancel happy-path + idempotent re-cancel (200),
  GET own-run (200), GET cross-tenant (404). (+8 suite total: 374 → 382.)

## Verification

- `mvnw.cmd -T 1C -B -ntp --strict-checksums -Pquality verify` — BUILD SUCCESS;
  0 SpotBugs high-confidence, 0 Checkstyle errors (3 of the 7 PMD review-triggers
  resolved by the dead-code/unused-field cleanups).
- `bash gate/check_parallel.sh` — GATE: PASS (135/135); Rules 96/99 now fire and
  pass against the namespaced corpus. `bash gate/test_architecture_sync_gate.sh` —
  green.

## Four competitive pillars (P-B)

- **performance** — unchanged; gate parallel runner + per-rule timing intact.
- **cost** — unchanged; no new runtime dependencies.
- **developer_onboarding** — improved: quickstart + oss-bill-of-materials SPI
  paths corrected to the live `engine.orchestration.spi` home.
- **governance** — strengthened: two dead kernel-truth gates restored with a
  non-vacuity guard; cancel-edge correctness invariant now holds on both surfaces.

## Baseline deltas

| Metric | Count | Delta |
|---|---|---|
| §4 constraints | 65 | 0 |
| ADRs | 101 | reconciled from the stale 90-claim to match adr_count (rc33–36 added ADR-0106..0116) |
| active gate rules | 135 | 0 |
| gate self-test cases | 226 | 0 |
| active engineering rules | 42 | 0 |
| active governing principles | 13 | 0 |
| enforcer rows | 168 | 0 |
| adr_count (ADR files) | 101 | +1 (ADR-0116) |
| maven_tests_green | 382 | +8 (5 unit + 3 ITs) |
| architecture_graph_nodes | 469 | +1 (ADR-0116 node) |
| architecture_graph_edges | 835 | +4 (ADR-0116 relates_to/affects edges) |
| recurring_defect_families | 12 | 0 |

## Self-review corrective (Phase 8)

The final adversarial re-review of this wave's own diff caught two issues — both fixed here,
both with regression tests:

- **FAILED-cancel regression (P1):** the first cut of the cancel CAS regressed `POST /cancel` on a
  FAILED run from 409 to 500. FAILED is non-terminal (FAILED→RUNNING retry is legal), so
  `updateIfNotTerminal` ran the mutator and the state-machine `IllegalStateException` escaped
  uncaught. Fixed: `updateIfNotTerminal` (both the SPI default and the `computeIfPresent` override)
  swallows the illegal-transition ISE and returns the Run unchanged, so the controller maps it to
  409 `illegal_state_transition`. Added a unit case + a `cancelFailedRunReturns409` IT.
- **Rule 82 dead anchor (P1 — a THIRD un-deadened gate, same class as 96/99):**
  `baseline_metrics_single_source` anchored on `^architecture_sync_gate:` at column 0, but that key
  is indented under `capabilities:`, so it parsed 0 metrics and silent-passed. Fixed: anchor on the
  `baseline_metrics:` block (indent-aware) + a non-vacuity guard (fail if 0 metrics parsed).
  Un-deadening it surfaced the stale ADR claim — the long-maintained "90 ADRs" counted only the
  0001-0105 range and went stale as ADR-0106..0116 landed; reconciled to the true 101 (= adr_count)
  across README + allowed_claim + this note.

## Methodology

Categorize → sibling-sweep → batch-fix → prevention. The non-vacuity guard is the
prevention generalisation of this wave's headline class: any auto-discovering
drift-detector that matches zero corpus elements must fail loudly. Latent W2-gated
findings (EngineRegistry unsynchronised reads, HookDispatcher ON_ERROR outcome
ranking, skill-capacity acquire/release symmetry, S2C trace_id enforcement) are
documented in ADR-0116 with rationale, not silently dropped.

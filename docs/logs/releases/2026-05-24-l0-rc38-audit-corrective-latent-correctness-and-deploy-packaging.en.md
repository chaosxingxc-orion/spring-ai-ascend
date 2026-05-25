# v2.0.0-rc38 — audit-corrective: latent-correctness + deploy-packaging fixes

> **Historical artifact frozen at SHA a53dc356 (v2.0.0-rc38).** Baseline
> counts in this document reflect the rc38 publication state and are NOT
> retroactively updated. The current canonical baseline lives in
> `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`
> and the latest release note.

**Date:** 2026-05-24
**Branch:** `rc37/ascend-kunpeng-strategic-pivot`
**Authority:** ADR-0118

## Summary

rc38 is an **audit-corrective** wave. A four-reviewer parallel deep-read of the
whole Java corpus ran against the rc37 tip while the gate and `mvn verify` were
already green — targeting the residual class the gate cannot see: latent
logic/concurrency bugs and deploy-time packaging gaps. Five findings survived
per-finding verification (read callers/callees + the covering test before
reporting); five further candidates were verified to be by-design and rejected.

The headline finding is a **recurrence**: the cancel-vs-complete race that rc35,
rc35-second-pass, and rc36 each closed for a different set of call-sites was
still live in the orchestrator's own private helper. rc38 closes it and, for the
first time, registers the root-cause class as a recurring-defect family so the
ledger can flag any future recurrence.

## Fixes

1. **(P1, dev-posture) Orchestrator status writes are now atomic.**
   `SyncOrchestrator.mutateIfNotTerminal` did a non-atomic `findById`-then-`save`:
   a parallel `RunController.cancel` writing CANCELLED **between** the re-read and
   the write was silently overwritten by SUCCEEDED (the mutator validated against
   the stale RUNNING snapshot). It now delegates to
   `RunRepository.updateIfNotTerminal` — the same atomic compare-and-set the
   cancel path uses (in-memory: a per-key `computeIfPresent` remap). The top-level
   Run is persisted at `run()` entry so the CAS has a row. A deterministic
   single-threaded regression test (`CancelInjectingRepository`) reproduces the
   exact read-modify-write window the prior latch-based tests skipped.

2. **(P1) Dockerfile copies the executable jar.** agent-service builds a plain
   library jar **and** a `boot`-classified executable jar (ADR-0078). The
   `agent-service-*.jar` glob matched both; a multi-source `COPY` into a
   non-directory destination fails `docker build` (or copies the non-executable
   jar). Now copies `agent-service-*-boot.jar`.

3. **(P1, latent) skill-capacity matrix bundled on the classpath.**
   `YamlSkillCapacityRegistry` resolves its default path on the filesystem then
   the classpath; the packaged jar / distroless image (run from `/app`) has
   neither, so it silently loaded an empty matrix and would fail-closed on every
   `tryAcquire` once Rule R-K wiring consults it. The pom now copies
   `docs/governance/skill-capacity.yaml` onto the classpath. Forward hygiene — no
   shipped path calls `resolve()` yet.

4. **(P2) JWKS decoder gated on `jwks-uri`, not `issuer`.** `issuer` is also a
   valid dev-local-mode setting (issuer-claim validation), so gating the JWKS bean
   on `issuer` wrongly registered it (calling `withJwkSetUri(null)`) for the
   dev-local-mode + issuer combination. Gated on the value it actually needs.

5. **(P2) OpenAPI pinned fixture realigned.** `openapi-v1-pinned.yaml`'s
   POST /v1/runs declared 201 + RunResponse while the canonical contract and the
   live controller return 202 + TaskCursor (Cursor Flow, ADR-0070); the drift made
   `OpenApiContractIT` pass vacuously. Realigned to 202 + TaskCursor.

## Rejected (verified by-design, NOT bugs)

- `springai_ascend_trace_originated_total{source=client|server}` — the `source`
  tag IS the origin dimension; asserted by enforcers.yaml, the architecture
  graph, `telemetry/policy.md`, an allowed_claim, and `TraceExtractFilterIT`.
- `AppPostureGate` reading `APP_POSTURE` — intentional defence-in-depth; the
  property-level `@ConditionalOnProperty` is the primary gate.
- run-dispatch `CALLER_RUNS` default — deliberate backpressure; today's dispatcher
  is a no-op so no cursor-flow guarantee is violated.
- `EngineRegistry` unsynchronised reads — registry is immutable after boot.
- skill-capacity acquire-without-release — `resolve()` has zero production call
  sites (W1 decision-envelope; Rule R-K.c suspension deferred to W2).

## Four competitive pillars (P-B)

- **performance** — unchanged; the orchestrator fix removes a lost-cancel race in
  dev-posture, no hot-path cost.
- **cost** — unchanged.
- **developer_onboarding** — improved: `docker build` now succeeds and ships a
  runnable image; the dev-local-mode + issuer auth config no longer crashes boot.
- **governance** — strengthened: a recurring root cause is finally registered as
  `F-nonatomic-run-status-write` so the ledger flags the next recurrence, and the
  by-design candidates are documented rather than silently re-litigated.

## Baseline deltas

| Metric | Count | Delta |
|---|---|---|
| §4 constraints | 65 | 0 |
| ADRs | 103 | +1 (ADR-0118) |
| active gate rules | 135 | 0 |
| gate self-test cases | 226 | 0 |
| active engineering rules | 42 | 0 |
| active governing principles | 13 | 0 |
| enforcer rows | 168 | 0 |
| adr_count (ADR files) | 103 | +1 (ADR-0118) |
| maven_tests_green | 383 | +1 (orchestrator CAS regression test) |
| architecture_graph_nodes | 471 | +1 (ADR-0118 node) |
| architecture_graph_edges | 844 | +5 (ADR-0118 relates_to/affects edges) |
| recurring_defect_families | 13 | +1 (F-nonatomic-run-status-write) |

## Verification

- `python gate/build_architecture_graph.py` — 471 nodes, 844 edges; validation OK.
- `mvn -Pquality verify` — BUILD SUCCESS; `SyncOrchestratorCancelRaceTest` 3/3.
- `bash gate/check_parallel.sh` + `bash gate/test_architecture_sync_gate.sh` — GATE PASS.

## Methodology

The cancel-vs-complete race is the textbook case the recurring-defect ledger
exists for: rc35 closed the terminal sites, rc35-second-pass the non-terminal
siblings, rc36 the controller half — yet the orchestrator's own helper, the very
indirection those waves routed through, was never converted. Because the class
was never a registered family, the ledger could not say "seen before, check the
siblings." rc38's prevention is the registration itself plus the
categorize → sweep → batch-fix → prevention discipline, with a deterministic test
that exercises the precise interleaving the prior tests missed.

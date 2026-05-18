---
rule_id: 44
title: "Strict Engine Matching"
level: L1
view: process
principle_ref: P-M
authority_refs: [ADR-0072]
enforcer_refs: [E75, E77, E88]
status: active
kernel_cap: 8
kernel: |
  **A Run whose envelope declares `engine_type=X` MUST be executed only by the `ExecutorAdapter` registered under `X` in `EngineRegistry`. Mismatch raises `EngineMatchingException` and transitions the Run to FAILED with reason `engine_mismatch`. No fallback policy. No silent reinterpretation of the payload as another engine's configuration.**
---

## Motivation

Authority: ADR-0072 / P-M. Part of the W2.x Engine Contract Structural Wave that absorbs the 2026-05-15 L0 proposal "Runtime-Engine Contract for Heterogeneous Agent Execution". Strict matching prevents silent reinterpretation of engine-specific payloads — the silent-reinterpretation failure mode is the most dangerous one in heterogeneous-engine systems because it surfaces as undefined behaviour rather than a crisp error.

## Cross-references

- Enforced by Gate Rule 56 (`engine_registry_covers_all_known_engines` — bidirectional yaml↔ENGINE_TYPE consistency, enforcer E77) and integration test E75 (`EngineMatchingStrictnessIT`).
- Additional enforcer E88 (W2.x post-release closure work) tightens registry-boot validation.
- Companion rule: Rule 43 ([`rule-43.md`](rule-43.md)) — Engine Envelope Single Authority.
- Companion rule: Rule 20 ([`rule-20.md`](rule-20.md)) — Run State Transition Validity (`engine_mismatch` is a legal RUNNING → FAILED transition).
- Deferred sub-clauses: Rule 44.b (Run.engineType field persistence), Rule 44.c (parent-run propagation on child failure) — see `docs/CLAUDE-deferred.md`. Rule 96 (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.

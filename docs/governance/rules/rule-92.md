---
rule_id: 92
title: "Gate Rules Corpus Freshness"
level: L0
view: process
principle_ref: P-D
authority_refs: [ADR-0083, "rc8 post-corrective review P2-1"]
enforcer_refs: [E125, E126]
status: active
kernel_cap: 8
kernel: |
  **Every `# Rule N — slug` header in `gate/check_architecture_sync.sh` (before the END marker) MUST have a matching `gate/rules/rule-NNN[a-z]?.sh` file (zero-padded to 3 digits, optional lowercase letter suffix). `gate/rules/` is an IDE-only generated artifact (refreshed by `gate/lib/extract_rules.sh`) — the production parallel gate consumes the canonical monolith directly. Closes rc8 post-corrective review P2-1: an incomplete shadow rule corpus drifting stale relative to canonical.**
---

# Rule 92 — Gate Rules Corpus Freshness

## Motivation

The rc8 post-corrective review (P2-1) found that `gate/rules/` had 83 generated files while the canonical monolith executed 102 sections — a 19-file drift. `gate/lib/orchestrator.sh` historically described `gate/rules/` as durable artifacts for IDE inspection, code review, and future unit testing, but the production parallel gate (`gate/check_parallel.sh`) had since converged on operating from the canonical monolith directly, leaving the per-rule files as a non-authoritative shadow corpus.

The shadow corpus carries two risks:
1. Readers and reviewers may inspect the stale files thinking they reflect current gate logic.
2. Agents searching for "where is Rule N implemented" may land on a stale file with old assertions.

Rule 92 closes the freshness gap. The fix is straightforward: every canonical header gets a matching file; `extract_rules.sh` keeps them in sync.

## Algorithm

For every `# Rule N[<letter>] — slug` header in `gate/check_architecture_sync.sh` before the END marker, the gate computes the expected filename:
- `N` zero-padded to 3 digits + optional letter suffix
- Path: `gate/rules/rule-<padded>.sh`

Missing files fail the rule with a list of orphan headers and the refresh command (`bash gate/lib/extract_rules.sh`).

## Why "IDE-only generated artifact"

The wording matters: `gate/rules/` is committed under source control, but **it is not consumed by either canonical or parallel production gate**. Both run from `gate/check_architecture_sync.sh`. Keeping it in source control benefits IDE indexing and PR review (smaller, focused diffs per rule); regenerating it on each rule addition is cheap and prevents drift.

## Enforcement

Enforced by E125 (Gate Rule 92 — `gate_rules_corpus_freshness`). Positive self-test: every header has a matching file → PASS. Negative self-test: a canonical header without a matching file → FAIL with the missing rule id named.

## Activation

Activated 2026-05-19 by the v2.0.0-rc9 wave (rc8 post-corrective review response). Enforcer E125 + E126 (positive + negative self-test fixtures).

## Cross-references

- ADR-0083 — rc9 corpus-truth + CI-acceptance authority record.
- `gate/lib/orchestrator.sh` — updated 2026-05-19 to explicitly state non-authoritative status of `gate/rules/`.
- `gate/lib/extract_rules.sh` — the refresher.
- `docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` finding P2-1 — origin.

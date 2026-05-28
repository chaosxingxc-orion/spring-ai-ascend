---
rule_id: G-5
title: "Gate Self-Consistency (parity / coverage / manifest / freshness)"
level: L0
view: process
principle_ref: P-D
authority_refs: [ADR-0082, ADR-0083, ADR-0085]
enforcer_refs: [E121, E122, E123, E124, E125, E126]
status: active
governance_infra: true
scope_phase: verify
kernel_cap: 8
kernel: |
  **Gate self-consistency is enforced on four surfaces: canonical (`gate/check_architecture_sync.sh`) and parallel wrapper (`gate/check_parallel.sh`) MUST execute the same rule slug set, terminating at the `# === END OF RULES ===` marker with em-dash `—` separators (no double-dash) (sub-clause .a). `gate/test_architecture_sync_gate.sh` MUST fail closed when `passed != TOTAL`, derive TOTAL at runtime (not a bare literal), and declare a `test_rule_<N>_*` function for every prevention-wave rule (N ≥ 80) (sub-clause .b). `baseline_metrics.active_gate_checks` MUST equal the literal `# Rule N — slug` header count, matching what `parallel_summary: executed N rules` reports (sub-clause .c). Every header MUST have a matching `gate/rules/rule-NNN[a-z]?.sh` file keyed by unique rule id (sub-clause .d; `gate/rules/` is IDE-only generated, the canonical monolith is canonical).**
---

# Rule G-5 — Gate Self-Consistency

Operationalises the gate-meta surface — the gate machinery's own integrity as a system. Without this rule the gate could silently skip rules (parallel-vs-serial drift), tolerate fail-open self-tests, or carry stale baseline counts.

## Sub-clauses

### .a — Serial/Parallel Gate Slug Parity (was Rule 88)

**Enforcer**: E121.

Canonical gate (`gate/check_architecture_sync.sh`) and parallel wrapper (`gate/check_parallel.sh`) MUST execute the same rule slug set. The canonical script MUST declare a `# === END OF RULES ===` terminator; the parallel awk MUST terminate on that marker. Every rule header MUST use em-dash `—` (`# Rule N — slug`); double-dash `--` is forbidden. Fails closed on (a) parallel-manifest gap vs canonical, (b) double-dash separator, or (c) missing END marker.

### .b — Self-Test Harness Fail-Closed Coverage (was Rule 89)

**Enforcer**: E122.

`gate/test_architecture_sync_gate.sh` MUST (a) fail closed (exit non-zero) when `passed != TOTAL`; (b) derive `TOTAL` at runtime (`TOTAL=$((passed + failed))` or equivalent), NOT a bare literal outside heredoc fixtures; (c) every prevention-wave Rule (`N >= 80`) MUST have a `test_rule_<N>_*` function (pre-rc4 rules 1-79 grandfathered — covered by ArchUnit / IT at design time).

### .c — Baseline Metric Matches Executable Manifest (was Rule 91)

**Enforcers**: E123, E124.

`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics.active_gate_checks` MUST equal the literal count of `# Rule N — slug` headers in `gate/check_architecture_sync.sh` before the `# === END OF RULES ===` terminator (== the value that `gate/check_parallel.sh` reports as `parallel_summary: executed N rules`).

### .d — Gate Rules Corpus Freshness (was Rule 92)

**Enforcers**: E125, E126.

Every `# Rule N — slug` header in `gate/check_architecture_sync.sh` (before the END marker) MUST have a matching `gate/rules/rule-NNN[a-z]?.sh` file (zero-padded to 3 digits, optional lowercase letter suffix). Files are keyed by unique rule id; a rule with multiple gate sections sharing the same id maps to a single file — so the `active_gate_checks` baseline (executable section count) MAY exceed the `gate/rules/` file count by the number of duplicated section ids. `gate/rules/` is an IDE-only generated artifact (refreshed by `gate/lib/extract_rules.sh`) — the production parallel gate consumes the canonical monolith directly.

## Cross-references

- ADR-0082 — origin of Rule G-5 sub-clause .a (serial/parallel parity) + Rule G-5 sub-clause .b (self-test fail-closed).
- ADR-0083 — origin of Rule G-5 sub-clause .c (baseline-vs-manifest) + Rule G-5 sub-clause .d (corpus freshness).
- ADR-0085 — Rule G-5 sub-clause .d clarification (per-section vs per-file counts).

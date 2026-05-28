---
rule_id: G-6
title: "Gate Machinery Integrity (duration + config)"
level: L0
view: scenarios
principle_ref: P-B
authority_refs: [ADR-0077]
enforcer_refs: [E102, E103]
status: active
governance_infra: true
scope_phase: verify
kernel_cap: 8
kernel: |
  **Gate machinery integrity is enforced on two surfaces: every gate run records per-rule duration in `gate/log/runs/<sha>_<ts>/per-rule.ndjson`; `gate/lib/update_benchmark_baseline.sh` maintains a rolling 5-run median at `gate/log/benchmarks/median.json`; the rule fails when any rule's current duration exceeds 2× baseline median AND exceeds 200ms absolute (sub-clause .a; bootstrap is vacuous until 5 successful runs exist). `gate/config.yaml` MUST validate against `gate/config.schema.yaml` — fails closed on missing required keys, type mismatch, out-of-range values, unknown keys (typo detection via `additionalProperties: false`), or enum violation (sub-clause .b; structural invariant: yaml → loader-validated env-vars → runtime-checked).**
---

# Rule G-6 — Gate Machinery Integrity (duration + config)

Operationalises the gate-meta surface: gate machinery itself is policed for performance regression and config well-formedness.

## Sub-clauses

### .a — Rule Duration Regression Check (was Rule 72)

**Enforcer**: E102.

Every gate run records per-rule duration in `gate/log/runs/<sha>_<ts>/per-rule.ndjson`. After each successful run, `gate/lib/update_benchmark_baseline.sh` updates a rolling median over the last 5 runs at `gate/log/benchmarks/median.json`. The rule fails when any rule's current duration exceeds 2x its baseline median AND exceeds 200ms absolute. Bootstrap waits until 5 successful runs exist; until then the check vacuously passes.

### .b — Gate Config Well-Formed (was Rule 73)

**Enforcer**: E103.

`gate/config.yaml` MUST validate against `gate/config.schema.yaml`. The gate fails closed on: missing required keys at any level, type mismatch, value outside declared min/max range, unknown keys (typo detection via `additionalProperties: false`), enum violation. Schema follows the wave's structural invariant: yaml → loader-validated env-vars → runtime-checked.

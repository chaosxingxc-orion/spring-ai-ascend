---
rule_id: 91
title: "Baseline Metric Matches Executable Manifest"
level: L0
view: process
principle_ref: P-D
authority_refs: [ADR-0083, "rc8 post-corrective review P0-1"]
enforcer_refs: [E123, E124]
status: active
kernel_cap: 8
kernel: |
  **`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics.active_gate_checks` MUST equal the literal count of `# Rule N — slug` headers in `gate/check_architecture_sync.sh` before the `# === END OF RULES ===` terminator (== the value that `gate/check_parallel.sh` reports as `parallel_summary: executed N rules`). Closes rc8 post-corrective review P0-1: the published baseline declared 74 active gate rules while both serial and parallel gates executed 102; ADR-0083 reconciles by adopting the executable-section count as the canonical meaning of `active_gate_checks`.**
---

# Rule 91 — Baseline Metric Matches Executable Manifest

## Motivation

The rc8 post-corrective review (P0-1) found a 28-section gap between the published baseline (`active_gate_checks: 74`) and the executable manifest (`parallel_summary: executed 102 rules`). The gap persisted for 8 consecutive release notes because:

1. The historical `74` count tracked "rule families" (top-level numbers in the canonical script's header comment block), excluding sub-rules like `28a-28k` and the `36b/41b` letters that nonetheless execute as independent sections at gate time.
2. No gate rule asserted equality between the ledger field and the manifest count.
3. Release notes routinely cited the 74-figure verbatim without re-deriving against `parallel_summary`.

The reviewer offered two paths: (a) update the ledger value to 102 (canonical = executable sections), or (b) rename the existing field to `active_gate_rule_families: 74` and add a separate `executable_gate_sections: 102`. ADR-0083 chose (a) — picking the executable section count as the single authoritative meaning of `active_gate_checks`. The historical family count is preserved in `active_engineering_rules_post_rcN` (CLAUDE.md kernel rule heads, which is the more useful family taxonomy anyway).

## Algorithm

The gate computes `_r91_manifest_count` by parsing `gate/check_architecture_sync.sh`:
```
awk '/^# === END OF RULES ===$/{exit} /^# Rule [0-9]+[a-z]? — /{c++} END{print c+0}'
```
and `_r91_declared` by parsing `architecture-status.yaml`:
```
grep -E '^[[:space:]]*active_gate_checks:[[:space:]]*[0-9]+' | head -1
```
The two MUST be equal. The check is purely numeric — the gate doesn't reason about rule semantics, only about the manifest-vs-ledger agreement.

## Why this closes P0-1

Rule 88 (rc8) enforced serial-vs-parallel parity (canonical script vs parallel wrapper agree on the section set). Rule 82 (rc4-rc6) enforced README/gate-README count claims against the ledger. Neither closed the **ledger-vs-canonical-manifest** axis. Rule 91 is the missing third edge of the triangle: now ledger, README quotes, parallel-summary output, and canonical-script headers all derive from one source.

## Enforcement

Enforced by E123 (Gate Rule 91 — `baseline_metric_matches_executable_manifest`) — straight comparison of the parsed numbers. Negative self-test in `gate/test_architecture_sync_gate.sh` mocks a status YAML with a value lower than the canonical count and asserts FAIL; positive fixture confirms PASS when they agree.

## Activation

Activated 2026-05-19 by the v2.0.0-rc9 wave (rc8 post-corrective review response). Enforcer E123 + E124 (positive + negative self-test fixtures).

## Cross-references

- ADR-0083 — rc9 corpus-truth + CI-acceptance authority record (originating ADR).
- Rule 82 (Baseline Metrics Single Source) — adjacent family: numeric-agreement on entrypoint prose vs ledger.
- Rule 88 (Serial/Parallel Gate Slug Parity) — adjacent family: canonical-vs-parallel agreement.
- `docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` finding P0-1 — origin.

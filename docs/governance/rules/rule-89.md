---
rule_id: 89
title: "Self-Test Harness Fail-Closed Coverage"
level: L0
view: process
principle_ref: P-D
authority_refs: [ADR-0082, "v2.0.0-rc7 post-corrective review P1-1"]
enforcer_refs: [E122]
status: active
kernel_cap: 8
kernel: |
  **`gate/test_architecture_sync_gate.sh` MUST (a) fail closed (exit non-zero) when `passed != TOTAL`; (b) derive `TOTAL` at runtime (`TOTAL=$((passed + failed))` or equivalent), NOT a bare literal outside heredoc fixtures; (c) every prevention-wave Rule (`N >= 80`) MUST have a `test_rule_<N>_*` function (pre-rc4 rules 1-79 grandfathered — covered by ArchUnit / IT at design time). Closes rc7 P1-1.**
---

# Rule 89 — Self-Test Harness Fail-Closed Coverage

## Motivation

The rc7 post-corrective architecture review (`docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md` finding P1-1) found that `gate/test_architecture_sync_gate.sh` had two hardcoded `TOTAL=` declarations (line 43: `TOTAL=138` left from the rc6 baseline; line 4098: `TOTAL=143`), and its exit logic ignored TOTAL entirely — `exit 0` fired whenever `failed=0`, regardless of whether `passed < TOTAL`. The rc7 corrective release note claimed `Tests passed: 143/143`, but a reviewer running the harness saw a numerator lower than 143 with no failure signal.

Family-sweep root-cause analysis surfaced a third dimension: **Rule 86 and Rule 87 had no `test_rule_<N>_*` functions**. Their fixtures lived as inline top-level blocks (lines 3942-4087) that ran during script-source and emitted PASS/FAIL via `ok()/fail()`. But the parallel orchestrator at line 4098 overwrote the `passed/failed` counters with values derived from the function-dispatched tests only, losing the inline contributions silently.

Rule 89 codifies the three invariants that prevent every leg of this class of defect:

## Details

### Sub-check (a) — fail closed when passed != TOTAL

The harness MUST contain a literal clause matching `passed != TOTAL` (in any equivalent shell form: `[[ "$passed" -ne "$TOTAL" ]]`, `$passed != $TOTAL`, etc.) that triggers `exit 1` when the condition is true. The original `if [[ "$failed" -gt 0 ]]; then exit 1; fi` is insufficient because it cannot detect the case where TOTAL is too high and some expected cases never emit a result at all.

### Sub-check (b) — TOTAL derived from a manifest

Bare-literal `TOTAL=NNN` declarations are forbidden. The harness MUST compute TOTAL at runtime from one of:
- `TOTAL=$((passed + failed))` after the result count is known (the simplest valid form), OR
- `TOTAL=${#KNOWN_CASES[@]}` from a declared manifest array, OR
- Any other expression that derives from observable script state.

This makes TOTAL self-correcting when cases are added or removed.

### Sub-check (c) — every Rule has a fixture

For every rule header `# Rule N — slug` in `gate/check_architecture_sync.sh`, the harness MUST contain at least one function whose name matches `test_rule_N_*` OR `test_ruleN_*` (legacy form). Inline top-level blocks are NOT accepted — they cannot be dispatched by the parallel orchestrator and their results are silently overwritten.

## Why three sub-checks

Each sub-check independently prevents a different failure mode the rc7 review surfaced:
- Sub-check (a) prevents `exit 0` when passed < TOTAL.
- Sub-check (b) prevents drift between a hardcoded constant and the actual case count.
- Sub-check (c) prevents inline-block test fixtures whose results get lost in the parallel aggregation.

Together, they make the harness's self-reported `Tests passed: X/Y` summary trustworthy as release evidence.

## Activation

Activated 2026-05-18 by the v2.0.0-rc7 post-corrective architecture review response wave (v2.0.0-rc8). Enforcer E122. Closes P1-1 of `docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md`.

## Cross-references

- Rule 86 (Root ARCHITECTURE Count + Path Truth) — sibling rc8 wave rule.
- Rule 87 (Status YAML Allowed Claim Module Name Truth) — companion rc6/rc7-rc8 gate.
- Rule 88 (Serial/Parallel Gate Slug Parity) — sibling rc8 wave rule; together with Rule 89 closes the two gate-truth integrity findings from the rc7 post-corrective review.
- Rule 82 (Baseline Metrics Single Source) — same family; both ensure numeric claims point back to an auditable single source.
- ADR-0082 (Canonical SPI ownership + topology-truth invariant, 2026-05-18) — names this rule as a downstream prevention surface.
- `docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md` finding P1-1 — origin.
- `docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review-response.en.md` — response document.

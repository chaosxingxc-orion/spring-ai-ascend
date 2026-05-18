---
rule_id: 88
title: "Serial/Parallel Gate Slug Parity"
level: L0
view: process
principle_ref: P-D
authority_refs: [ADR-0082, "v2.0.0-rc7 post-corrective review P0-2"]
enforcer_refs: [E121]
status: active
kernel_cap: 8
kernel: |
  **Canonical gate (`gate/check_architecture_sync.sh`) and parallel wrapper (`gate/check_parallel.sh`) MUST execute the same rule slug set. The canonical script MUST declare a `# === END OF RULES ===` terminator; the parallel awk MUST terminate on that marker. Every rule header MUST use em-dash `—` (`# Rule N — slug`); double-dash `--` is forbidden. Rule 88 fails closed on (a) parallel-manifest gap vs canonical, (b) double-dash separator, or (c) missing END marker. Closes rc7 P0-2 (parallel wrapper silently skipped Rules 86-87 via compound defect: marker + separator mismatch).**
---

# Rule 88 — Serial/Parallel Gate Slug Parity

## Motivation

The rc7 post-corrective architecture review (`docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md` finding P0-2) found that `gate/check_parallel.sh` exited 0 but silently skipped Rules 86 and 87. Family-sweep root-cause analysis surfaced two compounding defects, either of which alone would have caused the skip:

1. **Marker-mismatch**: the parallel wrapper's awk script terminated rule extraction at `^# Summary$`. The canonical script had a `# Summary` documentation header between Rule 85 and Rule 86 (left over from the rc6 prevention wave's history comment). Result: Rules 86-87 never made it into the manifest.
2. **Separator-mismatch**: the parallel wrapper's awk pattern required em-dash `—` separators (`# Rule N — slug`). Rules 86-87 were authored with double-dash `--` (`# Rule N -- slug`). Result: even if the `# Summary` marker were removed, the awk would still skip 86-87.

This is a class of integrity defect: **a gate that claims to execute every canonical rule but quietly skips some**. Any CI signal or reviewer relying on `check_parallel.sh` for fast verification would miss the newest rules — the most likely to catch drift in the youngest part of the corpus.

## Details

### Algorithm

Rule 88 runs the following sub-checks against `gate/check_architecture_sync.sh` and `gate/check_parallel.sh`:

1. **Slug-set parity**: extract `<rule_id>_<slug>` pairs from canonical headers (tolerant of both em-dash and double-dash for backwards compatibility during transition). Then run a SECOND awk extraction with the same logic the parallel wrapper uses (terminating at `^# === END OF RULES ===$`). The two sets MUST be equal. Any slug present in canonical but missing from parallel-set is reported as `parallel wrapper would skip rule(s): ...`. Any slug present in parallel-set but not as a `pass_rule "<slug>"` invocation is reported as `parallel awk would extract rule(s) not defined as canonical pass_rule blocks: ...`.

2. **Separator consistency**: grep canonical for `^# Rule [0-9]+[a-z]? -- ` (double-dash). Any match fails the rule. Only em-dash `—` is accepted going forward.

3. **END marker presence**: grep canonical for `^# === END OF RULES ===$`. Absence fails the rule.

### Why a marker, not a heuristic

The original wrapper's `^# Summary$` termination was a heuristic (the `# Summary` comment happened to be at the end of the rules in rc6 and earlier). When the rc7 prevention wave added Rules 86-87 AFTER `# Summary`, the heuristic broke without anyone noticing. An EXPLICIT marker (`# === END OF RULES ===`) declares the boundary up-front; any new rule added later goes BEFORE the marker by construction; the parallel wrapper can never silently truncate.

## Activation

Activated 2026-05-18 by the v2.0.0-rc7 post-corrective architecture review response wave (v2.0.0-rc8). Enforcer E121. Closes P0-2 of `docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md`.

## Cross-references

- Rule 86 (Root ARCHITECTURE Count + Path Truth) — sibling rc8 wave rule; both close P0 findings from the same review.
- Rule 87 (Status YAML Allowed Claim Module Name Truth) — companion rc6/rc7-rc8 gate.
- Rule 89 (Self-Test Harness Fail-Closed Coverage) — sibling rc8 wave rule; both close gate-truth integrity findings.
- ADR-0082 (Canonical SPI ownership + topology-truth invariant, 2026-05-18) — names this rule as a downstream prevention surface.
- `docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md` finding P0-2 — origin.
- `docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review-response.en.md` — response document.

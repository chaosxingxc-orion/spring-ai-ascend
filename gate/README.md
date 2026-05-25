# gate/ — Architecture-Sync Gate

> Document-corpus consistency checks for spring-ai-ascend. **135 active gate rules** (canonical bash, executable rule sections counted from `# Rule N — slug` headers), backed by **239 self-tests** (`gate/test_architecture_sync_gate.sh` derives the total at runtime). The canonical numbers live in [`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](../docs/governance/architecture-status.yaml) (single source of truth — Rule G-2 sub-clause .b numeric-agreement check rejects stale counts here; Rule G-5 sub-clause .c enforces `active_gate_checks` AND `enforcer_rows` against live extractors).
>
> **Python ≥ 3.10 required** for `gate/build_architecture_graph.py` and `gate/migrate_adrs_to_yaml.py`. Install once: `pip install -r gate/requirements.txt`. Rule R-H (`architecture_graph_well_formed`) fails fast with a clear message if PyYAML is missing.
>
> **Generated artefact:** `docs/governance/architecture-graph.yaml` (and its `.mmd` sibling) are produced by `gate/build_architecture_graph.py` and listed in `.gitignore`. Regenerate on demand; never hand-edit (Rule G-1 sub-clause .b).

## What is this?

The architecture-sync gate proves the document corpus is internally consistent at the current SHA — names, paths, counts, contracts, and wave-qualifier prose stay aligned with reality across `ARCHITECTURE.md`, the per-capability ledger, ADRs, contract catalogs, and release notes.

It also interprets Maven whitebox-quality reports for SpotBugs, PMD, and Checkstyle: high-confidence correctness/safety and hard-style findings are blocking, while PMD maintainability findings are review triggers.

It does **not** prove the running system behaves correctly. That is the operator-shape gate (`run_operator_shape_smoke.*`), which is fail-closed until a W4 runnable-artifact target lands.

## Canonical entrypoint

```bash
bash gate/check_parallel.sh                 # 135 active gate rules, parallel (~7min wall-clock); emits parallel_summary trailer per Rule G-5 sub-clause .a
bash gate/check_architecture_sync.sh        # 135 active gate rules, serial   (~24min wall-clock); terminates at # === END OF RULES === marker
bash gate/test_architecture_sync_gate.sh    # 239 self-tests (~20s); TOTAL derived at runtime per Rule G-5 sub-clause .b; fails closed when passed != TOTAL
python gate/build_architecture_graph.py     # regenerate the architecture-graph from canonical inputs
```

`gate/check_parallel.sh` is the wrapper used by CI. It reads
`gate/check_architecture_sync.sh`, splits it on `# Rule N — <slug>` markers,
round-robin distributes rules into 8 batches (override with `GATE_JOBS=N`),
and runs them in parallel. Identical PASS/FAIL semantics and deterministic
output ordering; opt out with `GATE_PARALLEL=0` to fall through to the
serial canonical script. Add `GATE_PROFILE=1` to dump per-rule wall-clock
to stderr.

Exit `0` and `GATE: PASS` if all rules pass; exit `1` and `GATE: FAIL` if any rule fails. Per-rule output is `PASS: <name>` or `FAIL: <name> -- <reason>`.

## PowerShell entrypoint is deprecated

`gate/check_architecture_sync.ps1` is a **fail-closed deprecation stub** as of v2.0.0-rc2. It was frozen at Rule R-A in 2026-05 while the bash gate evolved through Rules 28a–28k + 30–60. Authority: second-pass architecture review finding P0-1 (`docs/reviews/2026-05-16-l0-w2x-rc1-second-pass-architecture-review.en.md`). Gate Rule 61 (`legacy_powershell_gate_deprecated`) keeps the deprecation stub in place.

Run the bash entrypoint from Git Bash / WSL / any POSIX shell on Windows.

## Dev-only helpers (not architecture gates)

| File | Role | Notes |
|------|------|-------|
| `doctor.ps1` / `doctor.sh` | Environment probe — `APP_POSTURE`, required env vars, `mvnw` exec bit, Java availability | Convenience helpers. NOT a release gate; PowerShell ↔ bash parity is NOT enforced. |
| `run_operator_shape_smoke.ps1` / `.sh` | Fail-closed shells for the W4 operator-shape gate (no runnable artifact yet) | NOT a release gate; PowerShell ↔ bash parity is NOT enforced. |

## Files in this directory

| File | Role |
|------|------|
| `check_architecture_sync.sh` | **Canonical L0 release gate — 135 active executable sections. `gate/rules/` filenames stay numeric by design per ADR-0086 `gate_layer_boundary:` section (implementation-layer identifier vs semantic-authority namespace).** |
| `check_architecture_sync.ps1` | DEPRECATED. Fail-closed stub; see deprecation banner. |
| `test_architecture_sync_gate.sh` | Self-test harness — 239 self-test cases. `TOTAL` derived at runtime per Rule G-5.b. |
| `build_architecture_graph.py` | Regenerates `docs/governance/architecture-graph.yaml` from the authoritative inputs (Rule G-1 sub-clause .b). |
| `doctor.sh` / `doctor.ps1` | Dev-only env probe (not a gate). |
| `run_operator_shape_smoke.sh` / `.ps1` | Dev-only fail-closed smoke shells (not a gate). |
| `check_spring_ai_milestone.sh` | Spring AI milestone-version probe (separate concern). |
| `schema-first-grandfathered.txt` | Pipe-delimited grandfather list for Rule M-2 sub-clause .a / 60; every entry carries a `sunset_date`. |
| `rls-baseline-grandfathered.txt` | Grandfathered Flyway migrations for Rule R-J.a (RLS retrofit deferred to W2 per CLAUDE-deferred.md 40.b). |
| `log/` | Audit JSON files retained from earlier gate generations; the current canonical bash gate does not write here. |

## Rule catalog (current — see `check_architecture_sync.sh` header for the canonical comment block)

The bash script's header comment is the single source of truth for the rule list. The previous markdown table in this README was retired in v2.0.0-rc2 to eliminate dual-truth drift (the rules-table-as-prose became another F-α "parity-claim without enforcer" instance per the second-pass review). To browse the rule list, open `gate/check_architecture_sync.sh` and read the comment block lines 1–119.

## Self-test coverage

`gate/test_architecture_sync_gate.sh` runs 239 self-tests — positive + negative fixtures per the rules most prone to regression. The script prints `Tests passed: N/N` on success where `N` is derived at runtime per Rule G-5.b. Per Rule G-5 sub-clause .b / E122 sub-check (b), `TOTAL` is computed at runtime (`TOTAL=$((passed + failed))`) rather than declared as a bare literal; per sub-check (a) the harness exits non-zero when `passed != TOTAL` (fail-closed); per sub-check (c) every **prevention-wave Rule** (`N >= 80`) defined in `check_architecture_sync.sh` has at least one `test_rule_<N>_*` function in the harness — pre-rc4 Rules 1-79 are grandfathered (covered by ArchUnit / integration tests at design time, not by inline self-test fixtures). This scope narrowing aligns with `CLAUDE.md` Rule G-5 sub-clause .b kernel and `docs/governance/rules/rule-G-5.md`; rc9 corrected an earlier `enforcers.yaml` row + this README line that claimed broader "every Rule" coverage (rc8 post-corrective P1-4). The early `TOTAL=` near the top of the file was removed by the rc8 wave as dead code.

## See also

- [ARCHITECTURE.md](../ARCHITECTURE.md) — §4 #1–#65 are the constraints these rules enforce.
- [CLAUDE.md](../CLAUDE.md) — engineering Rule G-2 sub-clause .a (architecture-text truth) defines the prose-vs-enforcer contract; Rule R-C.a (Code-as-Contract) requires every active normative constraint to have an enforcer.
- [docs/governance/architecture-status.yaml](../docs/governance/architecture-status.yaml) — the per-capability ledger Rules 1, 7, 19, 24 read.
- [docs/governance/retracted-tags.txt](../docs/governance/retracted-tags.txt) — input for Rule 63 (`release_note_retracted_tag_qualified`).
- [docs/logs/reviews/2026-05-17-l0-w2x-rc1-second-pass-review-response.en.md](../docs/logs/reviews/2026-05-17-l0-w2x-rc1-second-pass-review-response.en.md) — v2.0.0-rc2 response document with the F-α / F-β / F-γ category audit that drove Rules 61–63.

# gate/ — Architecture-Sync Gate

> Document-corpus consistency checks for spring-ai-ascend. **108 active gate rules** (canonical bash, executable rule sections counted from `# Rule N — slug` headers; rc9 reconciliation closes the rc8 P0-1 baseline-vs-manifest gap — see ADR-0083), backed by **161 self-tests**. The canonical numbers live in [`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](../docs/governance/architecture-status.yaml) (single source of truth — Rule 82 numeric-agreement check rejects stale counts here). Recent waves: W1 Layered 4+1 + Architecture Graph (ADR-0068) added Rules 37–40; Phase M added Rules 41–44; W1.x L0-ironclad-rule wave Rules 45–52; W1.x Phases 8–9 Rules 53–54; W2.x Engine Contract Structural Wave Rules 55–60; v2.0.0-rc2 second-pass review closure Rules 61–63; 2026-05-17 cross-corpus consistency audit Rules 64–66 (enforcers E94–E96); 2026-05-18 Beyond-SDD review response Rule 79; 2026-05-18 rc4 cross-constraint review response Rules 80–83 (enforcers E113–E116); 2026-05-18 rc5 post-response review response Rules 84–85 + Rule 82 strengthening (enforcers E117–E118); 2026-05-18 rc6 post-response review response Rules 86–87 + ADR-0081 (enforcers E119–E120); 2026-05-18 rc7 post-corrective review response Rules 88–89 + Rule 86 fenced-tree-block extension + ADR-0082 (enforcers E121–E122).
>
> **Python ≥ 3.10 required** for `gate/build_architecture_graph.py` and `gate/migrate_adrs_to_yaml.py`. Install once: `pip install -r gate/requirements.txt`. Rule 38 (`architecture_graph_well_formed`) fails fast with a clear message if PyYAML is missing.
>
> **Generated artefact:** `docs/governance/architecture-graph.yaml` (and its `.mmd` sibling) are produced by `gate/build_architecture_graph.py` and listed in `.gitignore`. Regenerate on demand; never hand-edit (Rule 34).

## What is this?

The architecture-sync gate proves the document corpus is internally consistent at the current SHA — names, paths, counts, contracts, and wave-qualifier prose stay aligned with reality across `ARCHITECTURE.md`, the per-capability ledger, ADRs, contract catalogs, and release notes.

It does **not** prove the running system behaves correctly. That is the operator-shape gate (`run_operator_shape_smoke.*`), which is fail-closed until a W4 runnable-artifact target lands.

## Canonical entrypoint

```bash
bash gate/check_parallel.sh                 # 108 active gate rules, parallel (~7min wall-clock); emits parallel_summary trailer per Rule 88
bash gate/check_architecture_sync.sh        # 108 active gate rules, serial   (~24min wall-clock); terminates at # === END OF RULES === marker
bash gate/test_architecture_sync_gate.sh    # 161 self-tests (~20s); TOTAL derived at runtime per Rule 89; fails closed when passed != TOTAL
python gate/build_architecture_graph.py     # regenerate the architecture-graph
```

`gate/check_parallel.sh` is the wrapper used by CI. It reads
`gate/check_architecture_sync.sh`, splits it on `# Rule N — <slug>` markers,
round-robin distributes rules into 8 batches (override with `GATE_JOBS=N`),
and runs them in parallel. Identical PASS/FAIL semantics and deterministic
output ordering; opt out with `GATE_PARALLEL=0` to fall through to the
serial canonical script. Add `GATE_PROFILE=1` to dump per-rule wall-clock
to stderr.

Exit `0` and `GATE: PASS` if all rules pass; exit `1` and `GATE: FAIL` if any rule fails. Per-rule output is `PASS: <name>` or `FAIL: <name> -- <reason>`.

## PowerShell entrypoint is deprecated (v2.0.0-rc2)

`gate/check_architecture_sync.ps1` is a **fail-closed deprecation stub** as of v2.0.0-rc2. It was frozen at Rule 29 in 2026-05 while the bash gate evolved through Rules 28a–28k + 30–60. Authority: second-pass architecture review finding P0-1 (`docs/reviews/2026-05-16-l0-w2x-rc1-second-pass-architecture-review.en.md`). Gate Rule 61 (`legacy_powershell_gate_deprecated`) keeps the deprecation stub in place.

Run the bash entrypoint from Git Bash / WSL / any POSIX shell on Windows.

## Dev-only helpers (not architecture gates)

| File | Role | Notes |
|------|------|-------|
| `doctor.ps1` / `doctor.sh` | Environment probe — `APP_POSTURE`, required env vars, `mvnw` exec bit, Java availability | Convenience helpers. NOT a release gate; PowerShell ↔ bash parity is NOT enforced. |
| `run_operator_shape_smoke.ps1` / `.sh` | Fail-closed shells for the W4 operator-shape gate (no runnable artifact yet) | NOT a release gate; PowerShell ↔ bash parity is NOT enforced. |

## Files in this directory

| File | Role |
|------|------|
| `check_architecture_sync.sh` | **Canonical L0 release gate (108 active rules — executable section count; rc9 reconciliation, ADR-0083).** |
| `check_architecture_sync.ps1` | DEPRECATED (v2.0.0-rc2). Fail-closed stub; see deprecation banner. |
| `test_architecture_sync_gate.sh` | Self-test harness — 161 self-test cases covering Rules 1–6, 16, 19, 22, 24, 25, 26, 27, 28, 28j, 28k, 29, 54 (incl. rc7 ADR-0080 .spi negative), 60 (sunset), 61, 62, 63, 64, 65, 66, 79, 80, 81, 82 (incl. rc6 numeric-agreement strengthening), 83, 84, 85, 86 (incl. rc8 fenced-tree-block extension), 87, 88, 89. |
| `build_architecture_graph.py` | Regenerates `docs/governance/architecture-graph.yaml` from the authoritative inputs (Rule 34). |
| `doctor.sh` / `doctor.ps1` | Dev-only env probe (not a gate). |
| `run_operator_shape_smoke.sh` / `.ps1` | Dev-only fail-closed smoke shells (not a gate). |
| `check_spring_ai_milestone.sh` | Spring AI milestone-version probe (separate concern). |
| `schema-first-grandfathered.txt` | Pipe-delimited grandfather list for Rule 48 / 60; every entry carries a `sunset_date`. |
| `rls-baseline-grandfathered.txt` | Grandfathered Flyway migrations for Rule 40 (RLS retrofit deferred to W2 per CLAUDE-deferred.md 40.b). |
| `log/` | Audit JSON files retained from earlier gate generations; the current canonical bash gate does not write here. |

## Rule catalog (current — see `check_architecture_sync.sh` header for the canonical comment block)

The bash script's header comment is the single source of truth for the rule list. The previous markdown table in this README was retired in v2.0.0-rc2 to eliminate dual-truth drift (the rules-table-as-prose became another F-α "parity-claim without enforcer" instance per the second-pass review). To browse the rule list, open `gate/check_architecture_sync.sh` and read the comment block lines 1–119.

## Self-test coverage

`gate/test_architecture_sync_gate.sh` runs 161 self-tests — positive + negative fixtures per the rules most prone to regression. The script prints `Tests passed: 161/161` on success. Per Rule 89 / E122 sub-check (b), `TOTAL` is computed at runtime (`TOTAL=$((passed + failed))`) rather than declared as a bare literal; per sub-check (a) the harness exits non-zero when `passed != TOTAL` (fail-closed); per sub-check (c) every **prevention-wave Rule** (`N >= 80`) defined in `check_architecture_sync.sh` has at least one `test_rule_<N>_*` function in the harness — pre-rc4 Rules 1-79 are grandfathered (covered by ArchUnit / integration tests at design time, not by inline self-test fixtures). This scope narrowing aligns with `CLAUDE.md` Rule 89 kernel and `docs/governance/rules/rule-89.md`; rc9 corrected an earlier `enforcers.yaml` row + this README line that claimed broader "every Rule" coverage (rc8 post-corrective P1-4). The early `TOTAL=` near the top of the file was removed by the rc8 wave as dead code.

## See also

- [ARCHITECTURE.md](../ARCHITECTURE.md) — §4 #1–#65 are the constraints these rules enforce.
- [CLAUDE.md](../CLAUDE.md) — engineering Rule 25 (architecture-text truth) defines the prose-vs-enforcer contract; Rule 28 (Code-as-Contract) requires every active normative constraint to have an enforcer.
- [docs/governance/architecture-status.yaml](../docs/governance/architecture-status.yaml) — the per-capability ledger Rules 1, 7, 19, 24 read.
- [docs/governance/retracted-tags.txt](../docs/governance/retracted-tags.txt) — input for Rule 63 (`release_note_retracted_tag_qualified`).
- [docs/reviews/2026-05-17-l0-w2x-rc1-second-pass-review-response.en.md](../docs/reviews/2026-05-17-l0-w2x-rc1-second-pass-review-response.en.md) — v2.0.0-rc2 response document with the F-α / F-β / F-γ category audit that drove Rules 61–63.

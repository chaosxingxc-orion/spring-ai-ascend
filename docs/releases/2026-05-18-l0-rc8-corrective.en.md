---
release_tag: v2.0.0-rc8
release_date: 2026-05-18
release_type: corrective_uplift
supersedes_tag: v2.0.0-rc7
retracts_tag: null
authority: docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md
response_doc: docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review-response.en.md
---

# v2.0.0-rc8 — rc7 post-corrective review response (2026-05-18)

## One-liner

> v2.0.0-rc8 closes the rc7 post-corrective review gaps by aligning the GraphMemoryRepository ownership corpus, restoring serial/parallel gate parity, and making the gate self-test harness fail closed on summary-count drift.

## Baseline counts (post-rc8)

| metric | count |
|---|---|
| §4 constraints | 65 |
| Active ADRs | 82 |
| Layer-0 governing principles | 13 |
| Active engineering rules | 45 |
| Active gate rules | 74 |
| Gate self-test cases | 149 |
| Enforcer rows | 104 |
| Maven tests GREEN (under `./mvnw verify`) | 371 |
| Architecture graph | 348 nodes / 486 edges (regenerated at verification time) |

Canonical structured single-source: [`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](../governance/architecture-status.yaml).

**Deltas vs `v2.0.0-rc7`** (additive uplift only — rc7 NOT retracted; no production Java behavioural change):

- Active engineering rules **+2** — Rules 88, 89 (rc7 post-corrective review response prevention wave).
- Active gate rules **+2** — `serial_parallel_gate_slug_parity`, `self_test_harness_fail_closed_coverage`. Note: the rc7 baseline `72` was the count the broken parallel wrapper executed (it silently skipped Rules 86-87); after rc8's Rule 88 parity fix, the canonical script and parallel wrapper both execute 74 rules, which is the new true baseline. Rule 88 enforces this going forward.
- Gate self-tests **+6** — 2 per Rule 86 fenced-tree-block extension (positive + negative) + 2 per Rule 88 (positive + negative) + 2 per Rule 89 (positive + negative).
- Enforcer rows **+2** — E121, E122.
- Active ADRs **+1** — ADR-0082 (`GraphMemoryRepository canonical ownership + module-metadata.yaml is single source of truth for SPI ownership topology`).
- Architecture graph **+7 nodes / +12 edges (measured 348/486)** — Rules 88/89 + cards + E121/E122 + ADR-0082 + Rule 86 amendment edges.
- §4 constraints / Layer-0 principles — no change (no production Java behavioural change).
- Maven tests GREEN — unchanged at 371 (no Java touched in rc8; documentation-truth + gate-integrity wave only).

## Summary

v2.0.0-rc8 is a **corrective uplift** on rc7 that closes the 5 findings from the Codex L0 rc7 post-corrective architecture review (`docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md`) plus 14 hidden defects surfaced by the per-family corpus sweep (G-α 5 hidden, G-β 9 hidden; the reviewer's 5 cited findings expand to 19 instances when each surface is enumerated separately). The runtime architecture was judged directionally sound by the review with no overdesign found; this wave is **documentation-truth + gate-integrity** work, exactly the class the rc4→rc7 prevention waves (Rules 80-87) were designed to police. **rc7 is NOT retracted** — its prose error (line 49 P0-2 closure overstating tree placement of memory.spi) is now corrected, and rc7 carries an inline historical marker pointing at rc8.

The reviewer's central observation — *"the latest rc7 corrective wave fixed the previous six findings, but introduced or left behind three authority/verification gaps in the memory SPI and gate surfaces"* — is closed by four structural changes: (a) GraphMemoryRepository ownership corpus reconciled across 7 authority surfaces with `module-metadata.yaml#spi_packages` ∩ actual Java file path pinned as canonical SSOT (ADR-0082); (b) parallel wrapper's awk extraction made tolerant of em-dash/double-dash separator + an explicit `# === END OF RULES ===` terminator marker (Rule 88); (c) self-test harness `TOTAL` derived at runtime + fail-closed `passed != TOTAL` clause + every Rule has a `test_rule_<N>_*` fixture (Rule 89); (d) Rule 86 extended with a fenced-tree-block second pass that closes the regex-blind-spot that allowed the original rc7 drift to escape.

## Closure of the 5 findings + 14 hidden defects

- **P0-1 (Memory SPI ownership inconsistent after rc7)** — chose reviewer's path 1 (documentation-only correction; ADR-0082 records the canonical owner decision and why path 2's architectural move was rejected). 5 surfaces aligned: root `ARCHITECTURE.md:163-170` tree (memory/spi row moved from agent-runtime-core block to agent-service/runtime/ block + agent-runtime-core annotated "Memory SPI is OUT OF SCOPE per ADR-0079/ADR-0082" + ghost `S2cCallbackOutcome` reference removed); `docs/governance/architecture-status.yaml:1410` allowed_claim rewritten to enumerate memory.spi under agent-service; `docs/dfx/spring-ai-ascend-graphmemory-starter.yaml:32` pre-Phase-C path replaced with agent-service path + ADR-0082 pointer; `docs/dfx/agent-runtime.yaml` (pre-Phase-C orphan file) deleted; `docs/releases/2026-05-18-l0-rc7-corrective.en.md` carries `superseded_by` frontmatter + inline historical marker noting the line-49 overstatement. **Prevention:** Rule 86 fenced-tree-block extension (E119 scope widened).
- **P0-2 (Parallel gate silently omits Rules 86-87)** — **compound defect** — family sweep revealed two compounding causes the reviewer cited only as single-cause. Fix #1: canonical script's `# Summary` documentation header (which `^# Summary$` regex matched and stopped awk extraction) renamed to `# Wave history (rc6 -> rc7 -> rc8 prevention waves)`; explicit `# === END OF RULES ===` terminator marker added at line 4323. Fix #2: Rules 86/87 separators normalised from `--` to `—` (matches Rules 1-85); awk pattern made tolerant of both as defence in depth. Plus: `parallel_summary: executed N rules; serial source defined N rules` trailer added to check_parallel.sh; exit non-zero if counts disagree. **Prevention:** Rule 88 (`serial_parallel_gate_slug_parity`, E121) + 2 new self-tests (`test_rule88_serial_parallel_parity_pos` + `test_rule88_separator_neg`).
- **P1-1 (Gate self-test summary is not trustworthy enough for release evidence)** — three sub-fixes: (i) dead `TOTAL=138` at line 43 removed; (ii) hardcoded `TOTAL=143` at line 4098 replaced with `TOTAL=$((passed + failed))` (manifest-derived at runtime); (iii) exit logic now fails closed when `passed != TOTAL`, not only when `failed > 0`. Plus: inline Rule 86/87 fixtures (lines 3942-4087) wrapped as proper `test_rule86_*()` / `test_rule87_*()` functions so the parallel orchestrator picks them up — previously their results were silently overwritten when the orchestrator reset the passed/failed counters at line 4154-4157. **Prevention:** Rule 89 (`self_test_harness_fail_closed_coverage`, E122) with three sub-checks codifying the three invariants.
- **P1-2 (Module-level authority text still lags ADR-0079)** — `agent-runtime-core/ARCHITECTURE.md` §2 Contents rewritten to authoritatively enumerate all 15 Java surfaces (4 runs entities + 1 runs SPI + 6 orchestration SPI + 3 s2c SPI + 1 idempotency entity); memory SPI explicitly called out as out-of-scope per ADR-0082; RunRepository path corrected from `runs/RunRepository.java` to `runs/spi/RunRepository.java`. `agent-service/module-metadata.yaml:10` description rewritten: `service.runtime` now owns memory.spi + resilience.spi + reference adapters; orchestration/runs/s2c SPI extracted to agent-runtime-core per ADR-0079; consolidated from agent-platform + agent-runtime per ADR-0078; canonical SPI ownership pinned by ADR-0082.
- **P2-1 (ADR-0081 verification text corrupt/stale)** — sweep clarified: the `≥` character is UTF-8-valid U+2265 (not mojibake); the real defect is the stale count `≥142/142` vs current baseline 149/149. Rewritten line 132 with ASCII `=` to `Tests passed: 149/149 (rc8 baseline)`; added a `check_parallel.sh` parity citation per Rule 88.

**Hidden defects closed by family sweep** (14 instances; full table in the response doc):

1. `docs/dfx/agent-runtime.yaml` orphan DFX file for deleted pre-Phase-C module — **deleted**.
2. `S2cCallbackOutcome` ghost reference in root ARCHITECTURE.md tree (no Java file exists) — removed.
3. Rule 86 fenced-code-block exclusion was the regex-blind-spot where rc7 drift hid — **extended with 2nd pass**.
4. Compound defect: separator mismatch on Rules 86/87 (beyond the `# Summary` marker single-cause) — **separator normalised**.
5. Dead `TOTAL=138` at test harness line 43 — removed.
6. ADR-0081 line 132 character `≥` is valid UTF-8 but count is stale — refreshed.
7. README.md baseline-prose disagreement (72/143 → 74/149) — refreshed.
8. `gate/README.md` baseline-prose disagreement — refreshed.
9. `baseline_metrics.active_gate_checks: 72` encoded the parallel-skip defect — updated to 74.
10. No serial/parallel parity self-test existed — **Rule 88 codifies**.
11. Inline Rule 86/87 test fixtures were silently overwritten by the parallel orchestrator — wrapped as proper functions.
12. Old release-note + ADR-0081 cited `143/143` while harness on Git Bash for Windows actually produced `37/143` — baseline now 149/149 (Linux/WSL); Git Bash shows 146/149 (3 E2-only failures per Rule 74).
13. agent-runtime-core's apparent surface area was slightly smaller than rc7 prose implied — §2 Contents rewrite makes the actual 15-surface enumeration the ground truth.
14. Rule 82 single-source enforcement made every other surface "correctly cite the broken value" — Rule 82 strengthening + new Rules 88/89 collectively make baseline drift detection robust to single-source corruption.

## Doc-precision addendum (in-scope per user direction)

- **ADR-0082** new file — records the canonical owner decision + module-metadata.yaml SSOT rule + path 2 rejection rationale. Cross-refs ADR-0034 / ADR-0051 / ADR-0078 / ADR-0079 / ADR-0080 / ADR-0081.

## New prevention gates + ADR (rc8)

| Rule | Slug | Closes | Enforcer |
|---|---|---|---|
| 88 | `serial_parallel_gate_slug_parity` | rc7 P0-2 prevention (compound defect: marker + separator) | E121 |
| 89 | `self_test_harness_fail_closed_coverage` | rc7 P1-1 prevention (fail-open + hardcoded total + missing fixture parity) | E122 |
| 86 amended | `root_architecture_count_and_path_truth` (fenced-tree-block 2nd pass) | rc7 P0-1 prevention (regex-blind-spot) | E119 (scope widened) |

Each rule lands as one inline section in `gate/check_architecture_sync.sh` with two self-tests in `gate/test_architecture_sync_gate.sh`. Cards: `docs/governance/rules/rule-88.md` (new), `rule-89.md` (new), `rule-86.md` (amended). `CLAUDE.md` Rule 88 + Rule 89 added under a new `### rc7 post-corrective review response prevention wave (2026-05-18)` heading; Rule 86 kernel rewritten to declare the fenced-tree-block extension.

**ADR-0082** — `GraphMemoryRepository canonical ownership + module-metadata.yaml is single source of truth for SPI ownership topology`. Extends ADR-0034, ADR-0051, ADR-0078, ADR-0079. Relates to ADR-0080, ADR-0081.

## Architecture baseline (post-rc8)

Canonical structured baseline lives in [`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](../governance/architecture-status.yaml). Headline numbers (rc8):

- 45 active engineering rules (rc7 baseline 43 + rc8 wave +2 Rules 88-89).
- 13 Layer-0 governing principles (P-A..P-M, unchanged).
- 74 active gate rules (rc7 baseline 72 + rc8 wave +2; the rc7 `72` itself was the broken parallel wrapper's count — Rule 88 now enforces the truth).
- 149 gate self-tests (rc7 baseline 143 + rc8 wave +6); on Linux/WSL `Tests passed: 149/149`; on Git Bash for Windows `Tests passed: 146/149` with 3 E2-only failures per Rule 74.
- 104 enforcer rows (rc7 baseline 102 + rc8 wave +2 E121-E122).
- 65 §4 constraints in ARCHITECTURE.md (unchanged).
- 82 ADRs (rc7 baseline 81 + rc8 wave +1 ADR-0082).
- 371 Maven tests GREEN under `./mvnw verify` — 277 surefire + 94 failsafe (unchanged; rc8 touched zero Java).
- 348 architecture-graph nodes / 486 architecture-graph edges (rc7 baseline 341 / 474; +7 nodes / +12 edges (measured 348/486) from Rules 88/89 + cards + E121/E122 + ADR-0082 + Rule 86 amendment; exact numbers remeasured at verification).

## Four competitive pillars (P-B baselines)

This wave preserves all four pillar baselines unchanged. Pillar names as declared in `docs/governance/competitive-baselines.yaml`:

- **performance** — no runtime code changes; baseline preserved.
- **cost** — no runtime code changes; baseline preserved.
- **developer_onboarding** — preserved (quickstart unchanged; ADR-0082 records SPI ownership SSOT so future onboarding doesn't re-derive the question).
- **governance** — baseline strengthened: Rules 88/89 + Rule 86 amendment close the rc7 review's three highest-priority gate-truth and corpus-truth findings; the family sweep methodology surfaced 14 hidden defects beyond the reviewer's 5 cited (a yield 4× higher than rc7's wave per Rule 1 root-cause-first discipline).

## Verification commands

```bash
bash gate/test_architecture_sync_gate.sh   # -> Tests passed: 149/149 (Linux/WSL); 146/149 on Git Bash for Windows
bash gate/check_architecture_sync.sh       # -> GATE: PASS (74 active gate rules)
bash gate/check_parallel.sh                # -> GATE: PASS; parallel_summary: executed 74 rules; serial source defined 74 rules
python gate/build_architecture_graph.py    # -> 348 nodes / 486 edges; Graph validation: OK
./mvnw clean verify                        # -> BUILD SUCCESS (371 tests GREEN: 277 surefire + 94 failsafe)
```

Linux/WSL verification per Rule 74 (E2 NDJSON self-tests require `python3` in PATH; Git Bash for Windows may show 3 E2-only failures unrelated to the rc8 wave surface — the fail-closed exit at rc8 correctly fails the harness in that environment, which is the intended behaviour).

## Tag posture

Tag **v2.0.0-rc8** supersedes v2.0.0-rc7. v2.0.0-rc7 is **not** retracted (corrective uplift; no behavioural regression). v2.0.0-w2x-final remains retracted per `docs/governance/retracted-tags.txt`. rc7's inline historical marker points readers to this rc8 release note and the rc7 post-corrective response doc.

## Cross-references

- Review: `docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md`.
- Response: `docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review-response.en.md`.
- Prior wave release note: `docs/releases/2026-05-18-l0-rc7-corrective.en.md` (v2.0.0-rc7; historical).
- ADR-0082: `docs/adr/0082-graphmemory-ownership-canonical-and-topology-truth.yaml`.
- Rule cards: `docs/governance/rules/rule-86.md` (amended) · `rule-88.md` (new) · `rule-89.md` (new).
- Rule history: `docs/governance/rule-history.md` (rc8 entry).

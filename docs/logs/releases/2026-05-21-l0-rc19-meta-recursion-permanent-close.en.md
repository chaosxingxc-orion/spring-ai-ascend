---
level: L0
view: process
affects_level: L0
affects_view: process
release_tag: v2.0.0-rc19
date: 2026-05-21
authority: ADR-0096
supersedes_release_notes:
  - docs/logs/releases/2026-05-21-l0-rc18-comprehensive-hardening.en.md
---

# L0 v2.0.0-rc19 — Meta-Recursion Permanent Close + Stragglers + Runbook

## Verdict

rc19 closes the rc18 reviewer's "recursive irony has 4 deeper layers"
finding. Where rc18 Wave 1 closed the surface bypasses via helper
extraction, rc19 Wave 1 closes the 4 structural assumptions reviewers
found in the helper itself (awk parser fragility, hand-edited last_updated
proxy, narrow signal-path filter, missing META-of-META gate).

**5 waves, 5 PRs, all merged green:**
- Wave 1 #21 → b1cd29a (python yaml parser + Rule 112 META-of-META)
- Wave 3 #22 → cc13298 (doc cleanup: README count, schema enum, prose softening)
- Wave 4 #23 → 3d12c88 (runbook + Rule 114 filename gate)
- Wave 2 #24 → (Rule 113 paren guard + 11 stale R-C ref sweep)
- Wave 5 #25 → (this PR; ADR-0096 + release note + baseline + freeze rc18)

**Methodology:** Wave 1 closes the headline. Waves 2/3/4 run in parallel
(independent files). Wave 5 finalizes. Multi-wave runbook (NEW) now
codifies the pattern.

## Adversarial findings closed (8 of 8 critical/high)

| Finding | rc19 fix | Wave |
|---|---|---|
| ADV-RC18-1 (no-op commit bypasses freshness) | content-diff comparison via git show | 1 |
| ADV-RC18-2 (9999-12-31 + format-only date check) | datetime.date.fromisoformat + today check | 1 |
| ADV-RC18-3 (path filter too narrow) | derive paths from families[].surfaces[] | 1 |
| ADV-RC18-4 (awk literal-block injection) | pyyaml parser correctly treats as string | 1 |
| ADV-RC18-5 (shallow-clone on git <2.15) | .git/shallow marker fallback | 1 |
| ADV-RC18-6 (paren reintroduction ungated) | Rule 113 sub-check .a | 2 |
| ADV-RC18-7 (F-recursive permanent close) | Rule 112 META-of-META gate | 1 |
| ADV-RC18-8 (composite attack) | all 3 mechanisms above defeated | 1 |

## Maintainability findings closed

| Finding | rc19 fix | Wave |
|---|---|---|
| Maint MAINT-rc18-3 (schema enum stale) | added `monitoring` to header comment | 3 |
| Maint MAINT-rc18-4 (filename convention ungated) | Rule 114 + 2 fixtures | 4 |
| Maint MAINT-rc18-8 (README 16 vs 17) | corrected count | 3 |
| Maint MAINT-rc18-9 (zero-correctives claim false) | softened with truthful disclosure | 3 |
| Maint MAINT-rc18-10 (no runbook) | NEW docs/runbooks/multi-wave-release.md | 4 |
| Maint MAINT-rc18-12 (migration.md ungated) | Rule 113 sub-check .b | 2 |

## Architectural findings closed

| Finding | rc19 fix | Wave |
|---|---|---|
| Arch Q1 (Wave 3+4 stragglers) | 11 stale R-C ref sweep across status.yaml/P-C.md/P-J.md | 2 |
| Arch Q3 (Pattern D mitigation lie) | Rule 112 enforces helper-call for new META; KD-rc19-1 records remaining 21-fixture debt | 1 |
| Arch Q5 (migration.md no gate) | Rule 113 sub-check .b | 2 |
| Arch Q6 (F-recursive informational only) | Rule 112 makes it structural | 1 |
| Arch Q7 (zero-correctives claim false) | rc18 release note prose updated | 3 |
| Correctness Finding 1 (cleanup_status trailing comment) | pyyaml proper parsing | 1 |
| Correctness Finding 2 (md regex asymmetric) | widened to [A-Za-z0-9_-] | 1 |

## Baseline metrics

| Metric | rc18 | rc19 | Delta |
|---|---|---|---|
| §4 constraints | 65 | 65 | unchanged |
| Active ADRs | 94 | 95 | +1 (ADR-0096) |
| Active gate rules | 123 | 126 | +3 (Rule 112 + 113 + 114) |
| Gate self-test cases | 210 | 220 | +10 (5 Rule 111 ADV closure + 2 Rule 112 + 2 Rule 113 + 2 Rule 114 - 1 dedup) |
| Active engineering rules | 37 | 37 | unchanged (rc19 is pure hardening) |
| Enforcer rows | 157 | 160 | +3 (E159 + E160 + E161) |
| Layer-0 governing principles | 13 | 13 | unchanged |
| Reactor modules | 8 | 8 | unchanged |
| Architecture graph nodes | 395 | 402 | +7 (3 rules + 3 enforcers + ADR-0096 node) |
| Architecture graph edges | 642 | 656 | +14 (2 per new gate-rule rule→enforcer + enforcer→artefact for Rules 112/113/114 + ADR-0096 supersedes / extends / relates_to edges) |
| Recurring defect families | 9 | 9 | unchanged (F-recursive-prevention-irony promoted closed) |
| Maven tests green | 374 | 374 | unchanged (no Java code changes) |

## Pillar coverage (per Rule R-B / ADR-0065)

- **performance** — no change.
- **cost** — no change.
- **developer_onboarding** — improved. NEW multi-wave-release runbook
  removes tribal knowledge for the 5-wave pattern. Rule 114 prevents
  the dot-vs-hyphen filename trap that bit rc17.
- **governance** — significantly strengthened. Rule 112 closes the
  META-of-META gap; Rule 113 closes the legacy-paren reintroduction;
  Rule 114 closes the filename trap; python yaml replaces fragile awk;
  content-diff freshness replaces mtime proxy.

## What shipped per wave

### Wave 1 (#21, b1cd29a) — Meta-Recursion Permanent Close

- NEW `gate/lib/validate_recurring_families.py` (pyyaml-based parser)
- Updated `gate/lib/check_recurring_families.sh` (bash shim → python)
- NEW Rule 112 + E159 (META-of-META gate; dogfoods itself)
- Rule 110 source-statement added (dogfooding self-application)
- 5 new Rule 111 fixtures + 2 Rule 112 fixtures
- ci.yml fetch-depth: 0 (already added in rc18 Wave 1)
- F-recursive-prevention-irony cleanup_status promoted → closed

### Wave 2 (#24) — Wave 3+4 Stragglers + Rule 113

- Sweep 11 stale `Rule R-C.{b,c,d,e}` refs (status.yaml/P-C.md/P-J.md)
- "Gate Rule R-C.1 quickstart_present" typo fixed
- NEW Rule 113 + E160 (legacy paren guard + migration.md completeness)
- 2 Rule 113 fixtures

### Wave 3 (#22, cc13298) — Doc Cleanup

- README.md R-rule count 16 → 17 (Maint MAINT-rc18-8)
- Family yaml schema header comment + `monitoring` enum (Maint MAINT-rc18-3)
- rc18 release note "Zero corrective commits" softened to "Zero post-merge corrective commits on main" with disclosure

### Wave 4 (#23, 3d12c88) — Runbook + Filename Gate

- NEW `docs/runbooks/multi-wave-release.md` (codifies 5-wave pattern)
- NEW Rule 114 + E161 (filename convention gate)
- 2 Rule 114 fixtures

### Wave 5 (this PR) — META Finalize

- NEW `docs/adr/0096-rc19-meta-recursion-permanent-close.yaml`
- NEW this release note
- baseline_metrics lockstep update (active_gate_checks 126,
  gate_executable_test_cases 220, enforcer_rows 160, adr_count 95,
  architecture_graph_nodes 402, architecture_graph_edges 656)
- allowed_claim prose update for rc19
- README.md baseline line update
- rc18 release note frozen with `Historical artifact frozen at SHA
  61a000e` marker
- `docs/governance/rule-history.md` rc19 section
- MEMORY index update

## What did NOT ship (intentional out-of-scope)

- **21 legacy fixture helper-isations** (KD-rc18-1 → KD-rc19-1) still
  deferred. Rule 112 enforces helper-call for new META rules but doesn't
  retrofit non-META legacy fixtures. Opportunistic per-rule-touch.
- Pre-rc16 grandfathered rules without `scope_surfaces:` frontmatter.

## In-branch corrective commits (3 total, all squashed on main)

| Wave | Correctives | Cause |
|---|---|---|
| 1 | 3 | (a) ci.yml fetch-depth + Rule 110 dogfooding; (b) baseline lockstep + Rule 87 marker fix; (c) family yaml content bump (Rule 111.b firing on Wave 1's own commit) |
| 2 | 1 | (a) Rule 113 fixtures + family yaml content bump |
| 4 | 1 | (a) Rule 114 fixtures + family yaml content bump |

Total: 5 in-branch correctives; 0 post-merge correctives on `main`.
The lockstep discipline (per `feedback_lockstep_baseline_surfaces.md`)
held at the merge boundary.

## Verification

- [x] 215/218 self-tests pass on Windows Git Bash (3 pre-existing python3 env failures)
- [x] All Rule 111 fixtures (14 total) PASS with new python validator
- [x] All Rule 112 fixtures (2) PASS
- [x] All Rule 113 fixtures (2) PASS
- [x] All Rule 114 fixtures (2) PASS
- [x] Python validator standalone smoke: 3 sub-checks PASS on real corpus
- [x] Graph: 402 nodes / 656 edges
- [x] gate/rule-number-migration.md has expected section headings
- [x] enforcers.yaml has 0 `(legacy Rule NN ...)` parens
- [ ] CI green on PR #25 (this Wave 5)

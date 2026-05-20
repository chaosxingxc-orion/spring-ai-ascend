---
level: L0
view: process
affects_level: L0
affects_view: process
release_tag: v2.0.0-rc17
date: 2026-05-21
authority: ADR-0094
supersedes_release_notes:
  - docs/logs/releases/2026-05-20-l0-rc16-recurring-family-comprehensive-closure-and-meta-scope.en.md
---

# L0 v2.0.0-rc17 — Recurring-Defect Family Truth + Rule Consolidation

> **Historical artifact frozen at SHA 9eb3045.** This release note captures
> rc17 wave-final baselines (93 ADRs / 123 active gate rules / 37 active
> engineering rules / 205 self-tests / 407 graph nodes / 643 edges / 8
> recurring defect families). For current canonical baselines see
> `docs/governance/architecture-status.yaml#architecture_sync_gate.allowed_claim`.
> rc18 (per ADR-0095) superseded these counts: 94 ADRs / 123 gate rules
> (unchanged — rc18 is pure hardening of rc17) / 37 engineering rules
> (unchanged) / 210 self-tests / 394 graph nodes / 634 edges / 9 families.
> Rule 28 exempts this file via the frozen-SHA marker convention.

## Verdict

rc17 closes the user-initiated reflection wave: chao read the rc4 → rc16 release notes and asked "已经发现的错误类别已经做到完全清理了吗？" (Are the already-discovered error classes fully cleaned up?). A family-recurrence audit confirmed the user's intuition — 4 root-cause classes had recurred 6-9 times each. The pattern was the defect.

rc17 institutionalises recurring-defect-family knowledge as a first-class corpus citizen (yaml SSOT + md human view + gate freshness check) and simultaneously consolidates 4 rule kernels whose sub-clauses had drifted across orthogonal domains.

**Per-deliverable outcome:**
- **Family ledger** — 8 root-cause classes catalogued in `docs/governance/recurring-defect-families.{yaml,md}`, each with surfaces / prevention chronology / cleanup status / open residual.
- **Rule G-9** — new L0 rule gating ledger freshness. Gate Rule 111 + E156/E157/E158 + 3 self-test fixtures. Card declares `scope_surfaces:` (6 surfaces) per Rule 110 META.
- **Rule G-3 → G-3 + G-3.1** — disjunction-grammar invariant extracted from kernel-card structural-coherence invariant.
- **Rule R-I → R-I + R-I.1** — shipped-W1 manifest separated from W3+-deferred ingress routing.
- **Rule G-2 → G-2 + G-2.1** — per-surface authority-text truth separated from cross-surface deleted-module-scope prevention.
- **Rule R-C → R-C + R-C.1 + R-C.2** — code-as-contract / module evolution / run spine split into three coherent rules.
- **`/refresh-defect-archive` skill** — developer-facing companion at `.claude/skills/refresh-defect-archive.md`.
- **`docs/governance/rules/README.md`** — taxonomy documentation for D-/R-/G-/M- prefixes + `.1`/`.2` sub-rule naming convention introduced in rc17.

**Methodology:** Take rc16 Rule 110 META's discipline ("declare scope_surfaces + ≥2 fixtures") one level up — institutionalise the RECURRENCE pattern itself as a gateable artefact. Family ledger is the structural backstop; Rule G-9 ensures it stays fresh.

## Family taxonomy

| Family ID | Class | rc Occurrences | rc17 cleanup |
|---|---|---|---|
| **F-numeric-drift** | Numeric drift across authority surfaces | 9 | partial — catalogued; Rule G-8.a + 82/91/97/101 cover most surfaces |
| **F-deleted-module-name-leakage** | Deleted-module names persist after refactor | 6 | **structurally addressed** — Rule G-2.1 consolidates 94/98/103/109 |
| **F-authority-surface-path-drift** | Authority surfaces lag code refactors 2-6 waves | 8 | partial — crosses with deleted-module family |
| **F-kernel-vs-implementation-drift** | Prevention rule kernel ≠ gate impl | 4 | partial — Rule G-9 frontmatter discipline is the next backstop |
| **F-cross-authority-agreement** | 117 single-surface rules pass, surfaces disagree | 3 | structurally addressed rc14-16 (Rule G-8 + 106-109) |
| **F-deferred-clause-orphan** | CLAUDE-deferred.md sync drift | 3 | partial — Rule 99/107 cover bilateral; three-way sync still author-driven |
| **F-shadow-corpus-prose-staleness** | gate/rules/ comment drift | 6 | partial — Rule 92/97/109 cover known surfaces |
| **F-terminal-verb-overclaim** | Active kernels say "SUSPENDED" for W2-deferred behaviour | 3 | **closed rc16** — Rule 108 widens to all active corpus |

## Baseline metrics

| Metric | Count | Delta (rc16 → rc17) | Rationale |
|---|---|---|---|
| §4 constraints | 65 | unchanged | No new §4 invariants |
| Active ADRs | 93 | +1 | ADR-0094 (rc17 recurring-defect-family-truth + rule-consolidation) |
| Active gate rules | 123 | +1 | Rule 111 architecture_refresh_defect_family_re_eval_required |
| Gate self-test cases | 205 | +3 | test_rule_111_yaml_wellformed_pos + test_rule_111_yaml_wellformed_neg + test_rule_111_md_yaml_parity_neg |
| Active engineering rules | 37 | +6 | R-C → R-C + R-C.1 + R-C.2 (+2); R-I → R-I + R-I.1 (+1); G-3 → G-3 + G-3.1 (+1); G-2 → G-2 + G-2.1 (+1); new Rule G-9 (+1) |
| Enforcer rows | 157 | +3 | E156 yaml-wellformed + E157 freshness + E158 yaml/md parity |
| Layer-0 governing principles | 13 | unchanged | P-A..P-M unchanged |
| Reactor modules | 8 | unchanged | Same 8-module post-rc13 reactor |
| Architecture graph nodes | TBD reconciled post-edit | est. +15 vs rc16 (396) | ADR-0094 + 6 new rule cards (G-3.1/R-I.1/G-2.1/R-C.1/R-C.2/G-9) + 3 enforcer rows + family ledger artefacts + skill |
| Architecture graph edges | TBD reconciled post-edit | est. +25 vs rc16 (615) | ADR-0094 supersedes/extends chains + new rule-card↔enforcer↔fixture edges + principle-coverage updates |
| **Recurring defect families** | **8** | **+8 (new tracked metric)** | F-numeric-drift, F-deleted-module-name-leakage, F-authority-surface-path-drift, F-kernel-vs-implementation-drift, F-cross-authority-agreement, F-deferred-clause-orphan, F-shadow-corpus-prose-staleness, F-terminal-verb-overclaim |
| Maven tests green | 374 | unchanged | No Java code deltas in rc17 |

## Pillar coverage (per Rule R-B / ADR-0065)

- **performance** — no change.
- **cost** — no change.
- **developer_onboarding** — improved. Reviewers can now answer "has this class been seen before?" via one yaml/md pair instead of 16 release notes. New `docs/governance/rules/README.md` documents the prefix taxonomy and sub-rule convention for cold readers.
- **governance** — strengthened. Rule G-9 catches family-ledger staleness on every architecture refresh. Rule splits clarify which sub-rule owns which invariant.

## What shipped

### Recurring-defect-family ledger

- **`docs/governance/recurring-defect-families.yaml`** — machine-readable SSOT. 8 families with schema_version=1, last_updated=2026-05-21, 9 required per-family fields.
- **`docs/governance/recurring-defect-families.md`** — human view with §1 summary table, §2 per-family detail, §3 META-lessons section codifying rc8 → rc17 progressive discipline.

### Rule G-9 — Recurring-Defect Family Truth (NEW)

Three sub-clauses:
- **.a** yaml well-formedness (E156) — top-level schema_version/last_updated/families + 9 required per-family fields.
- **.b** mtime/last_updated freshness (E157) — yaml `last_updated:` ≥ commit date of most-recent refresh signal (ADR adds, baseline_metrics changes, new release notes, CLAUDE.md kernel heading changes).
- **.c** yaml/md family-id parity (E158) — set of `^  - id:` in yaml == set of `F-...` references in md.

Card `scope_surfaces:` declares 6 surfaces; 3 self-test fixtures satisfy Rule 110 META.

### Rule G-3 → G-3 + G-3.1 split

- **G-3** retains sub-clauses .a-.e (kernel-card structural coherence: size bounded, byte match, every active rule has card, kernel-deferred coherence, terminal-verb vs shipped-decision).
- **G-3.1** takes former sub-clause .f (disjunction-truth grammar invariant). New card `rule-G-3-1.md`. Gate Rule 100 number + enforcers E141/E142 retained.

### Rule R-I → R-I + R-I.1 split

- **R-I** retains sub-clause .a (five-plane manifest — every module declares deployment_plane; shipped W1).
- **R-I.1** takes former sub-clause .b (edge↔compute ingress routing — IngressGateway SPI; status `design_only`, promoted to `runtime_enforced` at W3+ per ADR-0049 / ADR-0089). New card `rule-R-I-1.md`. Gate Rule 105 number + enforcer E143 retained.

### Rule G-2 → G-2 + G-2.1 split

- **G-2** retains sub-clauses .a/.b/.c/.d/.g (authority-text per-surface truth: shipped-row evidence, baseline-metrics single source, agent-*/ARCHITECTURE.md paths, root ARCHITECTURE.md count+path, release-note numeric truth).
- **G-2.1** takes former sub-clauses .e/.f/.h plus integrates Rule 94/98/103/109 deleted-module coverage under one card. Three sub-clauses (.a status-yaml-allowed-claim, .b active-corpus, .c broad-corpus including ops/Dockerfile/.github/.puml). New card `rule-G-2-1.md`. Existing gate Rule 94/98/103/109 numbers + enforcers E120/E129/E130/E137/E138 retained (attribution shifts to G-2.1).

### Rule R-C → R-C + R-C.1 + R-C.2 split

- **R-C** retains sub-clause .a (code-as-contract — every constraint maps to ≥1 enforcer surface).
- **R-C.1** takes former .b (independent module evolution — module-metadata.yaml + isolation build). New card `rule-R-C-1.md`. Enforcer E31 retained.
- **R-C.2** takes former .c + .d + .e (run contract spine — tenantId required + RunStateMachine validates + service.runtime ↛ service.platform). New card `rule-R-C-2.md`. Enforcers E2/E4/E9/E11 retained.

### `/refresh-defect-archive` skill

Project-scoped Claude skill at `.claude/skills/refresh-defect-archive.md`. Re-runs the family-derivation pipeline + bumps `last_updated` + verifies Rule 111 passes. Opt-in companion to the gate; the gate is authoritative.

### `docs/governance/rules/README.md`

New taxonomy documentation:
- D-/R-/G-/M- prefix conventions (8 D + 15 R + 11 G + 2 M = 36, + Rule R-A.c = 37).
- Sub-rule `.1`/`.2` naming convention introduced in rc17.
- Two-namespace layering (semantic D-/R-/G-/M- + numeric Rule 1-111) per ADR-0086 gate_layer_boundary.

### Surface updates

- **CLAUDE.md** — Layer-0 table updated for P-C (R-C, R-C.1, R-C.2) and P-I (R-I, R-I.1); 4 kernel paragraphs trimmed; 6 new kernel paragraphs added (G-9 + 5 split children).
- **principle-coverage.yaml** — P-B + P-C + P-D + P-I + legacy P2 references migrated to new rule IDs.
- **architecture-status.yaml#baseline_metrics** — 6 fields updated + new `recurring_defect_families: 8` added.
- **enforcers.yaml** — E156/E157/E158 appended for Rule 111 sub-checks.

## What did NOT ship (intentional out-of-scope)

- **Auto-derivation pipeline for family yaml.** Manual maintenance is fine at 8 families. If the count grows past 15-20 a python script that scans release notes and synthesises the yaml may be warranted. Documented as ADR-0094 open question.
- **`cleanup_status: monitoring` enum value.** Today only 4 values (closed / structurally_addressed / partial / incomplete). If rc16-closed families regress, a 5th `monitoring` value may be useful. Documented as ADR-0094 open question.
- **`scope_surfaces:` retrofit to pre-rc17 cards.** Grandfathered per rc16 Rule 110 META. Opt-in retrofit as cards are touched.
- **R-C split sub-clause renumbering inside agent-service.** R-C.c → R-C.2.a is documented in the new card body but existing Java code carries no Rule-ID anchors; no renumbering needed.

## Lessons (for the recurring-defect-families.md §3 META-Lessons table)

1. **Recurring families deserve a first-class derived view.** rc4-16 wave-by-wave release notes preserve narrative but not pattern. The yaml SSOT lets reviewers ask "has this class been seen before?" in one read.
2. **Rule splits should track semantic territory, not just sub-clause growth.** R-C's 5 sub-clauses spanned 3 domains (governance / build / persistence); splitting clarifies which reviewer should care.
3. **Sub-rule numeric suffixes (`.1`, `.2`) are now the convention for extracted sub-clauses.** Documented in `docs/governance/rules/README.md`. Existing `.a`/`.b`/etc. designations remain for internal sub-clauses; `.1`/`.2` are for standalone child rules with their own cards.
4. **The family-ledger freshness gate (Rule G-9) is the structural backstop for the rc10 meta-lesson "Reviewer scope < defect scope".** rc16 Rule 110 caught the per-rule discipline; rc17 Rule G-9 catches the cross-wave discipline.

## Verification

- [x] `awk` count of `#### Rule X` headings in CLAUDE.md = 37 ✓
- [x] `awk` count of `# Rule N[a-z]? — slug` headers in gate/check_architecture_sync.sh before END = 123 ✓
- [x] Enforcers.yaml row count = 157 ✓
- [ ] `bash gate/check_architecture_sync.sh` — full gate run (pending — run after final commits)
- [ ] `bash gate/test_architecture_sync_gate.sh` — 205 cases pass (pending)
- [ ] `python3 gate/build_architecture_graph.py --check --no-write` — graph idempotent (pending; will reconcile node/edge counts post-run)
- [ ] Manual Rule 111.a negative — delete a family row → FAIL (verified by test_rule_111_yaml_wellformed_neg fixture)
- [ ] Manual Rule 111.c negative — yaml/md id mismatch → FAIL (verified by test_rule_111_md_yaml_parity_neg fixture)

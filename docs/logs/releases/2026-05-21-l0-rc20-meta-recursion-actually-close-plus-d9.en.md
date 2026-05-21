---
level: L0
view: process
affects_level: L0
affects_view: process
release_tag: v2.0.0-rc20
date: 2026-05-21
authority: ADR-0097
supersedes_release_notes:
  - docs/logs/releases/2026-05-21-l0-rc19-meta-recursion-permanent-close.en.md
---

> **Historical artifact frozen at SHA fb5bd9d** — numeric baselines below
> (ADR count, gate rule count, etc.) reflect rc20 final merged state.
> Current canonical baselines live in
> [`docs/governance/architecture-status.yaml#baseline_metrics`](../../governance/architecture-status.yaml);
> rc21 closeout (ADR-0098) supersedes this note per Rule 28 release-note
> convention.

# L0 v2.0.0-rc20 — Meta-Recursion Actually-Close + Rule D-9 + Rule G-7 Extension

## Verdict

rc20 closes the rc19 review's headline finding (Rule 111 was never
`[META]`-marked, so Rule 112 silently exempted the prototype META rule
the whole rc18+rc19 chain was meant to govern) and introduces two
user-directed kernel rules:

- **Rule D-9** — no version/log metadata (`rc<N> Wave <M>`, `per ADR-NNNN`,
  `Finding F<N>`, ...) in production code; such metadata lives in commit
  messages, ADRs, release notes, rule cards, rule-history.md
- **Rule G-7 extended** — all driving scripts on Windows hosts MUST be
  invoked through Linux/WSL; Git Bash for Windows is a one-off debug
  shim only

**5 waves, 5 PRs, all merged green:**
- Wave 1 #26 → c1cc52d (Rule 111 [META] + content-diff freshness sync + cleanup_status parity + Rule 113 helper extraction)
- Wave 2 #27 → 4ac3a37 (rc19 release-note numeric truth + Rule 97 widening + P-C.md frontmatter R-C.1 fix)
- Wave 3 #28 → f2f60fc (surface-token sanitizer + Rule 112 boundary widening + bash -n CI + Rule 114 fixture parameterization)
- Wave 4 #29 → (this wave's Rule D-9 + Rule G-7 extension + Rule 115 + grandfather list)
- Wave 5 #30 → (this PR; ADR-0097 + release note + baseline lockstep + freeze rc19)

## Adversarial / structural findings closed

| Finding (rc19 review) | rc20 fix | Wave |
|---|---|---|
| Rule 111 not `[META]`-tagged — Rule 112 silently exempts prototype META rule | `[META]` marker + literal source marker comment | 1 |
| Kernel/card prose still says "mtime/24h freshness" after rc18/rc19 swapped to content-diff | CLAUDE.md G-9 kernel + rule-G-9.md card rewritten to content-diff | 1 |
| cmd_parity is id-only — F-recursive yaml=closed-vs-md=structurally-addressed drift slips through | cmd_parity extension: cleanup_status text per family-id with leading-emoji anchor | 1 |
| `signal_sha^` first-parent ambiguity on merge commits | `signal_sha^1` explicit + documented squash-merge requirement | 1 |
| Rule 113 fixture re-implements production grep (Pattern D recurrence) | extract `gate/lib/check_legacy_paren.sh` helper; production + fixtures source it | 1 |
| rc19 release note overclaimed the Windows Git Bash self-tests ratio | corrected to live denominator; Rule 97 extended with denom-drift sub-check | 2 |
| ADR-0096 says "3 in-branch correctives total" while release note says 5 | standardised on 5 (true per-wave breakdown) | 2 |
| `P-C.md` frontmatter `enforced_by_rules: [R-C.a, R-C.b]` | fixed to `[R-C.a, R-C.1]` per ADR-0094 split | 2 |
| `SURFACE_PREFIX_BASES` contains invalid git pathspecs | removed dead placebo entries | 3 |
| `derive_signal_paths` accepts unresolvable surface tokens silently | surface-token sanitizer (warns on absolute / colon-bearing / unresolvable) | 3 |
| Rule 112 hard-coded `+80` body window | widened to next `# Rule` header / EOF boundary | 3 |
| Rule 112 helper regex locks to `.sh` only | widened to `gate/lib/(check\|validate)_*.(sh\|bash\|py)` | 3 |
| No `bash -n` syntax check at CI time | added pre-orchestrator CI step | 3 |
| Rule 114 negative fixture tests one near-miss | parameterised over 6 near-miss filename variants | 3 |

## New kernel rules (user-directed, per ADR-0097)

| Rule | Surface | Enforcer |
|---|---|---|
| **D-9 — No Version / Log Metadata in Code** | production code (`*.java`, `*.py`, `*.sh`, `*.bash`, `*.kt`, `*.ts`, `*.tsx`, `application*.yml`, `Dockerfile`, `.github/workflows/*.yml`) | Gate Rule 115 / E163; grep against `\brc[0-9]+ Wave [0-9]+\b\|\bper ADR-[0-9]{4}\b\|\(F[0-9]+\)\|\bFinding F[0-9]+\b\|\b(closes\|addresses) #[0-9]+\b`. Grandfather list: `gate/d9-grandfathered-files.txt` (54 pre-existing files, sunset 2026-11-21). |
| **G-7 (extended) — Linux-First Dev Environment + Invocation** | all driving shell scripts on Windows hosts | `docs/governance/dev-environment.md` (convention; agent + author discipline; not gateable beyond the existing verification gate) |

## Baseline metrics

Per `feedback_release_note_baseline_truth.md` (canonical = first numeric column), this table uses the rc15+ 2-column (Count + Delta) format. The Count column is the current rc20 canonical baseline (matches `architecture-status.yaml#baseline_metrics`); the Delta column documents the rc19 → rc20 change.

| Metric | Count | Delta |
|---|---|---|
| §4 constraints | 65 | unchanged from rc19 |
| Active ADRs | 96 | +1 from rc19 (ADR-0097) |
| Active gate rules | 127 | +1 from rc19 (Rule 115; rc20 Waves 1/2/3 hardened existing rules without adding top-level rules) |
| Active engineering rules | 38 | +1 from rc19 (Rule D-9) |
| Gate self-test cases | 226 | +6 from rc19 (3 Wave 1 + 1 Wave 2 + 2 Wave 4) |
| Enforcer rows | 162 | +2 from rc19 (E162 + E163) |
| Layer-0 governing principles | 13 | unchanged |
| Reactor modules | 8 | unchanged |
| Architecture graph nodes | 407 | +5 from rc19 (Wave 1 + Wave 4 + ADR-0097 node) |
| Architecture graph edges | 667 | +11 from rc19 (Wave 1 + Wave 4 + ADR-0097 supersedes/extends/relates_to edges) |
| Recurring defect families | 9 | unchanged (F-recursive-prevention-irony reopened to `cleanup_status: monitoring`) |
| Maven tests green | 374 | unchanged (no Java code changes) |

## Pillar coverage (per Rule R-B / ADR-0065)

- **performance** — no change.
- **cost** — no change.
- **developer_onboarding** — improved. Rule G-7 extension formalises
  WSL/Linux as the documented default on Windows hosts; eliminates the
  recurring "Git Bash performance + portability" trap.
- **governance** — significantly strengthened. F-recursive-prevention-irony
  is now structurally gated against the ORIGINAL META rule (Rule 111
  `[META]`); cmd_parity catches status-text drift; first-parent semantics
  closes latent freshness ambiguity. Rule D-9 makes production code
  free of version/log metadata.

## What did NOT ship (intentional out-of-scope)

- Full scrub of the 54 grandfathered files (sunset_date 2026-11-21).
  Each file requires per-file review; opportunistic per-touch.
- `cleanup_status: monitoring` cool-down (3 rc cycles without
  recurrence before re-promotion to `closed`) is convention-only;
  machine enforcement is an open question for rc21+.
- The 21 legacy fixture helper-isations (KD-rc18-1 / KD-rc19-1) still
  carry forward; the Rule 113 helper extraction (rc20 Wave 1) closes
  one specific fixture pair but does not retrofit the 21.

## In-branch corrective commits (9 total across 4 waves)

| Wave | Correctives | Cause |
|---|---|---|
| 1 | 3 | (a) cmd_parity emoji-anchor + baseline bump + rc19 release-note snapshot marker; (b) family yaml content bump for content-diff freshness; (c) cmd_parity skip status check when yaml or md side missing field (existing fixture compat) |
| 2 | 2 | (a) bump gate_executable_test_cases for new fixture; (b) rc19 release-note snapshot marker + family yaml content bump |
| 3 | 1 | (a) family yaml content bump for content-diff freshness |
| 4 | 3 | (a) bump baseline metrics for Rule 115 + regen gate rules + family yaml + rc19 snapshot marker; (b) bump active_engineering_rules to 38 for D-9; (c) family yaml content bump |

Total: 9 in-branch correctives; 0 post-merge correctives on `main`.
Rebase contention on `recurring-defect-families.yaml#last_updated`
between parallel waves was the dominant rebase cost.

## Verification

- [x] Wave 1 / 2 / 3 / 4 PRs all CI green and merged to `main`
- [x] Rule 111 carries `[META]` AND sources `gate/lib/check_recurring_families.sh` literally (test_rule_112_meta_self_application_live_rule_111_pos PASS)
- [x] cmd_parity catches synthetic yaml=closed-vs-md=structurally-addressed drift (test_rule_111_c_cleanup_status_parity_neg PASS)
- [x] Rule 113 fixtures source the helper (Pattern D closed)
- [x] Rule 115 catches synthetic `rc<N> Wave <M>` annotation in test fixture (PASS)
- [x] Rule 115 accepts clean production code (PASS)
- [x] `bash -n gate/*.sh` PASS (new pre-orchestrator CI step)
- [x] F-recursive-prevention-irony status: `cleanup_status: monitoring` (yaml + md aligned)
- [x] gate/d9-grandfathered-files.txt sunset_date 2026-11-21
- [ ] CI green on PR #30 (this Wave 5)

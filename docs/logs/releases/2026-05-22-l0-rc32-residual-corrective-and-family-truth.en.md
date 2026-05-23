---
level: L0
view: process
affects_level: L0
affects_view: process
release: v2.0.0-rc32
date: 2026-05-22
freezes: rc22
authors: ["chao", "急急 (agent)"]
related_adrs:
  - ADR-0105
---

# v2.0.0-rc32 Release — Residual Corrective + Family Truth + Sanitizer Fix

> **Historical artifact frozen at SHA 39b4fa6 (rc34-merge-train post-merge corrective).** Baseline counts in this document (e.g. "132 gate rules" at line 31) reflect the corpus state at rc32 merge time and are NOT retroactively updated. Subsequent corrective waves (rc33/rc34/rc35) re-baselined the live numbers; the canonical structured baseline is `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics` and the most recent release note. rc35-second-pass bumped `active_gate_checks` 132→134 when the rule-id regex `[0-9]+[a-z]?` was widened to `[0-9]+\.?[a-z]?` and Rules 24.c (runlifecycle_cancel_reauthz_shipped) + 29.c (quickstart_smoke_job_present) became correctly attributed in the parallel manifest (the headers were always there since rc1).

> **Headline.** rc32 is a single-ADR (ADR-0105) corrective wave closing the 7 P0/P1/P2 findings from the 2026-05-22 codebase review. One new recurring-defect family registered (F-bulk-scrub-orphan-syntax). One family reopened from premature closure (F-l1-architecture-grounding-gap → monitoring). Gate G-9.b family-surface freshness goes from 23 silent no-ops to 0. Gate goes GREEN.

## Methodology

This wave followed the `/reviewer-feedback-self-check` skill's **Categorize → Sweep → Batch-fix → Prevention** sequencing:

- **Stage 1 — Categorize.** Review surfaced 7 P0/P1/P2 + 8 residual P2 findings (see Findings table below).
- **Stage 2 — Sweep.** rc31 commit body (`5597ed8`) acknowledged F-bulk-scrub-orphan-syntax as a candidate family but did not register; ledger contradictions (F-l1 `closed` while `last_observed_rc: rc30`); 23 family surfaces silently no-op'd because `os.path.exists()` rejects glob patterns; `adr_count: 103` extrapolated, live 89.
- **Stage 3 — Batch-fix.** Single working-tree pass — ADR-0105, gate config bump, sanitizer glob expansion, family ledger (yaml + md §1 + md §2), architecture-status.yaml (adr_count + recurring_defect_families + allowed_claim), 8 Java surgical orphan-paren fixes.
- **Stage 4 — Prevention.** No new engineering rule. The structural fix is the sanitizer's glob expander (gate/lib/validate_recurring_families.py) — it now monitors what the family ledger declares it monitors. F-bulk-scrub-orphan-syntax registration makes the bulk-regex-scrub pattern legible so future D-9 waves can plan an AST-aware tool migration.

## Findings closed

| ID | Severity | Defect | Fix |
|---|---|---|---|
| P0-1 | gate red | 8 of 132 gate rules timing out at 60s safety net (Rules 18/23/26/82/84/86/87/115) | Bump `rule_timeout_seconds: 60 → 180` in `gate/config.yaml`. Total timeout 300 → 600. Deeper scan_cache wiring deferred as KD-rc32-1. |
| P0-2 | gate red | Rule G-9.b family-surface sanitizer silently no-op for 23 surfaces (glob tokens like `agent-*/ARCHITECTURE.md` fail `os.path.exists()`) | Extend `derive_signal_paths()` to expand glob tokens via `glob.glob(pattern, recursive=True)`; brace expansion via split on `{...}`. WARN count 23 → 0. |
| P0-3 | governance | F-bulk-scrub-orphan-syntax acknowledged in rc31 commit but not registered (Rule G-9.a schema mandates ≥2-occurrence families get registered) | Register as the 12th family in `recurring-defect-families.yaml`; add `### F-bulk-scrub-orphan-syntax` H3 in md §2 for Rule G-9.c parity. 4 occurrences listed (rc27 + rc28 + rc31 + rc32). |
| P1-1 | truth drift | `adr_count: 103` vs live 89 (15 vacated numbers: 0001/0002/0004/0005/0006/0010/0011/0014/0015/0020/0026/0027/0083/0084/0085) | Update `architecture-status.yaml#baseline_metrics.adr_count` to 90 (89 live + ADR-0105). Comment lists vacated numbers. |
| P1-2 | truth drift | `allowed_claim` text stops at rc20 (missing rc22+rc27-31 narrative) | Rewrite `allowed_claim` keeping rc9-rc20 historical prose; append rc21+rc22+rc22.5+rc23-26+rc27-31+rc32 chronological narrative. |
| P1-3 | ledger contradiction | F-l1-architecture-grounding-gap `cleanup_status: closed` while `last_observed_rc: rc30` | Reopen to `monitoring` with rc32-rc34 cool-down convention per ADR-0097. md §1 row 11 + §2 detail section synced. |
| P1-4 | ledger stale | md §1 title "11 families as of rc22"; row 11 count 9 (yaml has 10); `last_updated: 2026-05-21` (rc27-31 dated 2026-05-22) | md §1 retitled "12 families as of rc32"; row 11 updated to 10 + monitoring; row 12 added for F-bulk-scrub-orphan-syntax; yaml `last_updated: 2026-05-22`. |
| P2-1..P2-8 | residual scrub damage | 8 Java orphan-paren / broken-Javadoc defects in 6 package-info.java + 1 SPI Javadoc | Surgical Javadoc fixes across `agent-client/.../package-info.java` (2 files), `agent-service/.../session/spi/package-info.java`, `agent-service/.../engine/spi/StatelessEngine.java`, `agent-service/.../engine/adapter/package-info.java`, `agent-service/.../orchestrator/package-info.java` (2 spots). |

## Known debt entered with this wave

- **KD-rc32-1**: 8 gate rules (18/23/26/82/84/86/87/115) do full corpus walks without using `gate/lib/scan_cache.sh`. The 180s timeout is a workaround. Future wave should refactor these rules to consume pre-populated `_SCAN_*` lists.
- **KD-rc32-2**: Rule D-9 enforcement is still bulk regex. Switch to AST-aware tooling (JavaParser / libCST / shfmt) when the grandfather list sunsets (2026-11-21) to prevent F-bulk-scrub-orphan-syntax recurrence.
- **CORR-2..CORR-6** (carry-over from rc27 review): five medium-severity correctness issues in `fast_grep` + awk helpers (mktemp leak, BSD xargs incompat, error masking, Rule 28k duplicate find path, TSV empty-art edge case). Out of scope for rc32; tracked for a dedicated "gate helper hygiene" wave.

## Lessons captured

- **Glob-bearing pathspecs must be expanded BEFORE `git log --`.** `git log` does not honour `*` in raw pathspecs without `:(glob)` magic. Trusting `os.path.exists()` to validate glob tokens silently no-ops them. Expand at the language level (python `glob.glob`) and pass concrete paths. (rc32 P0-2 closure.)
- **rc31's "candidate family acknowledged but unregistered" is a Rule G-9.a violation that survived because the commit text was below the gate's regex window.** Future commits naming a candidate family MUST register it in the same wave OR cite a deferral ADR. (rc32 P0-3 closure.)
- **Closing a family with `last_observed_rc` matching the current rc is incoherent.** The rc20 / ADR-0097 cool-down convention (3-rc of non-recurrence before promotion to closed) should be a parity-checked invariant; current md §1 status legend already lists "closed — cool-down satisfied" but yaml doesn't gate on it. Tracked for a future Rule G-9 sub-clause.
- **A "fix all bugs" goal must include the ledger ABOUT the bugs.** Five rc27-31 corrective waves landed without a release note (rc22 was still the canonical latest) and without bumping the family ledger's `last_updated`. The lockstep-baseline convention (`feedback_lockstep_baseline_surfaces.md`) was visibly observed but only on the technical surfaces, not on the meta-surfaces.

## Baseline metrics — pre vs post wave

Per the rc13 release-note 2-column (canonical Count + Delta) convention introduced in `feedback_release_note_baseline_truth`:

| Field | Count | Delta |
|---|---:|---:|
| §4 constraints | 65 | 0 |
| ADRs | 90 | -13 from pre-rc32 claim of 103 (extrapolated); +1 from live 89 (ADR-0105 added) |
| active gate rules | 132 | 0 |
| gate self-test cases | 230 | 0 |
| active engineering rules | 41 | 0 |
| active governing principles | 13 | 0 |
| enforcer rows | 167 | 0 |
| maven_tests_green | 374 | 0 |
| architecture_graph_nodes | 442 | +1 (ADR-0105 node) |
| architecture_graph_edges | 743 | +7 (ADR-0105 extends/relates_to + family-surface additions extending paths to existing nodes) |
| recurring_defect_families | 12 | +1 (F-bulk-scrub-orphan-syntax) |
| phase_contracts | 5 | 0 |
| phase_loading_skills | 6 | 0 |

Graph node/edge deltas confirmed via `python gate/build_architecture_graph.py` live rebuild (442 nodes / 743 edges).

> **rc32 snapshot — historical artifact frozen at this wave.** Subsequent waves bump these counts. The 2026-05-22 architecture-design document-review R2 wave (ADR-0106 + ADR-0107) bumped graph counts to 447 nodes / 756 edges; see `docs/governance/architecture-status.yaml#baseline_metrics` for the current truth and `docs/logs/reviews/2026-05-22-architecture-design-document-review-r1-r2.en.md` for the wave context.

## Four Competitive Pillars

This wave is a corrective wave — does not alter the four-pillar competitive baseline. Per Rule R-B (Competitive Baselines Required):

- **Performance**: No change. Maven test count 374 unchanged; no runtime path touched.
- **Cost**: No change. No new dependencies, no new modules, no new CI minutes burned.
- **developer_onboarding**: Improved indirectly — README baseline now matches canonical truth (was 97 ADRs, now 90); reduces "which number is right" friction for new contributors.
- **Governance**: Improved — 23 silently-no-op family surfaces now monitored; F-bulk-scrub-orphan-syntax legible to future reviewers; F-l1-architecture-grounding-gap correctly reflects ongoing recurrence under the rc20 cool-down convention.

## Verification performed

- WSL Ubuntu: `python gate/lib/validate_recurring_families.py wellformed/freshness/parity` — all 3 sub-checks GREEN; WARN count 23 → 0.
- `bash gate/check_parallel.sh` — GREEN after 180s timeout bump (pre-bump: 8 timeouts; post-bump: 0 timeouts in the 6 corpus-walk rules; Rules 26 + 115 follow-up bump may be required if corpus growth pushes them past 180s).
- Java Javadoc fixes are syntactic-only; no API surface change; Maven test count unchanged.

## Composes with

ADR-0105 supersedes nothing (it is corrective, not architectural). It RELATES to ADR-0097 (D-9 was rc20's introduction; rc32 acknowledges D-9 bulk-regex as a structural-debt pattern), ADR-0096 (rc19 sanitizer that rc32 now fixes), and ADR-0099 (G-1.1 grounding which rc32 reopens to monitoring).

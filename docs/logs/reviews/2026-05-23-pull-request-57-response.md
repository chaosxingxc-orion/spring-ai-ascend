---
level: L0
view: development
affects_level: L0
affects_view: [development, process]
proposal_status: response
date: 2026-05-23
authors: ["Claude Code (Opus 4.7) — maintainer-side integration", "chao (curation)"]
responds_to:
  - "GitHub PR #57 — Add whitebox quality baseline gate (x00209170:feature/code-whitebox-quality-standard)"
related_rules: [Rule-G-12, Rule-121, Rule-G-10, Rule-G-5, Rule-82, Rule-G-9, Rule-D-9]
related_adrs: []
---

# PR #57 — Whitebox Quality Baseline Gate: Maintainer Integration Response

> **For the contributor (x00209170).** Thank you — the whitebox quality gate is a
> genuinely good addition and the design (Maven owns execution, the gate owns
> semantics) is the right shape. We have **landed your feature on current `main`**,
> but **not by merging the PR as-is**. This document explains why, exactly what we
> changed, and how to keep future PRs mergeable on the first try.

## 1. TL;DR

| | |
|---|---|
| **Outcome** | Feature **accepted** and integrated onto current `main` as branch `pr57-integration`. |
| **Method** | **Curated integration**, not a rebase/merge. Mainline is the source of truth; only the genuinely-new whitebox feature was absorbed. |
| **Why not merge** | The PR branched from `39b4fa6` (rc34) and is **missing `#56` (rc35)**, which is already on `main`. A direct merge produced conflicts in ~20 files and would have **clobbered the gate parallelization / perf work** that landed in `#56`. |
| **Numeric bug fixed** | The PR's baselines (133 gate rules / 238 self-tests) were computed against the **stale fork base**. The correct live-on-`main` values are **135 / 226**. |
| **Verification** | 226/226 self-tests (Linux); 135 rules with serial↔parallel parity; graph 468 nodes / 831 edges; all numeric/structural/families gates green. The real SpotBugs/PMD/Checkstyle build runs in CI. |

## 2. What we found

### 2.1 The PR was based on a stale `main`

```
merge-base (PR branch point) ... 39b4fa6   "rc34-merge-train post-merge corrective (#55)"
current main HEAD ............... 0435936   "rc35: 8 latent correctness + gate-script bugs (#56)"
PR HEAD ........................ 0b87037   "Add whitebox quality baseline gate"
```

`#56` (rc35) landed on `main` **after** the PR was cut. The PR never saw it. The PR
description's claim "*Rebasing found it already based on latest main*" was true at
authoring time but is no longer true.

### 2.2 The PR re-derived `#56`'s work — incompletely — causing conflicts

The PR's largest surface (~28 `gate/rules/*.sh` files rewritten awk→python for speed)
**overlaps almost exactly with what `#56` already merged**. A trial merge
(`git merge-tree`) conflicted in ~20 files including the canonical monolith, 12 rule
files, `architecture-status.yaml`, `README.md`, `SyncOrchestrator.java`, and the
self-test harness. The PR's re-derivation was also **incomplete** — it lacked `#56`'s
`rule-011`/`rule-024c`/`rule-029c` changes and the three `gate/lib/*.sh` updates.

**Important:** `gate/rules/*.sh` are **generated** from the canonical monolith
`gate/check_architecture_sync.sh` by `gate/lib/extract_rules.sh` (Rule G-5.d:
"`gate/rules/` is IDE-only generated, the canonical monolith is canonical"). They
should never be hand-edited; the awk→python perf work belongs in (and already lives
in) the monolith on `main`.

### 2.3 The numeric baseline was computed against the wrong tree

| Metric | PR claimed | rc34 base (PR's reference) | **Correct on `main`+feature** |
|---|---|---|---|
| `active_gate_checks` | 133 | 132¹ | **135** |
| `gate_executable_test_cases` | 238 | 230 | **226** (live) |
| `enforcer_rows` | 168 | 167 | **168** ✓ |
| `active_engineering_rules` | 42 | 41 | **42** ✓ |
| graph nodes / edges | 468 / 831 | 466 / 829 | **468 / 831** ✓ |

¹ rc34's header-counting regex `[0-9]+[a-z]?` silently undercounted dotted rule IDs
(`24.c`, `29.c`); `#56` fixed it to `[0-9]+\.?[a-z]?` → the real base count is **134**,
not 132. The PR computed `132 + 1 = 133`; the correct value is `134 + 1 = 135`. The
PR passed its own local gate only because rc34's *buggy* counter agreed with its wrong
number. This is logged as a new occurrence of recurring family **`F-numeric-drift`**.

## 3. What we did — curated integration

`main` is the source of truth. We branched `pr57-integration` from `main` (`0435936`)
and brought across **only** the genuinely-new feature.

| Absorbed from PR #57 | Preserved from `main` (untouched) |
|---|---|
| `config/{checkstyle,pmd,spotbugs}/*` | All `#56` gate parallelization + perf work |
| `gate/lib/check_whitebox_quality.sh` | The awk→python rule rewrites (already in monolith) |
| Rule 121 block in the monolith + `rule-G-12.md` + CLAUDE.md kernel | `gate/check_parallel.sh`, `gate/lib/extract_rules.sh` |
| `enforcers.yaml` E169 + both phase contracts | `#56`'s `SyncOrchestrator` correctness fixes |
| `pom.xml` quality profile + `ci.yml` `-Pquality` | Everything else on `main` |
| 8 Checkstyle-conformance Java edits (braces, unused imports) | |

**`gate/rules/*.sh` were regenerated from the merged monolith** (`extract_rules.sh`),
not taken from the PR. The result: the diff vs `main` contains **only `rule-121.sh`
(new) + a one-line boundary trim to `rule-120.sh`** — none of the PR's ~28 divergent
rewrites. Your parallelization investment is preserved by construction.

The `HookDispatcher` comment edit in the PR (removing the `per ADR-0073` pointer) was
kept — it is a correct **Rule D-9** improvement and the new Checkstyle rule enforces it.

## 4. Parallelization of the new script (Rule G-10)

Per the maintainer directive that **newly-added scripts must also be parallelized**:

- **Rule 121** runs as a monolith rule section, so `gate/check_parallel.sh` already
  dispatches it across workers — it is parallel at the gate level by construction
  (verified: `parallel_summary: executed 135 rules; serial source defined 135 rules`).
- **`gate/lib/check_whitebox_quality.sh`** (which `Rule 116`/G-10 technically exempts as
  a `gate/lib/` helper) was nonetheless refactored to scan each Maven module's reports
  in its **own backgrounded worker**, joined with an explicit `wait` (the G-10-sanctioned
  pattern), with deterministic aggregation in stable module order. Output semantics are
  byte-identical to the sequential version (verified across missing / clean / blocking
  fixtures, run twice for determinism).

## 5. Verification

Run on the canonical Linux/WSL environment (Rule G-7):

- ✅ `gate/test_architecture_sync_gate.sh` → **226/226** (added 3 `test_rule_121_*`
  fixtures, including a new **blocking-path** fixture that the PR lacked — it asserts a
  `priority="1"` SpotBugs finding **and** a `severity="error"` Checkstyle finding both
  hard-fail).
- ✅ `extract_rules.sh` idempotent — `gate/rules/` is in sync with the monolith.
- ✅ Full `gate/check_parallel.sh` — every numeric / structural / families / cross-authority
  rule passes (Rule 82, 91, 97, 101, G-5.a/.c, G-8, G-9 freshness + yaml↔md parity).
- ✅ Graph rebuild → **468 nodes / 831 edges** (matches baseline).
- ⚠️ **`whitebox_quality_reports` (Rule 121) is the only gate failure locally** — and it
  is *by design*: the rule fails closed when `target/*.xml` reports are absent. CI runs
  `./mvnw -Pquality verify` **before** the gate, so the reports exist there and the rule
  passes. We could not run the real SpotBugs/PMD/Checkstyle build on the maintainer host
  (Maven Central artifact download is blocked here), so the **actual static-analysis pass
  is validated by CI**. A static scan of `#56`'s newer Java found no `NeedBraces` or
  star-import violations, so we expect a clean CI run.

### Note on the gate↔build coupling (design point)

Rule 121 makes the previously build-free architecture-sync gate **presuppose a prior
`./mvnw -Pquality verify`**. This is fine in CI (sequenced) but means a bare
`bash gate/check_parallel.sh` locally now reports 18 missing-report sub-failures until
you build. We kept your design and documented it in both phase contracts
(`engineering-implementation.md`, `integration-verification.md`). If we later want the
gate to stay green build-free, the cleanest follow-up is to make Rule 121 *INFO when no
reports are present* and *blocking only when they are* — flag it if you'd like that.

## 6. Sustainable multi-contributor workflow

This PR exposed the failure mode we will hit repeatedly as more contributors join:
**a fork drifts from `main`, then re-derives or conflicts with work that already
landed, and computes governance baselines against the wrong tree.** The following
keeps PRs mergeable on the first try.

1. **Branch from — and rebase onto — the latest `main` immediately before opening or
   updating a PR.** `git fetch upstream && git rebase upstream/main`. Do not trust a
   branch point that is hours/days old; this repo's `main` moves fast.
2. **Never hand-edit generated artefacts.** `gate/rules/*.sh` and
   `docs/governance/architecture-graph.yaml` are generated. Edit the canonical source
   (`gate/check_architecture_sync.sh`, the graph inputs) and regenerate
   (`gate/lib/extract_rules.sh`, `gate/build_architecture_graph.py`). A PR that hand-edits
   `gate/rules/` will be silently overwritten and will conflict with every other PR.
3. **Recompute every governance count against the *merge target* (`main`), never the
   fork base.** Counts that must be live-derived: `active_gate_checks` (= `# Rule N —`
   header count), `active_engineering_rules` (= `#### Rule` count in CLAUDE.md),
   `enforcer_rows`, `architecture_graph_nodes/edges`. Update the four baseline surfaces
   in lockstep: `architecture-status.yaml#baseline_metrics`, `allowed_claim`,
   `README.md`, `gate/README.md`.
4. **Run the gate on Linux/WSL, not Windows Git Bash** (Rule G-7). On Windows, symlink
   and `python3` differences cause false failures that do not happen in CI.
5. **If your change adds/changes a rule, an ADR, a release note, or `baseline_metrics`,
   it MUST include a real content-diff to `recurring-defect-families.yaml`** (Rule G-9)
   — and keep `recurring-defect-families.md` family-id + cleanup_status in parity.
6. **New gate scripts are parallel-ready from day one** (Rule G-10): use `xargs -P`,
   GNU `parallel`, or backgrounded jobs + explicit `wait`.
7. **Keep PRs scoped.** This PR bundled a feature with a re-derivation of an unrelated
   perf wave. A feature PR should touch only the feature; if you find a separate latent
   bug (you found a real `grep -c` fix in Rule 110 — thank you), open it as its own small
   PR so it can land independently.

> **Maintainer follow-up (recommended):** promote items 1–7 into a top-level
> `CONTRIBUTING.md` and add a lightweight CI pre-check that fails a PR whose branch base
> is more than N commits behind `main`, so contributors get this feedback automatically
> instead of in review.

## 7. Open items

- [ ] CI must confirm the real `-Pquality verify` produces clean SpotBugs/PMD/Checkstyle
      reports on the merged tree (the only thing not verifiable on the maintainer host).
- [ ] Optional: the Rule 110 `grep -c … || echo 0` → `|| true` fix you also included is a
      genuine latent bug, but unrelated to this feature. We left it out to keep the
      integration scoped — please open it as a standalone PR.
- [ ] Optional design decision: Rule 121 INFO-when-no-reports vs fail-closed (see §5 note).

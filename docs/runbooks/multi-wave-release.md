# Multi-Wave Release Runbook (rc17 / rc18 / rc19 pattern)

This runbook captures the **multi-wave-under-fixed-version** pattern
used in rc17 → rc18 → rc19. Use it when:

- A reviewer pass finds ≥3 defects spanning independent surfaces
- The user wants to fix everything under one version number (no
  version-creep)
- Parallel PR review surface is desirable (each wave is independently
  reviewable + mergeable)

Authority: ADR-0096 (rc19 Wave 4 codification). Lineage: ADR-0094
(rc17), ADR-0095 (rc18) established the pattern; this runbook codifies
it for reproducibility.

---

## Pattern overview

```
Wave 1 (P0)  ─┐
Wave 2 (P1)  ─┤
Wave 3 (P2)  ─┼─→ Wave 5 (META finalize)
Wave 4 (P3)  ─┘
```

- **Waves 1-4 ship parallel PRs** off `main`, each touching independent
  files. CI runs on each.
- **Wave 5 finalizes** after Waves 1-4 merge: single ADR covering all,
  single release note with per-wave chapters, baseline_metrics
  lockstep update, freeze prior release note with merge SHA.
- **Single ADR-NNNN authority** for the entire multi-wave wave (vs
  ADR-per-wave which inflates ADR count).

## When NOT to use

- Single-defect fix: just one PR
- All defects in the same file: one PR is cleaner
- Defects are urgent enough that a corrective-loop is acceptable

## Wave numbering convention

- **Wave 1** = highest priority / most urgent / closes the headline
  finding. Often a structural fix (helper extraction, new gate rule).
- **Wave 2** = secondary structural fix or cross-rule pattern sweep.
  Often depends on Wave 1 helpers being available on main.
- **Wave 3** = naming / dead-content / cross-reference cleanup. Pure
  doc; runs in parallel with Wave 2/4.
- **Wave 4** = enforcer normalization, migration docs, or any
  bulk-rename work. Often runs in parallel with Wave 3.
- **Wave 5** = META finalize. Always last. Single commit with:
  - NEW `docs/adr/00NN-rcNN-<title>.yaml`
  - NEW `docs/logs/releases/<date>-l0-rcNN-<title>.en.md`
  - baseline_metrics updates (active_engineering_rules,
    active_gate_checks, gate_executable_test_cases, enforcer_rows,
    adr_count, architecture_graph_nodes, architecture_graph_edges,
    recurring_defect_families)
  - `allowed_claim` prose update mirroring baseline_metrics
  - README baseline line update
  - **Freeze prior release note** with `Historical artifact frozen at
    SHA <merge-sha>` marker right under H1
  - `docs/governance/rule-history.md` rcNN section
  - MEMORY index update (project_v2_0_0_rcNN_response_*.md)

## Branch naming

```
rcNN/wave-K-<short-scope-name>
```

Example: `rc18/wave-3-naming-cleanup`, `rc19/wave-1-meta-recursion-close`.

## PR title convention

```
fix(rcNN-waveK): <scope-summary>  # for waves 1-4
feat(rcNN-waveK): <scope-summary> # for wave 5 (introduces new ADR)
```

Example: `fix(rc18-wave3): naming + dead-content cleanup (ADR-0095 Wave 3)`,
`feat(rc18-wave5): META finalize — ADR-0095 + release note + baseline lockstep`.

## Worktree workflow (for parallel waves)

```bash
# From main worktree (on main branch, up to date)
git worktree add ../spring-ai-ascend-rcNNwK -b rcNN/wave-K-<scope> origin/main

# Work in the new worktree
cd ../spring-ai-ascend-rcNNwK
# ... edit files, commit ...
git push -u origin rcNN/wave-K-<scope>
gh.exe pr create --head rcNN/wave-K-<scope> --base main --title "..." --body "..."

# After PR merges
cd ../spring-ai-ascend  # back to main worktree
git worktree remove ../spring-ai-ascend-rcNNwK --force
git branch -D rcNN/wave-K-<scope>
git pull --ff-only
```

**Gotchas:**
- `git worktree remove` needs `--force` if the branch is still
  checked out elsewhere.
- After Wave-1 merges, Waves 2-4 worktrees need
  `git fetch origin main && git merge origin/main --no-edit`
  to rebase forward (cleaner than git rebase for shared branches).
- gh.exe pr create requires `--head` when invoked from a different
  worktree than the branch's checkout.

## Lockstep checklist for Wave 5 (the finalize wave)

Per `feedback_lockstep_baseline_surfaces.md` (rc17 lesson),
**4 surfaces must update in one commit** or CI corrective-spiral happens:

- [ ] `docs/governance/architecture-status.yaml#baseline_metrics` (Rule G-5.c)
- [ ] `docs/governance/architecture-status.yaml#architecture_sync_gate.allowed_claim` (Rule 28)
- [ ] `README.md` baseline line (Rule 27)
- [ ] Prior release note `docs/logs/releases/*.md` frozen with merge SHA marker (Rule 28)

Plus regenerate architecture-graph.yaml IF graph counts changed (Rule G-8.a parity).

## Common in-branch correctives

Per rc17/rc18/rc19 retrospective, these are EXPECTED inside a wave
branch (not "bad" — just part of the loop):

| Symptom | Wave usually affected | Cause | Fix |
|---|---|---|---|
| Rule 91 `active_gate_checks` mismatch | Wave 1 (new rule added) | baseline not bumped | bump baseline_metrics in same wave |
| Rule 97 stale graph counts in release note | Wave 5 | new ADR/family added after release note drafted | add `rc[N] snapshot` marker or recount |
| Rule 87 `agent-runtime-core` without marker | Wave 5 (allowed_claim rewrite) | new prose mentions deleted module name | add `pre-Phase-C` or `dissolved` marker within ±3 lines |
| CI shallow-clone (Rule 111.b / Rule 44) | Wave 1 (new strict freshness/diff check) | `actions/checkout@v4` defaults to fetch-depth: 1 | add `fetch-depth: 0` to ci.yml |
| Rule 112 META not sourcing helper | Wave 1 (new META rule + dogfooding) | existing META rule didn't source helper before | add `source gate/lib/check_*.sh` to that rule's body |

If you see these on a wave PR, the in-branch corrective is normal.
**Squash-merge consolidates them on `main`** so the public history
shows one commit per wave — the corrective loop is invisible to
downstream readers.

## Cross-references

- `feedback_lockstep_baseline_surfaces.md` (MEMORY) — 4-surface lockstep
- `feedback_release_note_baseline_truth.md` (MEMORY) — frozen-SHA convention
- `feedback_namespace_migration_recipe.md` (MEMORY) — bulk rename pattern
- ADR-0094 (rc17 first multi-wave use)
- ADR-0095 (rc18 5-wave example with extensive known_debt)
- ADR-0096 (rc19 5-wave example + this runbook)
- `docs/governance/rules/README.md` — rule prefix taxonomy
- `gate/rule-number-migration.md` — legacy → semantic mapping

---
level: L0
view: process
release_id: v2.0.0-rc11
tag: v2.0.0-rc11
date: 2026-05-19
status: published
supersedes_tag: v2.0.0-rc10 (retracted)
authority_refs:
  - ADR-0085
  - "docs/reviews/2026-05-19-l0-rc10-post-corrective-architecture-review.en.md"
  - "docs/reviews/2026-05-19-l0-rc10-post-corrective-architecture-review-response.en.md"
---

# v2.0.0-rc11 — Kernel-Truth + Shadow-Corpus-Precision + rc10 Retraction

> **Historical artifact frozen at SHA 131a13d (rc11 merge to main).** Baseline counts in this release note reflect the corpus state at rc11 wave time; the rc12 corrective wave (`docs/logs/releases/2026-05-19-l0-rc12-corrective.en.md`) updates the canonical baseline. Per Rule 28 release_note_baseline_truth, historical release notes are exempt from canonical baseline matching when this marker is present.

## TL;DR

The rc10 post-corrective architecture review (Codex, 2026-05-19) found 4 contract/prevention-rule precision defects in the rc10 wave: Rule 41 kernel overclaimed shipped runtime behaviour (P1-1), deleted-module-name leaks remained in operational Markdown runbooks (P1-2), Rule 96's kernel and implementation disagreed on the "either surface" policy (P1-3), and `gate/rules/` shadow-corpus prose was imprecise (P2-1). The reviewer also noted a secondary kernel-vs-impl drift in Rule 94 itself.

rc11 closes all four cited findings using the categorize→sweep→batch-fix→prevention methodology rc10 introduced. Per user election, this wave ALSO (a) fully retracts the v2.0.0-rc10 (retracted) tag, and (b) widens Rule 94's implementation to match its kernel.

The wave is **docs/yaml/gate-script only** — no production Java/Spring code changed. Maven test count unchanged at 371 GREEN.

## Findings closed

| Finding | Family | Closure summary |
|---|---|---|
| **P1-1** Rule 41 kernel overclaims end-to-end runtime suspension | J-α | Narrowed Rule 41 kernel to decision-envelope wording; added Rule 41.c sub-clause to `docs/CLAUDE-deferred.md`; NEW Rule 99 + enforcers E139/E140 to prevent recurrence on any deferred-aware rule. |
| **P1-2** Deleted-module-name leaks in operational runbooks + YAML comments | J-β | Fixed 4 cited rows (`ops/runbooks/*.md`, `ops/compose/sidecar-mem0.yml`); sweep surfaced ~30 hidden defects in active developer-facing docs (quickstart, posture-model, telemetry policy, application.yml metric tag, posture-coverage, BoM, dev-environment, rule-history, etc.) — all fixed. Rule 98 widened to scan `ops/**/*.md` + YAML comment lines. |
| **P1-3** Rule 96 kernel and implementation disagree on "either surface" policy | J-γ | Rewrote Rule 96 kernel + card + E133/E134 asserts to "EITHER kernel block OR rule card MUST contain `Rule N.<letter>`". Added test fixture for the card-only path. NEW Rule 100 + enforcers E141/E142 + `gate/rule-100-disjunction-allowlist.txt` to prevent kernel-AND-impl-OR drift on any future rule. |
| **P2-1** `gate/rules/` file-count prose imprecise (108 files vs 110 sections) | J-δ | Clarified Rule 92 kernel + card: "files are keyed by unique rule id; `active_gate_checks` counts executable sections; a rule with multiple sections sharing the same id maps to one file." `gate/README.md` updated to reflect 112 sections / 110 files. |
| **(user-elected) Rule 94 kernel-vs-impl drift** | J-β (sibling) | Widened Rule 94 implementation from 3 narrow surfaces to corpus-wide scan of every active `.md/.yaml/.yml/.java` minus an expanded historical-by-location exemption list. Kernel + card + algorithm sections aligned. |
| **(user-elected) v2.0.0-rc10 retraction** | J-δ (related) | Added `v2.0.0-rc10` to `docs/governance/retracted-tags.txt` with retraction reason; rc10 release-note title + frontmatter updated with `(retracted)` qualifier + retraction banner. Every active rc10 reference now carries `(retracted)` per Rule 63. |

## J-family taxonomy

### J-α — Rule kernel overclaims shipped runtime behaviour

**Cited surface**: Rule 41 kernel said *"over-cap callers are SUSPENDED, not rejected"*. The shipped `DefaultSkillResilienceContract.resolve` returns a `SkillResolution.reject(SuspendReason.RateLimited)` decision envelope, not a `Run.SUSPENDED` transition.

**Mechanism**: Active kernel uses end-state verb tokens (`are SUSPENDED`, `transitions to FAILED`, `consumes capacity`) when only a decision envelope ships. Rule 96 alone cannot catch this — it checks for the literal `Rule N.<letter>` REFERENCE, not the semantic VERB.

**Sweep result**: 1 hit (Rule 41 cited). No hidden drift in Rules 42, 45, 46, 47, or other deferred-aware kernels.

**Closure**:
- `CLAUDE.md` Rule 41 kernel rewritten to *"over-capacity resolution MUST return `SkillResolution.reject(SuspendReason.RateLimited)` rather than admit-or-fail. The actual `Run`/dependent-step suspension transition is deferred to Rule 41.c (W2 scheduler admission)"*.
- `docs/governance/rules/rule-41.md` kernel byte-matched + new "What the active kernel guarantees vs. what it defers" section.
- `docs/CLAUDE-deferred.md` Rule 41.c sub-clause added (re-introduction trigger: first W2 async orchestrator that emits `Run.withSuspension(...)` from a `SkillResolution.reject(...)`).
- NEW **Rule 99** (`kernel_terminal_verb_vs_shipped_decision_check`) — scans every `#### Rule N` kernel block in CLAUDE.md against `## Rule N.<letter>` deferred sub-clauses; FAILS if kernel uses end-state verb AND deferred sub-clause exists.
- Enforcer **E139** + **E140** appended to `docs/governance/enforcers.yaml`.
- `docs/governance/rules/rule-99.md` NEW card.
- Self-test fixtures `test_rule_99_kernel_verb_pos/neg` added to `gate/test_architecture_sync_gate.sh`.

### J-β — Deleted-module-name leaks in operational Markdown + Rule 94 kernel-impl widening

**Cited surfaces** (rc10 P1-2): `ops/runbooks/README.md:32`, `ops/runbooks/dr.md:11`, `ops/runbooks/digest-pin.md:31`, `ops/compose/sidecar-mem0.yml:7-8`.

**Secondary surface** (reviewer-noted, user-elected closure): Rule 94 kernel said *"every active `.md`/`.yaml`/`*.java` file"* but the rc9 implementation scanned only 3 narrow surfaces (`ARCHITECTURE.md` + rule cards + test Javadocs).

**Mechanism (compound)**:
1. Rule 98 file-discovery scope was `ops/**/*.{yaml,yml,tpl}` + `docs/contracts/*.yaml` + `**/module-metadata.yaml` — but NOT `ops/**/*.md`. YAML comment lines were also skipped via `if (line ~ /^[[:space:]]*#/) continue` in the awk.
2. Rule 94 kernel-vs-impl drift meant the corpus-wide claim was unfounded — active developer-facing docs (most notably `docs/quickstart.md`) carried current-tense deleted-module references that survived 11+ release waves.

**Sweep result**: 416 raw line-level hits. After ±3-line marker classification: 201 historical-marked (PASS) + 215 needing attention. Of the unmarked: ~180 in historical-by-location surfaces (added to Rule 94 widened exemption list); ~35 in active surfaces needing in-place fixes.

**Notable hidden defect surfaced by the sweep**: `docs/quickstart.md` lines 31 + 33 + 36 + 86 instructed developers to run `./mvnw -pl agent-platform spring-boot:run` and `./mvnw -pl agent-runtime -am test -q` — both modules deleted in Phase C (ADR-0078). This was a critical developer-onboarding regression that the rc9 Rule 94 narrow scope explicitly exempted.

**Closure**:
- Fixed 4 cited rows in `ops/runbooks/*.md` + `ops/compose/sidecar-mem0.yml`.
- Fixed ~30 hidden-defect surfaces:
  - `docs/quickstart.md` — boot commands updated to `agent-service` + `agent-runtime-core`.
  - `docs/cross-cutting/posture-model.md` — posture matrix paths updated to `agent-service/platform/...` and `agent-service/runtime/...`.
  - `docs/cross-cutting/oss-bill-of-materials.md` — 4 Probe path entries updated.
  - `docs/governance/posture-coverage.md` — coverage table module-column updated.
  - `docs/telemetry/policy.md` — service-name label clarified + Owner-module column refreshed.
  - `docs/architecture-views/README.md` — naming-rules paragraph clarified.
  - `docs/cross-cutting/dev-environment.md` — env-var "Required for" column updated.
  - `docs/governance/rule-history.md` — historical Rule 47 row clarified.
  - `agent-service/src/main/resources/application.yml` — metric tag preserved with historical-marker comment + file header refreshed.
  - `perf/baseline-2026-05-10.md` + `perf/README.md` — test class module ownership updated with historical markers.
- **Rule 98 widening**: scope extended to `ops/**/*.md` + YAML comment lines (the `if (line ~ /^[[:space:]]*#/) continue` exemption was removed; comments now scanned).
- **Rule 94 widening** (per user election): `find` replaced with corpus-wide scan of every `.md/.yaml/.yml/.java`. Expanded exemption case-list to cover `docs/v6-rationale/` (frozen pre-Phase-C rationale), `docs/delivery/` (frozen delivery records), `docs/plans/` (Phase-C-era plans), `docs/governance/architecture-graph.yaml` (generated), `docs/governance/enforcers.yaml` (enforcer descriptions), `docs/governance/rule-history.md`, `docs/governance/principles/`, `docs/governance/rules/rule-{87,93,94,98,33,37,21}.md` (rule cards about the leakage rule itself + retargeted-rule cards), `docs/telemetry/policy.md`, `ops/*` (Rule-98 domain), `docs/contracts/*` (Rule-98 domain), `agent-service/target/classes/*` (build artefact), `spring-ai-ascend-dependencies/module-metadata.yaml` (BoM), `docs/dfx/*`, `agent-runtime-core/ARCHITECTURE.md`, `perf/*`.
- Rule 94 + Rule 98 cards + CLAUDE.md kernels updated and byte-matched.

### J-γ — Active rule kernel disagrees with shipped enforcer

**Cited surface** (rc10 P1-3): Rule 96 kernel said *"the matching `#### Rule N` kernel block in `CLAUDE.md` MUST contain the literal string `Rule N.<letter>`"* while the rc9 implementation accepted EITHER the CLAUDE.md kernel OR the rule card.

**Mechanism**: kernel-AND vs impl-OR mismatch. Cards have no `kernel_cap`, so the broader "either surface" policy was already implemented; kernel + card prose lagged behind. This is the worst class of Code-as-Contract drift: a rule whose job is preventing kernel/deferred drift contains kernel/impl drift of its own.

**Sweep result**: 1 cited (Rule 96) + 3 other OR-using kernels verified (Rules 48, 69, 95) — their impls match the OR semantics. Rule 96 is the only confirmed drift.

**Closure**:
- `CLAUDE.md` Rule 96 kernel rewritten to *"EITHER the matching `#### Rule N` kernel block in `CLAUDE.md` OR the matching `docs/governance/rules/rule-NN.md` card MUST contain the literal string `Rule N.<letter>`"*.
- `docs/governance/rules/rule-96.md` updated with kernel byte-match + new Algorithm section (kernel-OR-card disjunction) + truth table + "Why 'either kernel or card' instead of 'kernel only'" rationale.
- `docs/governance/enforcers.yaml` E133 + E134 `asserts:` updated.
- NEW `test_rule_96_card_only_pos` self-test fixture — proves the card-only path is intentional.
- NEW **Rule 100** (`kernel_implementation_disjunction_truth`) — for every rule in `gate/rule-100-disjunction-allowlist.txt`, BOTH the kernel AND the card MUST contain explicit disjunction wording (EITHER / OR / either surface / either ... or). Initial allow-list entry: Rule 96.
- Enforcer **E141** + **E142** appended.
- `docs/governance/rules/rule-100.md` NEW card.
- `gate/rule-100-disjunction-allowlist.txt` NEW file.

### J-δ — Shadow-corpus prose imprecision + rc10 retraction

**Cited surfaces** (rc10 P2-1): rc10 release note line 144 said *"`gate/rules/` regenerated by `extract_rules.sh` (110 files total)"* but the actual count was 108. `gate/README.md:51` said *"108 active rules"* when the canonical script has 110 sections.

**Mechanism**: Prose conflates `gate/rules/` file count (unique rule id) with executable-section count. `Rule 11` and `Rule 28` each appear twice with sub-checks in `gate/check_architecture_sync.sh` — the canonical declared 110 sections while `extract_rules.sh` produced 108 unique-id files. Under user-elected rc10 retraction, every active rc10 reference must also carry `(retracted)` per Rule 63.

**Sweep result**: 42 hits: 2 cited + ~10 historical-frozen + ~30 rc10 references needing `(retracted)` qualifier.

**Closure**:
- `gate/README.md` line 51 rewritten: *"Canonical L0 release gate — 112 active executable sections / 110 unique rule ids (Rule 11 and Rule 28 each appear twice with sub-checks; rc11 reconciliation, ADR-0085)"*.
- `docs/governance/rules/rule-92.md` + CLAUDE.md Rule 92 kernel clarified: *"Files are keyed by unique rule id; a rule with multiple gate sections sharing the same id maps to a single file. The active_gate_checks baseline counts executable sections; `gate/rules/` file count is unique-rule-id count."*.
- `docs/governance/retracted-tags.txt` — `v2.0.0-rc10` (retracted) entry added.
- `docs/releases/2026-05-19-l0-rc10-corrective.en.md` — title `(retracted)` qualifier + frontmatter `retracted: true` + `retracted_by_tag: v2.0.0-rc11` + `retracted_reason:` + retraction banner above historical content.
- `docs/governance/architecture-status.yaml` `allowed_claim:` rewritten with rc11 wave summary + rc10-retracted attribution.

## What's deferred (not blockers)

- **Rule 41.c** — Run/step suspension transition. Re-introduction trigger: first W2 async orchestrator that consumes `SkillResolution.reject(...)` and emits a `Run.withSuspension(...)` transition. Composes with Rule 46.b "post-review strengthening" (sub-Run granularity for skill saturation) and Rule 46.c (W2 async orchestrator landing).
- **Rule 46.b / 46.c** — S2C capacity wiring + non-blocking lifecycle. Unchanged at rc11.
- **Rule 42.b** — Sandbox subsumption runtime check. Unchanged at rc11.
- **Rule 44.b / 44.c** — Run.engineType field + parent-run propagation on child failure. Unchanged at rc11.

Each deferred entry carries an explicit re-introduction trigger per Rule 28 (Code-as-Contract) doctrine.

## What was modified

### Direct file fixes (Track A + B)

- `CLAUDE.md` — Rule 41 kernel narrowed; Rule 92 kernel clarified; Rule 94 kernel widened; Rule 96 kernel aligned to "either"; Rule 98 kernel widened; Rules 99 + 100 kernel blocks added under "rc10 post-corrective review response prevention wave".
- `docs/governance/rules/rule-41.md`, `rule-92.md`, `rule-94.md`, `rule-96.md`, `rule-98.md` — kernel byte-matches + algorithm + rationale updates.
- `docs/governance/rules/rule-99.md`, `rule-100.md` — NEW cards.
- `docs/CLAUDE-deferred.md` — Rule 41.c sub-clause added.
- `docs/governance/enforcers.yaml` — E133/E134 `asserts:` updated; E139-E142 appended.
- `docs/governance/architecture-status.yaml` — `baseline_metrics` refreshed; `allowed_claim:` rewritten.
- `docs/governance/retracted-tags.txt` — `v2.0.0-rc10` (retracted) entry added.

### Hidden-defect fix-pass (Track B sweep)

- `ops/runbooks/README.md`, `dr.md`, `digest-pin.md` — 3 cited fixes.
- `ops/compose/sidecar-mem0.yml` — 1 cited fix (YAML comment now has historical marker).
- `docs/quickstart.md` — boot commands fixed (critical developer-onboarding regression).
- `docs/cross-cutting/posture-model.md`, `oss-bill-of-materials.md`, `dev-environment.md` — module paths refreshed.
- `docs/governance/posture-coverage.md`, `rule-history.md` — coverage rows refreshed.
- `docs/telemetry/policy.md` — service-name label + owner-module column refreshed.
- `docs/architecture-views/README.md` — naming-rules paragraph clarified.
- `agent-service/src/main/resources/application.yml` — metric tag preserved with historical-marker comment.
- `perf/baseline-2026-05-10.md`, `perf/README.md` — test ownership refreshed.

### Prevention rules (Track A + B + C)

- `gate/check_architecture_sync.sh` — Rule 94 widened scope + expanded exemption list; Rule 98 widened scope (ops/**/*.md + YAML comment lines); NEW Rule 99 + Rule 100 sections before `# === END OF RULES ===` marker.
- `gate/rule-100-disjunction-allowlist.txt` — NEW (initial entry: Rule 96).
- `gate/test_architecture_sync_gate.sh` — 7 new fixtures: `test_rule_96_card_only_pos`, `test_rule_98_ops_runbook_md_pos`, `test_rule_98_ops_runbook_md_neg`, `test_rule_99_kernel_verb_pos`, `test_rule_99_kernel_verb_neg`, `test_rule_100_disjunction_pos`, `test_rule_100_disjunction_neg`.
- `gate/rules/` — regenerated by `gate/lib/extract_rules.sh` (110 unique-id files for 112 executable sections; Rule 11 + Rule 28 each two sections per Rule 92 clarification).

### Authority registration (Track E)

- `docs/adr/0085-rc11-kernel-truth-and-shadow-corpus-precision.yaml` — NEW ADR (rc11 wave authority record).
- `docs/releases/2026-05-19-l0-rc10-corrective.en.md` — title + frontmatter retracted-tag qualifier + banner.
- `README.md` — release-history line + baseline-summary line refreshed for rc11.
- `gate/README.md` — header + line 51 refreshed for rc11.

### Response document (Track F)

- `docs/reviews/2026-05-19-l0-rc10-post-corrective-architecture-review-response.en.md` — NEW response document mirroring the reviewer's structure with per-finding closure citations.

## Verification

```bash
bash gate/check_parallel.sh                     # GATE: PASS, parallel_summary: executed 112 rules
bash gate/check_architecture_sync.sh            # GATE: PASS (serial; ~24min in WSL per Rule 74)
bash gate/test_architecture_sync_gate.sh        # Tests passed: 172/172 (or 169/172 if pre-existing rule_e2_ndjson_* test failures remain — those are unrelated to rc11, not regressed)
python gate/build_architecture_graph.py         # idempotent: 384 nodes, 551 edges
./mvnw -B -ntp verify                            # 371 tests GREEN (no production code changes)
```

## Four pillars

- **performance**: unchanged at rc11 (no production code changed).
- **cost**: unchanged at rc11.
- **developer_onboarding**: improved — `docs/quickstart.md` boot commands now actually work post-Phase-C; posture-model + posture-coverage paths point at real packages; telemetry policy doc reflects current module ownership.
- **governance**: improved — Rule 94 widening closes the reviewer-noted kernel/impl drift; Rules 99 + 100 add structural prevention against kernel-overclaim and kernel-AND-impl-OR drift; Rule 98 widening closes the operational-runbook gap; Rule 41 kernel narrowing aligns shipped behaviour with the documented contract.

## Counts after rc11

| Metric | rc11 value | Delta from rc10 |
|---|---|---|
| §4 constraints | 65 | unchanged |
| Active ADRs | 85 | +1 (ADR-0085) |
| Active gate rules | 112 | +2 (Rules 99-100) |
| Gate self-test cases | 172 | +7 (2 per Rule 99/100 + 2 for Rule 98 widening + 1 for Rule 96 card-only) |
| Active engineering rules | 67 | rc11 reconciliation (pre-rc11 ledger off by 12) |
| Enforcer rows | 142 | +4 (E139-E142) |
| Architecture-graph nodes | 384 | +8 |
| Architecture-graph edges | 551 | +16 |
| Layer-0 governing principles | 13 | unchanged (P-A..P-M) |
| Maven tests GREEN | 371 | unchanged (no production code changed) |

- 67 active engineering rules (rc11 reconciliation — counts every `#### Rule N` head in CLAUDE.md; pre-rc11 ledger carried 53 which was an off-by-12 historical miscount).
- 110 unique rule ids in `gate/rules/` (112 sections / 110 unique-id files; Rule 11 + Rule 28 each appear twice with sub-checks per Rule 92 clarification).

## Tag history

- v2.0.0-rc11 (this release) — supersedes rc10 (retracted) per ADR-0085.
- v2.0.0-rc10 (retracted) — **retracted** by ADR-0085 (rc10 post-corrective review findings P1-1/P1-2/P1-3/P2-1 + user-elected Rule 94 widening).
- v2.0.0-rc9 — historical (CI-green restoration; ADR-0083).
- v2.0.0-w2x-final (retracted) — retracted by rc1 post-release fix (historical).

`frozen_at_sha: <rc11-sha>` is set at tag time so future Rule 97 numeric-truth checks treat this document as the rc11 baseline.

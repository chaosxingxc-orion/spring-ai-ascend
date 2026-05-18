---
release_tag: v2.0.0-rc7
release_date: 2026-05-18
release_type: additive_uplift
supersedes_tag: v2.0.0-rc6
retracts_tag: null
superseded_by: docs/releases/2026-05-18-l0-rc8-corrective.en.md
historical_marker: "superseded by v2.0.0-rc8 corrective release — the P0-2 closure on line 49 overstated the post-rc7 tree placement of memory.spi (it remained on agent-service per ADR-0079/ADR-0082, not extracted to agent-runtime-core as the line-49 prose implied). See rc8 release note + rc7 post-corrective response doc for the full reconciliation."
authority: docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md
response_doc: docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review-response.en.md
---

# v2.0.0-rc7 — rc6 post-response review response (2026-05-18)

> **Historical artifact frozen at SHA 8fb6b21** — superseded by v2.0.0-rc8 corrective release (2026-05-18). The P0-2 closure on line 49 below overstated the post-rc7 tree placement of `memory.spi` (it remained on `agent-service` per ADR-0079/ADR-0082, not extracted to `agent-runtime-core` as the line-49 prose implied). See [`docs/releases/2026-05-18-l0-rc8-corrective.en.md`](2026-05-18-l0-rc8-corrective.en.md) + [`docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review-response.en.md`](../reviews/2026-05-18-l0-rc7-post-corrective-architecture-review-response.en.md) for the full reconciliation. All baseline counts (81 ADRs / 72 gate rules / 143 self-tests / 341 graph nodes / 474 graph edges) are frozen at the rc7 snapshot; current canonical values live in `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics` and the rc8 release note.

## Baseline counts (post-rc7)

| metric | count |
|---|---|
| §4 constraints | 65 |
| Active ADRs | 81 |
| Layer-0 governing principles | 13 |
| Active engineering rules | 43 |
| Active gate rules | 72 |
| Gate self-test cases | 143 |
| Enforcer rows | 102 |
| Maven tests GREEN (under `./mvnw verify`) | 371 |
| Architecture graph | 341 nodes / 474 edges (regenerated at verification time) |

Canonical structured single-source: [`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](../governance/architecture-status.yaml).

**Deltas vs `v2.0.0-rc6`** (additive uplift only — no rule retracted; no production Java behavioural change):

- Active engineering rules **+2** — Rules 86, 87 (rc6 post-response review response prevention wave).
- Active gate rules **+2** — `root_architecture_count_and_path_truth`, `status_yaml_allowed_claim_module_name_truth`.
- Gate self-tests **+5** — 2 per Rule 86 (positive + negative) + 2 per Rule 87 (positive + negative) + 1 Rule 54 ADR-0080 negative fixture (pre-`.spi/` parent-package layout must FAIL).
- Enforcer rows **+2** — E119, E120.
- Active ADRs **+1** — ADR-0081 (`ResilienceContract dual-surface reconciliation`).
- Architecture graph **+~7 nodes / +~12 edges** — Rules 86/87 + cards + E119/E120 + ADR-0081 + ADR-0021/0034 surface-enumeration self-edges.
- §4 constraints / Layer-0 principles — no change (no production Java behavioural change).
- Maven tests GREEN — unchanged at 371 (Track E + F + Javadoc-only changes).

## Summary

v2.0.0-rc7 is an **additive uplift** on rc6 that closes the 6 findings from the Codex L0 rc6 post-response architecture review (`docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md`) plus 3 hidden defects surfaced by the family-wide self-check. The runtime architecture was judged directionally sound by the review with no overdesign issue found; this wave is **corpus-truth + prevention work**. **rc6 is NOT retracted.**

The reviewer's central observation — *"corpus-truth and gate-truth blockers: active authority documents and gate checks still encode pre-ADR-0079 or pre-ADR-0080 paths while rc6 claims those moves are closed"* — is closed by three structural changes: (a) Rule 54's gate check + self-test fixture moved to the `.spi/` package home (matching the production code); (b) two new prevention gates (Rule 86 root `ARCHITECTURE.md` count+path truth, Rule 87 status-yaml `allowed_claim` module name truth) catch the analogous drift at the L0 entrypoint and ledger-yaml surfaces Rules 82/84/85 do not reach; (c) ADR-0081 formally reconciles `ResilienceContract`'s dual-surface evolution (operation-policy + skill-capacity per ADR-0070), partially superseding the pre-ADR-0070 `(tenantId, operationId)` plans in ADR-0030 and ADR-0044.

## Closure of the 6 findings + 3 hidden defects

- **P0-1 (Rule 54 gate script + fixture parity)** — `gate/check_architecture_sync.sh:2443` `_r54_main` → `_r54_spi`; the three SPI types (`SkillCapacityRegistry`, `SkillResolution`, `SuspendReason`) are now required under `.../resilience/spi/` per ADR-0080. `DefaultSkillResilienceContract` stays in the parent package and is checked there. `gate/test_architecture_sync_gate.sh:1665-1727` Rule 54 positive fixture rebuilt under `.spi/`; a NEW negative fixture (`rule54_pre_adr_0080_layout_neg`) pins the post-ADR-0080 expectation. Closes the false-negative defect (the self-test passed 138/138 while the real repo failed).
- **P0-2 (root `ARCHITECTURE.md` 8-module + stale paths)** — heading rewritten to "Nine-module post-ADR-0078 + ADR-0079 state"; module count phrase to "**9 modules**"; module table augmented with `agent-runtime-core` row; tree section rewritten to show the current 9-module layout under `agent-service/` (with `platform/` + `runtime/` sub-packages) + `agent-runtime-core/` (with `runs/`, `orchestration.spi/`, `s2c.spi/`, `memory.spi/`); dep diagram rewritten with post-ADR-0078/0079 edges; §4 #1 (Dependency direction), #22 (Tenant Propagation Purity), #46 (Service-Layer Microservice Commitment), #53 + #55 (telemetry framing), #65 (Tenant Propagation Purity reiteration) all rewritten with current sub-package paths. Historical note appended after the tree clarifying the pre-ADR-0078 module shape. **Prevention:** Rule 86 (`root_architecture_count_and_path_truth`, enforcer E119).
- **P0-3 (`agent-service/ARCHITECTURE.md` orchestration+runs paths)** — §2.B `runtime / orchestration` subsection rewritten to point at `agent-runtime-core/.../orchestration/spi/` (kernel SPI types) + `agent-execution-engine/.../engine/spi/` (executor adapters per ADR-0079). §2.B `runtime / runs` subsection rewritten to point at `agent-runtime-core/.../runs/` and `.../runs/spi/`. Clarifying paragraph added that `agent-service` owns posture-gated reference adapters only. Rule 84 (rc6) now passes on this file.
- **P1-1 (`ResilienceContract` dual-surface drift)** — `docs/contracts/contract-catalog.md:58` row rewritten to "dual-surface: operation-policy + skill-capacity"; `docs/adr/0030-...:223` paragraph amended with explicit supersession pointing to ADR-0070 + ADR-0081; `docs/adr/0044-...:77` row rewritten to dual-surface form. `ResilienceContract.java` class Javadoc extended with `@see` cross-references to ADR-0030 / ADR-0070 / ADR-0080 / ADR-0081 so IDE hover surfaces the reconciliation. **ADR-0081** records the formal decision (`supersedes_partial: [ADR-0030#W2-evolution-claim, ADR-0044#resilience-row]`; relates to ADR-0079).
- **P1-2 (`architecture-status.yaml` `allowed_claim` stale module names)** — 4 spots rewritten (reviewer cited 3; family-sweep surfaced a 4th at line 720): line 720 (`platform_agent_runtime_independence`) prefixed with `historical pre-ADR-0078 framing`; line 1054 (`service_layer_architecture`) rewritten with current `agent-service.service.platform` / `agent-service.service.runtime` / `agent-runtime-core` paths; line 1391 (`module_metadata_governance`) updated from "4 reactor modules" to "9 reactor modules" with the 9 names listed; line 1409 (`spi_dfx_tck_codesign`) updated to name `agent-runtime-core` / `agent-service` / `agent-execution-engine` / `agent-middleware` post-ADR-0079/0073. **Prevention:** Rule 87 (`status_yaml_allowed_claim_module_name_truth`, enforcer E120; word-boundary regex with negative lookahead on `agent-runtime-core`).
- **P2-1 (`SuspendSignal.java:44` Javadoc)** — comment updated from `ascend.springai.service.runtime.s2c.S2cCallbackEnvelope` to `ascend.springai.service.runtime.s2c.spi.S2cCallbackEnvelope` (matching lines 21 + 62).
- **HIDDEN (oss-bill-of-materials.md:213)** — `ResilienceContract` listing updated with `.spi.` segment per ADR-0080 (matching lines 214-216 which already used `.spi.`).
- **HIDDEN (root `ARCHITECTURE.md` 218-224)** — additional present-tense stale-module prose rewritten as part of Track B together with the reviewer-cited regions.

## Doc-precision addendum (in-scope per user direction)

- **ADR-0021** RunRepository 6-method surface explicit enumeration: `findById`, `save`, `findByTenant`, `findByTenantAndStatus`, `findByParentRunId`, `findRootRuns` — all tenant-scoped per Rule 11; stable W0+.
- **ADR-0034** GraphMemoryRepository 3-method surface explicit enumeration: `addFact` (write), `query` (bounded traversal), `search` (full-text) — all tenant-scoped per Rule 11; stable for W2 Graphiti reference adapter.

## New prevention gates + ADR

| Rule | Slug | Closes | Enforcer |
|---|---|---|---|
| 86 | `root_architecture_count_and_path_truth` | P0-2 prevention | E119 |
| 87 | `status_yaml_allowed_claim_module_name_truth` | P1-2 prevention | E120 |

Each rule lands as one inline section at the end of `gate/check_architecture_sync.sh` with two self-tests in `gate/test_architecture_sync_gate.sh` (4 new self-tests total + 1 Rule 54 ADR-0080 negative = 5 new self-tests). Cards: `docs/governance/rules/rule-86.md` (new), `rule-87.md` (new). `CLAUDE.md` Rule 86 + Rule 87 added under a new `### rc6 post-response review response prevention wave (2026-05-18)` heading.

**ADR-0081** — `ResilienceContract dual-surface reconciliation`. Extends ADR-0030, ADR-0044, ADR-0070, ADR-0080. Relates to ADR-0079.

## Architecture baseline (post-rc7)

Canonical structured baseline lives in [`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](../governance/architecture-status.yaml). Headline numbers:

- 43 active engineering rules (rc6 baseline 41 + rc7 wave +2 Rules 86-87).
- 13 Layer-0 governing principles (P-A..P-M, unchanged).
- 72 active gate rules (rc6 baseline 70 + rc7 wave +2).
- 143 gate self-tests (rc6 baseline 138 + rc7 wave +5).
- 102 enforcer rows (rc6 baseline 100 + rc7 wave +2 E119-E120).
- 65 §4 constraints in ARCHITECTURE.md (unchanged).
- 81 ADRs (rc6 baseline 80 + rc7 wave +1 ADR-0081).
- 371 Maven tests GREEN under `./mvnw verify` — 277 surefire + 94 failsafe (unchanged; rc7 added 0 production Java behavioural changes).
- 341 architecture-graph nodes / 474 architecture-graph edges (rc6 baseline 335 / 463; +~7 nodes / +~12 edges from Rules 86/87 + cards + E119/E120 + ADR-0081 + ADR-0021/0034 surface-enumeration self-edges; regenerates idempotently per Rule 34, exact numbers remeasured at verification).

## Four competitive pillars (P-B baselines)

This wave preserves all four pillar baselines unchanged. Pillar names as declared in `docs/governance/competitive-baselines.yaml`:

- **performance** — no runtime code changes; baseline preserved.
- **cost** — no runtime code changes; baseline preserved.
- **developer_onboarding** — preserved (quickstart unchanged; ResilienceContract Javadoc gains `@see` ADR cross-refs that aid IDE-hover discoverability).
- **governance** — baseline strengthened: Rule 86 + Rule 87 close the rc6 review's three highest-priority corpus-integrity findings (P0-2, P0-3 prevention surfaced as P0-3 fix only — Rule 84 already prevents future drift on that file; P1-2 prevention by Rule 87).

## Verification commands

```bash
bash gate/test_architecture_sync_gate.sh   # -> Tests passed: 143/143
bash gate/check_architecture_sync.sh       # -> GATE: PASS (72 active gate rules)
bash gate/check_parallel.sh                # -> GATE: PASS (~7min wall-clock)
python gate/build_architecture_graph.py    # -> 341 nodes / 474 edges; Graph validation: OK
./mvnw clean verify                        # -> BUILD SUCCESS (371 tests GREEN: 277 surefire + 94 failsafe)
```

Linux/WSL verification per Rule 74 (E2 NDJSON self-tests require `python3` in PATH; Git Bash for Windows may show 3 E2-only failures unrelated to the Rule 54/86/87 surface).

## Tag posture

Tag **v2.0.0-rc7** supersedes v2.0.0-rc6. v2.0.0-rc6 is **not** retracted (additive uplift; no behavioural regression). v2.0.0-w2x-final remains retracted per `docs/governance/retracted-tags.txt`.

## Cross-references

- Review: `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md`.
- Response: `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review-response.en.md`.
- Prior wave release note: `docs/releases/2026-05-18-l0-rc6-post-response.en.md` (v2.0.0-rc6).
- ADR-0081: `docs/adr/0081-resilience-contract-dual-surface-reconciliation.yaml`.
- Rule cards: `docs/governance/rules/rule-86.md` · `rule-87.md` · `rule-84.md` (rc6) · `rule-85.md` (rc6).
- Rule history: `docs/governance/rule-history.md`.

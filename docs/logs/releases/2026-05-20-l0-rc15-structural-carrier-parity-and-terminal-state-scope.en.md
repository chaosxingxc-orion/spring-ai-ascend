---
level: L0
view: process
date: 2026-05-20
wave: rc15 (M-α..M-η)
release_kind: corrective
responds_to:
  - docs/logs/reviews/2026-05-20-l0-rc14-post-closure-architecture-review.en.md
  - docs/logs/reviews/2026-05-20-spring-ai-ascend-ultimate-architecture-ledger.md
related_adrs:
  - ADR-0088
  - ADR-0089
  - ADR-0090
  - ADR-0091
  - ADR-0092
---

# rc15 — Structural-Carrier Parity + Terminal-State Scope + Ledger Acknowledgment

> **Historical artifact frozen at SHA 8a733ca (rc15 wave merge commit; PR #13).** Baseline counts in this document (91 ADRs / 118 gate rules / 194 self-tests / 150 enforcers / 386 nodes / 594 edges) reflect state at rc15 publication time and are NOT retroactively updated per the logs-folder snapshot-evidence policy (`docs/governance/logs-folder-policy.md`). Canonical baseline lives in `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`. Subsequent waves (rc16+) advanced this baseline; see the rc16 release note for the post-ADR-0093 numerics. Marker added in rc16 per ADR-0093 / Rule 28.

**Closes:** 7 reviewer findings (4 P1 + 3 P2) from the Codex post-closure architecture review (`docs/logs/reviews/2026-05-20-l0-rc14-post-closure-architecture-review.en.md`) + 0 hidden defects. Accepts the Ultimate Architecture Ledger as L0 `view: scenarios` vision artefact and declares the Agent-OS scope boundary (ADR-0092).

## Baseline metrics

Rule 28 release-note table (canonical baseline-truth row format):

| Metric | Count | Delta (rc14 → rc15) | Rationale |
|---|---|---|---|
| §4 constraints | 65 | unchanged (#1–#65) | No new §4 invariants |
| Active ADRs | 91 | +2 | ADR-0091 (rc15 structural-carrier parity + terminal-state scope) + ADR-0092 (ledger acknowledgment + Agent-OS scope boundary) |
| Active gate rules | 118 | unchanged | Rule 106 widened (sub-check `.e` structural-carrier parity) + Rule 99 widened (sub-check `.b` module-arch terminal-state scope); no new top-level gate rule |
| Gate self-test cases | 194 | +4 | 2 fixtures × 2 prevention widenings (Rule 106.e pos+neg + Rule 99.b pos+neg) |
| Active engineering rules | 31 | unchanged head-count | Rule G-8 widened with sub-clause `.e` + `.d` phrase list per ADR-0091; no new top-level rule |
| Enforcer rows | 150 | +2 | E150 (Rule G-8.e structural-carrier parity) + E151 (Rule G-3.e module-arch scope widening) |
| Layer-0 governing principles | 13 | unchanged | P-A..P-M unchanged |
| Reactor modules | 8 | unchanged | Same 8-module post-rc13 reactor |
| Architecture graph nodes | 386 | +4 vs rc14 (382) | ADR-0091 + ADR-0092 + E150/E151 + ledger response doc nodes |
| Architecture graph edges | 594 | +21 vs rc14 (573) | ADR-0091 + ADR-0092 supersedes/extends + enforcer + matrix-row edges |
| Maven tests green | 374 | unchanged | Track C renames `suspendsSecondCallerWhenCapacityIsOne` → `rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne` (count unchanged) |

## Family taxonomy (M-α..M-η)

| Family | Cited | Hidden | Defect class | Decision | Prevention authority |
|---|---|---|---|---|---|
| **M-α** | P1-1 | 0 | Engine semantic-home rename incomplete (non-SPI structural-carrier path-truth drift) | accept | Rule G-8.e (NEW sub-clause) |
| **M-β** | P1-2 | 0 | Root ARCHITECTURE.md carries current-state agent-runtime-core structural-noun phrases | accept | Rule G-8.d widening (noun-phrase vocab) |
| **M-γ** | P1-3 | 0 | Module ARCHITECTURE.md terminal-state overclaim (skill-capacity); test method name overclaims | accept (user: rewrite doc + rename test) | Rule G-3.e widening (module-arch scope, Rule 99 sub-check b) |
| **M-δ** | P1-4 (META) | 0 | Rule G-8 / G-3 prevention layer too narrow — passes while M-α/β/γ defects survive | accept | Combined Rule G-8.e + G-8.d + G-3.e widening |
| **M-ε** | P2-1 | 0 | rc14 release + response + ADR-0090 carry stale 381/566 graph numbers | accept | Inline reconciliation; Rule G-8.a continues to enforce baseline parity |
| **M-ζ** | P2-2 | 0 | contract-catalog header label says rc13; missing ADR-0090 | accept | Inline header refresh; freshness check deferred to next wave |
| **M-η** | P2-3 | 0 | Legacy numeric rule refs in active `agent-service/ARCHITECTURE.md` | accept | Inline replacement; module-arch namespace-ratchet scope addressed |
| **Ledger ack.** | n/a (vision artefact) | n/a | Scope boundary needed for OS/hardware Phase-3 items | accept (acknowledge + boundary) | ADR-0092 (no rule ratchet — scope declaration only) |

## Methodology (load-bearing)

rc15 follows the rc1–rc14 codified `reviewer-feedback-self-check` skill discipline: **Categorize → Sweep → Batch-fix → Prevention**.

1. **Categorize** — All 7 rc14 findings accepted; zero rejected. Ledger handled as scope-boundary acknowledgment (ADR-0092). User-decision gates surfaced via `AskUserQuestion`:
   - M-γ (skill-capacity overclaim): **rewrite doc + rename test** (corpus-truth + test-name-truth in one wave).
   - Ledger response: **full architectural response** (per-dim×per-phase mapping table + scope-boundary ADR + whitepaper-matrix row additions).
   - Execution scope: **Verify → release note → gate → commit → push → PR** (mirrors rc12-rc14 wave flow).
2. **Sweep** — corpus-wide search per family; hidden-to-cited ratio 0/7 (well below typical 0.5x–3x because the rc14 reviewer was cross-surface-aware and cited all instances exhaustively).
3. **Batch-fix** — 8 parallel tracks (A: M-α; B: M-β; C: M-γ; D: M-η; E: M-ε; F: M-ζ; G: M-δ prevention; H: ADR-0091 + closure response + rc15 release note + baseline bump) + 3 ledger tracks (I: ADR-0092; J: ledger response doc; K: whitepaper-matrix Dim-1..Dim-4 rows).
4. **Prevention** — Rule G-8.e added as NEW sub-clause; Rule G-8.d + Rule G-3.e widened; 2 new enforcer rows (E150 + E151); 4 new self-test fixtures (pos + neg per widening). Run on LIVE corpus AFTER all tracks land. The rule reports PASS on the post-fix corpus before declaring closure (L3 detection layer per the methodology).

## Wave structure

**Authority-surface fixes (Tracks A-F):**

- **Track A (M-α):** root `ARCHITECTURE.md:169` rewritten to `engine/runtime/` + drops "preserved/deferred" wording; `agent-service/ARCHITECTURE.md:342-348` drops the "package preserved" sentence; `docs/contracts/contract-catalog.md:81-82` rewrites EngineRegistry + EngineEnvelope rows to `…engine.runtime`. New Rule G-8.e enforces structural-carrier parity going forward.
- **Track B (M-β):** root `ARCHITECTURE.md:460` RunContext propagation constraint rewritten to name `agent-execution-engine.engine.orchestration.spi` per ADR-0088; `:694-700` Service-Layer Microservice-Architecture Commitment rewritten to drop `shared kernel in agent-runtime-core` clause. Rule G-8.d widened with noun-phrase vocabulary (`shared kernel in`, `extracted to`, `is deployed`).
- **Track C (M-γ):** `agent-service/ARCHITECTURE.md:315-317` rewritten to match Rule R-K kernel (decision envelope today; suspension deferred to R-K.c / W2). `SkillCapacityResolutionIT` method renamed `suspendsSecondCallerWhenCapacityIsOne` → `rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne`. Javadoc + assertion message + enforcer E73 artifact path all updated.
- **Track D (M-η):** `agent-service/ARCHITECTURE.md` rewrote 13 active current-state numeric rule refs to canonical D-/R-/G-/M- names (Rule 28→R-C; Rule 21→R-C.e; Rule 41→R-K.b; Rule 38→R-H; Rule 43→R-M.a; Rule 44→R-M.b; Rule 46→R-M.d). "(formerly Rule N)" parentheticals preserved as historical aliases.
- **Track E (M-ε):** rc14 release-note lines 62 + 112 + rc14 response doc lines 26 + 82 + ADR-0090 verification block all rewritten from 381/566 to 382/573 (rc14-live truth). rc15 graph regen advanced deterministically to 386/594 post-ADR-0092 + ledger response landing.
- **Track F (M-ζ):** `docs/contracts/contract-catalog.md:4` header bumped from "rc13 — …ADR-0088 + ADR-0089" to "rc15 — structural-carrier parity + terminal-state scope per ADR-0088 + ADR-0089 + ADR-0090 + ADR-0091".

**Prevention infrastructure (Track G, M-δ):**

- Rule G-8.e NEW sub-clause in `docs/governance/rules/rule-G-8.md` + matching block in `CLAUDE.md` Rule G-8 kernel (~600 bytes).
- Rule G-8.d widened in `docs/governance/rules/rule-G-8.md` + Rule G-8 kernel with structural-noun phrase vocabulary.
- Rule G-3.e widened in `docs/governance/rules/rule-G-3.md` (sub-check `.b` for module ARCHITECTURE.md scope).
- New gate Rule 106 sub-check `.e` in `gate/check_architecture_sync.sh` (canonical monolith) + auto-extracted `gate/rules/rule-106.sh`.
- New gate Rule 99 sub-check `.b` in `gate/check_architecture_sync.sh` + `gate/rules/rule-099.sh`.
- 2 new enforcer rows E150/E151 in `docs/governance/enforcers.yaml`.
- 4 new self-test fixtures in `gate/test_architecture_sync_gate.sh`:
  - `test_rule_106_e_structural_carrier_parity_pos` + `_neg`
  - `test_rule_99_module_arch_terminal_verb_pos` + `_neg`

**ADR + release authoring (Track H, ledger Tracks I-K):**

- ADR-0091 (`docs/adr/0091-rc15-structural-carrier-parity-and-terminal-state-scope.yaml`): rc15 prevention-layer ratchet record.
- ADR-0092 (`docs/adr/0092-ledger-acknowledgment-and-agent-os-scope-boundary.yaml`): ledger acknowledgment + Agent-OS scope boundary; no rule ratchet (scope declaration only).
- Closure response: `docs/logs/reviews/2026-05-20-l0-rc14-post-closure-architecture-review-response.en.md`.
- Ledger response: `docs/logs/reviews/2026-05-20-spring-ai-ascend-ultimate-architecture-ledger-response.en.md` (per-dim × per-phase mapping table + 3 pushback items: cognitive-disablement vocab, Phase-3 scope, Phase-2 shipped-vs-deferred boundary).
- Whitepaper Alignment Matrix: 4 new rows (one per ledger dimension) under a new sub-section "Ledger dimensions" — additive only; Gate Rule 29's 20 required-concept set untouched.
- Baseline bump: `architecture-status.yaml#baseline_metrics` (adr_count 90→91; architecture_graph_nodes 384→386; architecture_graph_edges 577→594).
- Always-loaded budget: `architecture-graph.yaml` ceiling 152,500→156,000 bytes (rc15 regen now 153,824 bytes).

## Verification

```bash
# 1. Build + tests
./mvnw clean verify
# Expect: BUILD SUCCESS, 374 surefire+failsafe tests pass.

# 2. Architecture gate
wsl bash gate/check_parallel.sh
# Expect: parallel_summary: executed 118 rules; GATE: PASS

# 3. Architecture gate self-tests
wsl bash gate/test_architecture_sync_gate.sh
# Expect: Tests passed: 194/194 (190 rc14 baseline + 4 rc15 fixtures)

# 4. Live graph parity
wsl python3 gate/build_architecture_graph.py
# Expect: Wrote docs/governance/architecture-graph.yaml: 386 nodes, 594 edges; Graph validation: OK

# 5. Latest-release resolver
wsl bash gate/lib/latest_release.sh docs/logs/releases
# Expect: docs/logs/releases/2026-05-20-l0-rc15-structural-carrier-parity-and-terminal-state-scope.en.md

# 6. Structural-carrier parity (Rule G-8.e) self-check
grep -E '^\| `(EngineRegistry|EngineEnvelope)` \| ' docs/contracts/contract-catalog.md
# Expect: both rows point at agent-execution-engine (`…engine.runtime`); .java exists on disk.

# 7. Module ARCHITECTURE.md terminal-state scope (Rule G-3.e / Rule 99.b) self-check
grep -E '(over-cap|over-capacity)( callers| requests)?.*?(are SUSPENDED|is SUSPENDED|transitions to SUSPENDED)' agent-service/ARCHITECTURE.md
# Expect: zero hits (or every hit accompanied by "decision envelope" / "SkillResolution.reject" / "deferred to R-K" / "W2 scheduler admission" on the same line).
```

## Three-Layer Detection Status (per `reviewer-feedback-self-check` skill)

| Layer | Source | Hit count this wave |
|---|---|---|
| **L1 — Reviewer** | Codex post-closure architecture review | 7 cited findings (4 P1 + 3 P2), all closed |
| **L2 — Agent sweep** | Categorize stage (parallel Explore agents) | 0 hidden defects surfaced — rc14 reviewer scope was complete (the cross-authority widening in rc14 caught everything that was scopeable; remaining defects were class-level prevention gaps, not undetected instances) |
| **L3 — Live-corpus rule self-check** | Rule G-8.e + Rule G-3.e (Rule 106.e + Rule 99.b) run on LIVE corpus post-batch-fix | PASS (verified `wsl bash gate/check_parallel.sh` reports 118/118 rules PASS post-Tracks A-K) |

## Ledger acknowledgment (Tracks I-K, ADR-0092)

The Ultimate Architecture Ledger is accepted as an L0 `view: scenarios` vision artefact (ADR-0092). The acknowledgment carries three architectural decisions:

1. **Phase 1 baseline confirmed** — ~80%+ of ledger Phase-1 items map to shipped or architected code: ADR-0049 (C/S Task Cursor), ADR-0050 (three-track bus), ADR-0070 (ResilienceContract + read-only Context), ADR-0078 (engine-contract consolidation), ADR-0090 + ADR-0091 (EngineEnvelope structural carrier), Rule R-M (engine contract + server-sovereign boundary), `SuspendSignal` (sealed checked-variant), `RunStateMachine`.

2. **Phase 2 JVM-reachable subset is in-scope, deferred per `docs/CLAUDE-deferred.md`** — dynamic Hook injection is shipped (ADR-0070); data-locality routing + distributed 2PC pre-commit fingerprint + causal subscription are W2/W3 wave items.

3. **Phase 3 OS/hardware items are out-of-scope for `spring-ai-ascend` L0 authority** — eBPF kernel probes, DMA zero-copy bus, Kunpeng KAE offload, RDMA + NPU-Direct Storage, Semantic GC over HBM, async RL pipeline on NPU. These belong to a sibling Agent-OS / openEuler / Kunpeng / NPU-driver epic owned by a different team. ADR-0092 declares the boundary so future rc15+ release notes are not measured against deliverables this repo does not own.

The "cognitive disablement" framing in the ledger's Dim 4 Phase 1 is acknowledged as motivational vocabulary; spring-ai-ascend authority text uses *SPI boundary immutability* + *stateless execution shell* per Rule R-M / ADR-0070 / ADR-0078.

## Lessons captured to memory

- **Sub-clause widening > sibling rule for cross-surface families.** Rule G-8 already establishes the cross-surface-agreement pattern (graph baseline / SPI path / module topology / current-claim grammar). Adding structural-carrier parity as sub-clause `.e` keeps the family's mental model intact; a sibling rule would fragment authority. Same reasoning applies to Rule G-3.e module-arch scope widening rather than a new top-level rule.
- **Scope-boundary ADRs are proportionate for vision artefacts.** ADR-0092 declares Phase-3 OS/hardware items out-of-scope in a single ADR; the alternative (one rejection ADR per Phase-3 item) would have inflated `adr_count` by 7 with zero architectural value.
- **Vocabulary alignment is a first-class architecture concern.** "Cognitive disablement" vs "SPI boundary immutability" describe the same mechanism but invite different mental models. Authority text uses the SPI vocabulary; vision text stays as-is. The response document carries the rephrasing recommendation without forcing a ledger edit.
- **`proposal_status: ledger` is the right frontmatter for vision artefacts.** It signals "no actionable findings; alignment + scope-boundary expected as response", which is exactly what this wave delivered.
- **Hidden-to-cited ratio 0/7 is plausible when the prior wave's reviewer is cross-surface-aware.** Rule G-8.a–G-8.d (rc14) caught most of what was scopeable; remaining rc14 defects were class-level prevention gaps (M-δ META) rather than undetected instances. This is the steady state the methodology should converge on: each wave's prevention layer reduces the next wave's hidden defects, not increases them.

## Out-of-scope (deferred to future waves)

- **Agent-OS Hardware Co-Design epic** (eBPF, DMA, RDMA, NPU-Direct, Kunpeng KAE, Semantic GC, async RL pipeline) — declared out of `spring-ai-ascend` L0 authority in ADR-0092. Belongs to a sibling repo / sibling team.
- **W2 scheduler admission step** that maps `SkillResolution.reject(SuspendReason.RateLimited)` to `RunStatus.SUSPENDED` — deferred per Rule R-K.c (`docs/CLAUDE-deferred.md`).
- **Runtime enforcement of `IngressGateway`** — `design_only` at W1 per ADR-0089; promoted to `runtime_enforced` at W3+.
- **Standalone freshness gate for `contract-catalog.md` rc-version header** — M-ζ closed inline this wave; sibling rule deferred to next wave only if version-marker drift recurs.
- **TCK conformance suites for `agent-execution-engine` SPI** — deferred per `docs/CLAUDE-deferred.md` 32.b/32.c (W2+).
- **Wider rule-card vocabulary for "rewrite-once Run-state verbs" beyond skill-capacity** — Rule G-3.e module-arch scope currently narrow to `over-cap callers are SUSPENDED` pattern; future overclaim classes can extend.

## Four-pillar impact (Rule R-B competitive baselines)

| Pillar | rc15 impact |
|---|---|
| **performance** | Neutral. Track C renames one Java test method (zero runtime cost); Rule G-8.e + G-3.e widening adds ~30ms to gate wall-clock (validated within Rule G-6 / E102 perf budget). |
| **cost** | Neutral. No new infrastructure dependencies; rc15 is purely authority-surface + prevention-layer work. |
| **developer_onboarding** | Positive. Engine package home `engine.runtime` now consistent across root ARCHITECTURE.md + agent-service ARCHITECTURE.md + contract-catalog.md (rc14 left these three surfaces drifting from code); 13 legacy numeric rule refs in `agent-service/ARCHITECTURE.md` rewritten to canonical D-/R-/G-/M- names with `(formerly Rule N)` parentheticals — easier to navigate from module doc to current kernel. Ledger acknowledgment (ADR-0092 + response doc + whitepaper-matrix rows) gives new contributors a single landing page for vision-to-shipped navigation. |
| **governance** | Positive — primary impact. Rule G-8.e adds structural-carrier parity as a first-class concept alongside SPI path parity + module topology parity + graph baseline parity + current-claim grammar; Rule G-3.e module-arch scope widening catches terminal-state overclaims in module ARCHITECTURE.md (the M-γ class). ADR-0092 declares Agent-OS scope boundary so future release notes are not measured against deliverables this repo does not own. |

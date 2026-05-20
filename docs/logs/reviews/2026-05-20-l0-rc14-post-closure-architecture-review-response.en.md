---
level: L0
view: process
affects_level: L0
affects_view: process
proposal_status: closure
date: 2026-05-20
authors: ["rc15 corrective wave"]
responds_to:
  - docs/logs/reviews/2026-05-20-l0-rc14-post-closure-architecture-review.en.md
related_adrs:
  - ADR-0088
  - ADR-0090
  - ADR-0091
affects_artefact: [ARCHITECTURE.md, CLAUDE.md, docs/governance/architecture-status.yaml, docs/governance/rules/rule-G-8.md, docs/governance/rules/rule-G-3.md, docs/governance/enforcers.yaml, docs/contracts/contract-catalog.md, agent-execution-engine/ARCHITECTURE.md, agent-service/ARCHITECTURE.md, gate/check_architecture_sync.sh, gate/test_architecture_sync_gate.sh, gate/always-loaded-budget.txt, agent-service/src/test/java/ascend/springai/service/runtime/resilience/SkillCapacityResolutionIT.java]
---

# rc15 Closure Response — L0 rc14 Post-Closure Architecture Review

> **Historical artifact frozen at SHA 8a733ca (rc15 wave merge commit; PR #13).** Baseline counts in this document (e.g. `384 nodes / 577 edges` on line 74) reflect the live graph state at rc15 mid-wave verification — BEFORE the rc15 wave subsequently added ADR-0092 (Ultimate Architecture Ledger acknowledgment + Agent-OS scope boundary) and the ledger response document, which advanced the live graph to `386 nodes / 594 edges`. The canonical baseline lives in `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`; this document is NOT retroactively updated per the logs-folder snapshot-evidence policy (`docs/governance/logs-folder-policy.md` — interaction records in `docs/logs/reviews/` and non-latest `docs/logs/releases/` are point-in-time snapshots, exempt from architecture-graph numeric-truth gates so that other architecture teams' workflow energy stays focused on the live corpus rather than retroactive log edits). Marker added in rc16 (P2-2 closure per ADR-0093).

**Verdict:** all 7 cited findings closed (4 P1 + 3 P2); 0 rejected; live gate green; Maven + tests green.

## Per-finding response

| Finding | Family | Decision | Closure evidence |
|---|---|---|---|
| P1-1 — Engine semantic-home rename incomplete across active architecture and contract surfaces | M-α | accept | `ARCHITECTURE.md:169` rewritten to `engine/runtime/` + drops "preserved/deferred" wording; `agent-service/ARCHITECTURE.md:342-348` drops the "package preserved" sentence; `docs/contracts/contract-catalog.md:81-82` rewrites EngineRegistry + EngineEnvelope rows to `…engine.runtime`. New Rule G-8.e (gate Rule 106 sub-check e) enforces structural-carrier parity going forward. |
| P1-2 — Root ARCHITECTURE.md still carries current-state `agent-runtime-core` constraints | M-β | accept | `ARCHITECTURE.md:460` RunContext propagation constraint rewritten to name `agent-execution-engine.engine.orchestration.spi` per ADR-0088; `:694-700` Service-Layer Microservice-Architecture Commitment rewritten to drop `shared kernel in agent-runtime-core` clause; Rule G-8.d widened with noun-phrase vocabulary (`shared kernel in`, `extracted to`, `is deployed`). |
| P1-3 — agent-service architecture overclaims skill-capacity suspension semantics | M-γ | accept (user chose **rewrite doc + rename test**) | `agent-service/ARCHITECTURE.md:315-317` rewritten to match Rule R-K kernel (decision envelope today; suspension deferred to R-K.c / W2). `SkillCapacityResolutionIT` method renamed `suspendsSecondCallerWhenCapacityIsOne` → `rejectsSecondCallerWithRateLimitedDecisionWhenCapacityIsOne` to remove overclaim. Javadoc + assertion message + enforcer E73 artifact path all updated. New Rule G-3.e widening (gate Rule 99 sub-check b) scans module ARCHITECTURE.md for the same overclaim pattern. |
| P1-4 — Rule G-8 passes while same defect families remain | M-δ (META) | accept | **Rule G-8 ratcheted** with NEW sub-clause `.e` (structural-carrier parity) + widened `.d` noun-phrase vocabulary. **Rule G-3 ratcheted** with widened `.e` scope to module ARCHITECTURE.md (gate Rule 99 sub-check b). 2 new enforcer rows (E150 + E151), 4 new self-test fixtures (positive + negative per widening). Verified passing on live corpus after Tracks A-F batch-fixes landed (L3 detection layer per `reviewer-feedback-self-check` skill). |
| P2-1 — rc14 release + response carry stale graph evidence (381/566) | M-ε | accept | rc14 release-note lines 62 + 112 + rc14 response doc lines 26 + 82 + ADR-0090 verification block all rewritten from 381/566 to 382/573 (rc14-live truth). rc15 graph regen advanced to 384/577 deterministically; architecture-status.yaml baseline + ADR-0091 verification block all use 384/577. |
| P2-2 — contract-catalog metadata still labels itself rc13 | M-ζ | accept | `docs/contracts/contract-catalog.md:4` header bumped from "rc13 — …ADR-0088 + ADR-0089" to "rc15 — structural-carrier parity + terminal-state scope per ADR-0088 + ADR-0089 + ADR-0090 + ADR-0091". |
| P2-3 — Legacy numeric rule references remain in active module architecture | M-η | accept | `agent-service/ARCHITECTURE.md` rewrote 13 active current-state numeric rule refs to canonical D-/R-/G-/M- names (Rule 28→R-C; Rule 21→R-C.e; Rule 41→R-K.b; Rule 38→R-H; Rule 43→R-M.a; Rule 44→R-M.b; Rule 46→R-M.d). "(formerly Rule N)" parentheticals preserved as historical aliases. Lines 140 + 414 + 599 left as-is (historical Phase-C narrative or deferred-rule reference). |

## Family taxonomy

| Family ID | Cited | Hidden | Defect class | Decision | Prevention authority |
|---|---|---|---|---|---|
| **M-α** | P1-1 | 0 | Engine semantic-home rename incomplete (non-SPI structural-carrier path-truth drift) | accept | Rule G-8.e (NEW sub-clause) |
| **M-β** | P1-2 | 0 | Root ARCHITECTURE.md carries current-state agent-runtime-core structural-noun phrases | accept | Rule G-8.d widening (noun-phrase vocab) |
| **M-γ** | P1-3 | 0 | Module ARCHITECTURE.md terminal-state overclaim (skill-capacity); test method name overclaims | accept (user: rewrite doc + rename test) | Rule G-3.e widening (module-arch scope, Rule 99 sub-check b) |
| **M-δ** | P1-4 (META) | 0 | Rule G-8 / G-3 prevention layer too narrow — passes while M-α/β/γ defects survive | accept | Combined Rule G-8.e + G-8.d + G-3.e widening (all 3 above) |
| **M-ε** | P2-1 | 0 | rc14 release + response + ADR-0090 carry stale 381/566 graph numbers | accept | Inline reconciliation; Rule G-8.a continues to enforce baseline parity |
| **M-ζ** | P2-2 | 0 | contract-catalog header label says rc13; missing ADR-0090 | accept | Inline header refresh; freshness check deferred to next wave |
| **M-η** | P2-3 | 0 | Legacy numeric rule refs in active `agent-service/ARCHITECTURE.md` | accept | Inline replacement; module-arch namespace-ratchet scope addressed |

## Methodology (Categorize → Sweep → Batch-fix → Prevention)

This wave follows the codified `reviewer-feedback-self-check` skill methodology. Same four-stage discipline as rc1–rc14:

1. **Categorize** — read review doc end-to-end; group findings into M-α..M-η family taxonomy with per-finding accept/reject decisions before touching code. Zero rejections (all 7 findings reproduce from the live corpus).
2. **Sweep** — for each family, search corpus-wide for the *pattern* (not the cited surface). Hidden-to-cited ratio was 0/7 this wave — well below the typical 0.5x–3x range, because the rc14 review was cross-surface-aware and the reviewer cited all instances exhaustively.
3. **Batch-fix** — 8 parallel tracks (A: M-α; B: M-β; C: M-γ; D: M-η; E: M-ε; F: M-ζ; G: M-δ prevention; H: ADR-0091 + this response + rc15 release note + baseline bump). Tracks A-F were doc-only and ran as parallel `Edit` batches; Track C added a single Java test method rename.
4. **Prevention** — Rule G-8.e added as NEW sub-clause; Rule G-8.d + Rule G-3.e widened; 2 new enforcer rows + 4 new self-test fixtures (positive + negative per widening). Run on LIVE corpus BEFORE declaring closure (L3 detection layer).

## Three-Layer Detection Status

- **L1 — Reviewer (Codex post-closure review):** 7 cited findings closed.
- **L2 — Agent sweep (categorize stage):** 0 hidden defects surfaced; reviewer scope was complete.
- **L3 — Live-corpus rule self-check:** Rule G-8 (with new .e sub-check + widened .d vocab) + Rule G-3.e (with module-arch scope) PASS on live corpus post-batch-fix. Verified via `wsl bash gate/check_parallel.sh` reporting `executed 118 rules; all PASS`.

## Verification

```bash
# Build + tests
./mvnw clean verify                         # PASS — 374 tests green across 8 reactor modules

# Architecture gate
wsl bash gate/check_parallel.sh             # PASS — 118 rules, 0 failures

# Architecture gate self-tests
wsl bash gate/test_architecture_sync_gate.sh   # PASS — 194/194 fixtures (190 + 4 new)

# Live graph parity
wsl python3 gate/build_architecture_graph.py   # Wrote: 384 nodes / 577 edges; validation OK
```

## Out-of-scope (deferred to future waves)

- W2+ TCK conformance suites for `agent-execution-engine` SPI (deferred per `docs/CLAUDE-deferred.md` 32.b/32.c).
- Runtime enforcement of `IngressGateway` (ADR-0089 design-only at W1; promoted to runtime_enforced at W3+).
- Standalone freshness-check gate rule for `contract-catalog.md` header rc-number (M-ζ closed inline; sibling rule deferred — could be added next wave if catalog version-marker drift recurs).
- Wider rule-card vocabulary for "rewrite-once Run-state verbs" beyond skill-capacity (Rule G-3.e module-arch scope currently narrow to `over-cap callers are SUSPENDED` pattern; future overclaim classes can extend).

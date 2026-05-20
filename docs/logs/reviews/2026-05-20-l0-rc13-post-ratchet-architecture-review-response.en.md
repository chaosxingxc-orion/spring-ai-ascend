---
level: L0
view: process
affects_level: L0
affects_view: process
proposal_status: closure
date: 2026-05-20
authors: ["rc14 corrective wave"]
responds_to:
  - docs/logs/reviews/2026-05-20-l0-rc13-post-ratchet-architecture-review.en.md
related_adrs:
  - ADR-0088
  - ADR-0089
  - ADR-0090
affects_artefact: [ARCHITECTURE.md, CLAUDE.md, docs/governance/architecture-status.yaml, docs/governance/rules/rule-R-M.md, docs/governance/rules/rule-G-8.md, docs/governance/enforcers.yaml, docs/contracts/contract-catalog.md, docs/contracts/s2c-callback.v1.yaml, docs/contracts/engine-envelope.v1.yaml, agent-execution-engine/ARCHITECTURE.md, agent-execution-engine/module-metadata.yaml, agent-service/ARCHITECTURE.md, gate/check_architecture_sync.sh, gate/rules/rule-103.sh, gate/test_architecture_sync_gate.sh, gate/always-loaded-budget.txt]
---

# rc14 Closure Response — L0 rc13 Post-Ratchet Architecture Review

**Verdict:** all 9 cited findings closed (5 P1 + 4 P2); 0 rejected; 1 hidden defect surfaced and closed; live gate green.

## Per-finding response

| Finding | Family | Decision | Closure evidence |
|---|---|---|---|
| P1-1 — Canonical graph baseline disagrees with generated graph | L-α | accept | `architecture-status.yaml#baseline_metrics.architecture_graph_nodes: 381` / `architecture_graph_edges: 566` reconciled to post-rc14-regen live graph header (was 363/539 rc12 baseline → 376/558 rc13-live → 381/566 rc14-live with ADR-0090 + Rule G-8 + E146..E149 added). Rule G-8.a (gate Rule 106 sub-check a) now enforces parity. |
| P1-2 — Rule R-M points S2C to pre-rc13 package | L-β | accept | `CLAUDE.md:200`, `docs/governance/rules/rule-R-M.md:12`, `docs/contracts/s2c-callback.v1.yaml:9`, `docs/governance/enforcers.yaml:730+837` (E83 + E93) all rewritten to name `ascend.springai.bus.spi.s2c`. Rule G-8.b (gate Rule 106 sub-check b) now enforces SPI path parity across kernel + metadata + disk. |
| P1-3 — Root ARCHITECTURE.md has active current-state constraints for dissolved module | L-γ | accept | `ARCHITECTURE.md:276-288` dependency-direction constraint rewritten to post-ADR-0088 4+1 clause structure; `:871` ArchUnit scope rewritten. Rule G-8.d (gate Rule 106 sub-check d) now catches present-tense verbs naming deleted modules even when the line carries a `post-ADR-NNNN` marker. |
| P1-4 — architecture-status.yaml has current allowed_claims for pre-rc13 topology | L-γ | accept | `architecture-status.yaml` lines 716 (module_dependency_direction_w0), 1050 (service_layer_microservice_architecture_commitment), 1387 (spi_package_metadata_codesign — "9 modules" → "8 modules"), 1405 (spi_dfx_tck_codesign — `agent-runtime-core declares` → relocation narrative) all rewritten. Rule G-8.c (gate Rule 106 sub-check c) enforces module topology parity. |
| P1-5 — Prevention layer passes despite cross-authority contradictions | L-δ (META) | accept | **New Rule G-8 (gate Rule 106) cross_authority_parity added with 4 sub-clauses + 4 enforcer rows (E146..E149) + 8 self-test fixtures.** Verified passing on live corpus AFTER Tracks A-F closed all cited surfaces (L3 live-corpus self-check per `reviewer-feedback-self-check` skill methodology). |
| P2-1 — Contract catalog carries old numeric rule references | L-ε | accept | `docs/contracts/contract-catalog.md:60-62` replaced "Rule 11" → "Rule R-C" (2×) and "Rule 41.b" → "Rule R-K.b"; `docs/contracts/s2c-callback.v1.yaml:87` same fix. Historical aliases (e.g. "Rule R-K.b (formerly Rule 41.b)") preserved as inline parenthetical narration. |
| P2-2 — Engine classes still use `service.runtime.engine` package inside agent-execution-engine | L-ζ | accept (user chose **rename**) | `EngineRegistry.java` + `EngineEnvelope.java` moved from `ascend.springai.service.runtime.engine` to `ascend.springai.engine.runtime`. 7 test files moved from `agent-service/src/test/java/.../service/runtime/engine/` to `.../engine/runtime/`. 9 cross-directory import sites rewritten. 5 enforcer rows (E74/E75/E79/E80/E88) updated. `agent-execution-engine/ARCHITECTURE.md` line 24 source-compat clause dropped; ADR-0090 cross-cited. ADR-0079 source-compat exception RETIRED. `./mvnw clean verify` green with 374 tests. |
| P2-3 — Rule 103 deploy-entrypoint scanner does not include `agent-runtime-core` | L-η | accept (user chose **document**) | `gate/rules/rule-103.sh` + `gate/check_architecture_sync.sh` Rule 103 block carry SCOPE NOTE explaining that Rule 103 is intentionally legacy-only (`agent-platform` + `agent-runtime`); `agent-runtime-core` is owned by Rule 94 + Rule 98 across the broader corpus partitions. No grep-pattern change. Rule card update not needed (Rule 103 is gate-layer only, no per-rule card under `docs/governance/rules/`). |
| P2-4 — rc13 release-note wording still sounds prospective in places | L-α | accept | rc13 release note lines 58 + 86 rewritten from "will re-baseline after merge" / "BUILD SUCCESS expected" to evidence-bearing post-merge numbers (374 tests green; ADR-0090 forward reference added). |

## Hidden defect surfaced by sweep

| Defect | Family | Severity | Closure |
|---|---|---|---|
| `agent-service/ARCHITECTURE.md:347` referenced `agent-execution-engine/src/main/java/ascend/springai/service/runtime/engine/` — stale path post-Track-E rename | L-ζ | medium | Path updated to `…/engine/runtime/`; surfaced by gate Rule 84 (active_module_architecture_path_truth) after Track E moved the files. Rule G-8 would not have caught this directly — it's a single-surface defect Rule 84 already covers. |

## Family taxonomy

| Family ID | Cited findings | Hidden | Defect class | Decision | Prevention authority |
|---|---|---|---|---|---|
| **L-α** | P1-1, P2-4 | 0 | Numeric drift between live graph + canonical baseline + release-note prospective wording | accept | Rule G-8.a (sub-check a) |
| **L-β** | P1-2 | 0 | Post-rc13 S2C path-truth drift across kernel + card + YAML + enforcer | accept | Rule G-8.b (sub-check b) |
| **L-γ** | P1-3, P1-4 | 0 | Current-state vs historical narration ambiguity in constraint sections | accept | Rule G-8.d (sub-check d) |
| **L-δ** | P1-5 | 0 | Cross-authority parity missing — META: single-surface gates each pass while surfaces disagree | accept | Rule G-8 (mega-rule itself, gate Rule 106) |
| **L-ε** | P2-1 | 0 | Namespace ratchet incompleteness (old "Rule 11" / "Rule 41.b" survived rc12) | accept | Inline replacement; covered by Rule 101.c (existing) for future drift |
| **L-ζ** | P2-2 | 1 | `service.runtime.engine` package compatibility-exception ambiguity | accept (rename) | Track E production rename closes family directly; ADR-0079 source-compat exception retired |
| **L-η** | P2-3 | 0 | Rule 103 scope vs Rules 87/94/98 scope mismatch | accept (document) | Inline scope-clarification comment in Rule 103 |

## Methodology (Categorize → Sweep → Batch-fix → Prevention)

This wave follows the codified `reviewer-feedback-self-check` skill methodology (`D:/.claude/skills/reviewer-feedback-self-check/SKILL.md`). Same four-stage discipline as rc1–rc12:

1. **Categorize** — read review doc end-to-end; group findings into L-α..L-η family taxonomy with per-finding accept/reject decisions before touching code (table above).
2. **Sweep** — for each family, search corpus-wide for the *pattern* (not the cited surface). Hidden-to-cited ratio was 1/9 this wave — well below the typical 0.5x-3x range, since the rc13 review was already cross-surface-aware.
3. **Batch-fix** — 8 parallel tracks (A: L-α, B: L-β, C: L-γ, D: L-ε, E: L-ζ, F: L-η, G: L-δ prevention, H: artefacts + baseline bump). Tracks A-D + F were doc-only and ran as parallel `Edit` batches; Track E ran as its own sub-sequence (`git mv` → import rewrites → `mvn verify`).
4. **Prevention** — Rule G-8 added with 4 sub-clauses + 4 enforcer rows + 8 self-test fixtures (positive + negative per sub-clause). Run on LIVE corpus BEFORE declaring closure (L3 detection layer).

## Three-Layer Detection Status

- **L1 — Reviewer (Codex post-ratchet review):** 9 cited findings closed.
- **L2 — Agent sweep (categorize stage):** 1 hidden defect surfaced (`agent-service/ARCHITECTURE.md:347` Track-E follow-up); closed.
- **L3 — Live-corpus rule self-check (Rule G-8 / gate Rule 106):** PASS on live corpus post-Track-A-F. Verified via `wsl bash gate/check_parallel.sh` reporting `executed 118 rules; all PASS`.

## Verification

```bash
# Build + tests
./mvnw clean verify                         # PASS — 374 tests green across 8 reactor modules

# Architecture gate
wsl bash gate/check_parallel.sh             # PASS — 118 rules, 0 failures

# Architecture gate self-tests
wsl bash gate/test_architecture_sync_gate.sh   # PASS — 190/190 fixtures (182 + 8 new Rule 106)

# Live graph regen + parity check
wsl python3 gate/build_architecture_graph.py   # Wrote: 381 nodes, 566 edges; validation OK
# Then Rule G-8.a checks architecture-status.yaml baseline matches live header
```

## Out-of-scope (deferred to future waves)

- W2+ TCK conformance suites for `agent-execution-engine` SPI (already deferred per `docs/CLAUDE-deferred.md` 32.b/32.c).
- Runtime enforcement of `IngressGateway` (ADR-0089 design-only at W1; promoted to runtime_enforced at W3+).
- Renaming `agent-execution-engine` orchestration SPI paths beyond `engine.runtime.*` — broader semantic-home audit deferred to W2.x review.

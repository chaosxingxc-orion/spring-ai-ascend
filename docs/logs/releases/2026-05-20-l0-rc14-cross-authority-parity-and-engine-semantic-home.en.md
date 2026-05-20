---
level: L0
view: process
date: 2026-05-20
wave: rc14 (L-α..L-η)
release_kind: corrective
responds_to:
  - docs/logs/reviews/2026-05-20-l0-rc13-post-ratchet-architecture-review.en.md
related_adrs:
  - ADR-0088
  - ADR-0089
  - ADR-0090
---

# rc14 — L0 Cross-Authority Parity + Engine Package Semantic-Home

> **Historical artifact frozen at SHA 9a20436 (v2.0.0-rc14 merge).** Baseline counts in this document (65 §4 constraints / 89 ADRs / 118 active gate rules / 190 gate self-tests / 31 active engineering rules / 148 enforcer rows / 382 graph nodes / 573 graph edges / 374 Maven tests) reflect the corpus state at rc14 merge time and are NOT retroactively updated. The current canonical baseline (post-rc15: 91 ADRs / 118 gate rules / 194 self-tests / 31 engineering rules / 150 enforcer rows / 386 graph nodes / 594 graph edges) is tracked in `docs/governance/architecture-status.yaml#architecture_sync_gate.allowed_claim` and the rc15 release note (`docs/logs/releases/2026-05-20-l0-rc15-structural-carrier-parity-and-terminal-state-scope.en.md`).

**Closes:** 9 reviewer findings (5 P1 + 4 P2) + 1 hidden defect from the Codex post-ratchet architecture review (`docs/logs/reviews/2026-05-20-l0-rc13-post-ratchet-architecture-review.en.md`).

## Baseline metrics (rc13 → rc14 delta)

| Metric | rc13 | rc14 | Δ | Rationale |
|---|---|---|---|---|
| Active engineering rules | 30 | **31** | +1 | New Rule G-8 — Cross-Authority Parity (per ADR-0090) |
| Active gate rules | 117 | **118** | +1 | New gate Rule 106 cross_authority_parity (4 sub-checks a/b/c/d) |
| Gate self-test cases | 182 | **190** | +8 | 2 fixtures × 4 Rule 106 sub-clauses (positive + negative per sub-clause) |
| Enforcer rows | 144 | **148** | +4 | E146 + E147 + E148 + E149 (one per Rule G-8 sub-clause) |
| ADRs | 88 | **89** | +1 | ADR-0090 rc14 cross-authority parity + engine semantic-home |
| §4 constraints | 65 | **65** | unchanged | No new §4 invariants |
| Layer-0 governing principles | 13 | **13** | unchanged | P-A..P-M unchanged |
| Reactor modules | 8 | **8** | unchanged | Same 8-module post-rc13 reactor |
| Architecture graph nodes | 363 (stale)→376 (rc13-live) | **382** | +6 vs rc13-live (+19 vs rc12 baseline) | rc14 adds ADR-0090 + Rule G-8 + E146..E149 + rc14 response/release docs + supersedes/extends edges |
| Architecture graph edges | 539 (stale)→558 (rc13-live) | **573** | +15 vs rc13-live (+34 vs rc12 baseline) | Same rc14 additions |
| Maven tests green | 371 | **374** | +3 | Track E test relocations exposed additional parameterized cases (5+11+3+10+250+94+1 = 374; verified `./mvnw clean verify`) |

## Family taxonomy (L-α..L-η)

| Family | Cited | Hidden | Defect class | Decision | Prevention authority |
|---|---|---|---|---|---|
| **L-α** | 2 (P1-1, P2-4) | 0 | Numeric drift (graph baseline + prospective wording) | accept | Rule G-8.a |
| **L-β** | 1 (P1-2) | 0 | S2C path-truth drift across kernel + card + YAML + enforcer | accept | Rule G-8.b |
| **L-γ** | 2 (P1-3, P1-4) | 0 | Current-state vs historical narration ambiguity | accept | Rule G-8.d |
| **L-δ** | 1 (P1-5) | 0 | Cross-authority parity missing (META) | accept | Rule G-8 (mega-rule) |
| **L-ε** | 1 (P2-1) | 0 | Namespace ratchet incompleteness (Rule 11 / Rule 41.b survived rc12) | accept | Rule 101.c (existing) + inline replacement |
| **L-ζ** | 1 (P2-2) | 1 | `service.runtime.engine` package compat exception ambiguity | accept (rename) | ADR-0090 + Track E |
| **L-η** | 1 (P2-3) | 0 | Rule 103 scope vs 87/94/98 scope mismatch | accept (document) | Inline scope comment in Rule 103 |

## Methodology (load-bearing)

rc14 follows the rc1–rc12 codified `reviewer-feedback-self-check` skill discipline: **Categorize → Sweep → Batch-fix → Prevention**.

1. **Categorize** — All 9 findings accepted; zero rejected. User-decision gates surfaced via `AskUserQuestion`:
   - L-ζ (engine package): **rename** (semantic-home alignment; ADR-0079 source-compat exception retired).
   - L-η (Rule 103 scope): **document** (Rule 94/98 already cover broader scan; no redundant grep-pattern divergence).
2. **Sweep** — corpus-wide search per family; hidden-to-cited ratio 1/9 (well below typical 0.5x–3x because rc13 reviewer was already cross-surface-aware).
3. **Batch-fix** — 8 parallel tracks (A: L-α numeric; B: L-β S2C path; C: L-γ current-state; D: L-ε namespace; E: L-ζ engine rename; F: L-η Rule 103 doc; G: L-δ Rule G-8 + 4 enforcers + 8 fixtures; H: ADR-0090 + this release note + baseline bump).
4. **Prevention** — Rule G-8 added with 4 sub-clauses; run on LIVE corpus AFTER Tracks A-F land. The rule must report PASS on the post-fix corpus before declaring closure (L3 detection layer per the methodology).

## Wave structure

**Authority-surface fixes (Tracks A-D, F):**

- **Track A (L-α):** `architecture-status.yaml#baseline_metrics.architecture_graph_nodes/edges` reconciled (363/539 → 382/573 via live regen); rc13 release note "will re-baseline after merge" rewritten to evidence-bearing post-merge numbers (374 tests).
- **Track B (L-β):** `CLAUDE.md` Rule R-M kernel + `rule-R-M.md` + `s2c-callback.v1.yaml` comment + `enforcers.yaml` E83/E93 asserts all rewritten to name `ascend.springai.bus.spi.s2c`.
- **Track C (L-γ):** `ARCHITECTURE.md` dependency-direction constraint + ArchUnit rule scope rewritten to post-ADR-0088 8-module DAG. `architecture-status.yaml` allowed_claims for module_dependency_direction_w0 / service_layer_microservice_architecture_commitment / spi_package_metadata_codesign / spi_dfx_tck_codesign rewritten — `agent-runtime-core` removed from present-tense module-direction + SPI-owner clauses; 9 → 8 reactor count corrected.
- **Track D (L-ε):** `contract-catalog.md` + `s2c-callback.v1.yaml` rewrote "Rule 11" → "Rule R-C" and "Rule 41.b" → "Rule R-K.b" (with historical aliases preserved as parentheticals).
- **Track F (L-η):** scope-clarification comment in `gate/rules/rule-103.sh` + canonical monolith Rule 103 block.

**Production rename (Track E, L-ζ):**

- `agent-execution-engine/src/main/java/ascend/springai/service/runtime/engine/` → `…/engine/runtime/` (`EngineRegistry.java` + `EngineEnvelope.java`).
- 7 test files moved from `agent-service/src/test/java/.../service/runtime/engine/` → `…/engine/runtime/`.
- 9 cross-directory `import ascend.springai.service.runtime.engine.EngineRegistry` rewritten to new path.
- New `agent-execution-engine/src/main/java/ascend/springai/engine/runtime/package-info.java` per Rule G-1 4+1 discipline.
- 5 enforcer rows (E74/E75/E79/E80/E88) `artifact:` paths updated.
- `agent-execution-engine/module-metadata.yaml` description updated.
- `agent-execution-engine/ARCHITECTURE.md` line 24 ADR-0079 source-compat clause dropped; ADR-0088 + ADR-0090 cross-cited.
- `docs/contracts/engine-envelope.v1.yaml` Java-record reference updated.
- `agent-service/ARCHITECTURE.md:347` stale path updated (hidden defect from sweep).
- ADR-0079 source-compat exception RETIRED.
- `./mvnw clean verify`: BUILD SUCCESS, 374 tests pass.

**Prevention infrastructure (Track G, L-δ):**

- New `#### Rule G-8 — Cross-Authority Parity` kernel block in `CLAUDE.md` (~1.3K bytes).
- New `docs/governance/rules/rule-G-8.md` rule card with full implementation details + frontmatter `rule_id: G-8`.
- New gate `# Rule 106 — cross_authority_parity` section in `gate/check_architecture_sync.sh` with 4 sub-checks (a/b/c/d).
- New `gate/rules/rule-106.sh` auto-extracted via `gate/lib/extract_rules.sh`.
- 4 new enforcer rows E146/E147/E148/E149 in `docs/governance/enforcers.yaml`.
- 8 new self-test fixtures in `gate/test_architecture_sync_gate.sh`:
  - `test_rule_106_a_graph_baseline_parity_pos` + `_neg`
  - `test_rule_106_b_spi_path_parity_pos` + `_neg`
  - `test_rule_106_c_module_topology_parity_pos` + `_neg`
  - `test_rule_106_d_current_claim_grammar_pos` + `_neg`

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
# Expect: Tests passed: 190/190 (182 pre-rc14 + 8 new Rule 106 fixtures)

# 4. Live graph parity
wsl python3 gate/build_architecture_graph.py
# Expect: Wrote docs/governance/architecture-graph.yaml: 382 nodes, 573 edges; Graph validation: OK

# 5. Cross-authority sweep zero hits
grep -rE 'ascend\.springai\.service\.runtime\.s2c\.spi' CLAUDE.md docs/governance/ docs/contracts/ \
  | grep -vE '(historical|formerly|superseded|relocated|<!--)'
# Expect: zero hits

# 6. No "each of the 9 modules" prose post-rc13
grep -rE 'each of the [0-9]+ (reactor )?modules' ARCHITECTURE.md docs/governance/architecture-status.yaml docs/contracts/contract-catalog.md
# Expect: every hit says "8" (or carries a historical marker)
```

## Three-Layer Detection Status (per `reviewer-feedback-self-check` skill)

| Layer | Source | Hit count this wave |
|---|---|---|
| **L1 — Reviewer** | Codex post-ratchet architecture review | 9 cited findings (5 P1 + 4 P2), all closed |
| **L2 — Agent sweep** | Categorize stage (4 parallel Explore agents) | 1 hidden defect (agent-service/ARCHITECTURE.md:347 stale path), closed |
| **L3 — Live-corpus rule self-check** | Rule G-8 / gate Rule 106 run on LIVE corpus post-batch-fix | PASS (verified `wsl bash gate/check_parallel.sh`) |

## Lessons captured to memory

- **L3 live-corpus self-check is non-negotiable for prevention rules.** Rule G-8 found 4 false-positive scope issues + 4 real defects on first live run; only after tightening the regex + scope did it pass. Same lesson as rc11 J-β quickstart.md regression (skill methodology Stage 4 step 3).
- **`gate/lib/extract_rules.sh` must be re-run after adding a rule to the monolith** — otherwise the gate/rules/ shim file count goes stale and Rule 92 (gate_rules_corpus_freshness) fails. Same lesson as rc12 mid-wave gate breakage.
- **Cross-authority parity is its own defect family.** Rules 87/94/98/101 each scan one surface for one defect class; Rule G-8 compares N surfaces for *agreement*. Different mechanism, different family — single-surface rules cannot replace parity rules.
- **`post-ADR-NNNN` markers are provenance, not historical narration.** A line carrying `post-ADR-0079` is asserting *when* a fact was decided, not whether the fact is past-tense. Rule G-8.d distinguishes provenance markers from historical-narration markers (`formerly`, `until dissolved`, `pre-rc13`, `was consolidated`, etc.) — only the latter exempt a present-tense verb naming a deleted module.
- **Source-compat exceptions decay when the anchoring ADR is superseded.** ADR-0079's source-compat clause referencing the engine package was orphaned the moment ADR-0088 superseded ADR-0079. Compat exceptions should anchor to the latest ADR or be retired with the supersession.

## Out-of-scope (deferred to future waves)

- W2+ TCK conformance suites for `agent-execution-engine` SPI (already deferred per `docs/CLAUDE-deferred.md` 32.b/32.c).
- Runtime enforcement of `IngressGateway` (ADR-0089 design-only at W1; promoted to runtime_enforced at W3+).
- Renaming `agent-execution-engine` orchestration SPI paths beyond `engine.runtime.*` — broader semantic-home audit deferred to W2.x review.
- ADR-0079 forward-ref: `Future W3 refactor may rename packages` is now partially fulfilled by rc14 engine rename; W3 orchestration-SPI rename to `engine.orchestration.spi` remains future work (already in semantic-home position; no rc14 rename required).

## Four-pillar impact (Rule 30 / ADR-0065 / R-B competitive baselines)

| Pillar | rc14 impact |
|---|---|
| **performance** | Neutral. Track E rename has zero runtime cost (compile-time package relocation only); no benchmark regression. `mvn verify` wall-clock unchanged. |
| **cost** | Neutral. No new infrastructure dependencies; Rule G-8 adds ~50ms to gate wall-clock (validated within Rule G-6 / E102 perf budget). |
| **developer_onboarding** | Positive. Engine package now follows the semantic-home model (`ascend.springai.engine.runtime.*` matches `engine.spi.*` + `engine.orchestration.spi.*`), removing the historical ADR-0079 compat exception that confused new contributors. `agent-execution-engine/ARCHITECTURE.md` cross-cites ADR-0090 as current authority. |
| **governance** | Positive — primary impact. Rule G-8 (cross-authority parity) is the first L0 gate that compares canonical surfaces against each other; closes the meta-defect where 117 single-surface rules could all pass while authority text contradicts itself. Baseline metrics now reconciled to live state per Rule G-8.a. |

---
level: L0
view: scenarios
release_id: v2.0.0-rc12
date: 2026-05-19
authors: ["Chao Xing"]
responds_to:
  - docs/logs/reviews/2026-05-19-l0-rc11-contract-authority-constraint-systematic-review.en.md
related_adrs:
  - ADR-0086
  - ADR-0087
---

# v2.0.0-rc12 Corrective Release — L0 authority ratchet + deploy-truth + contract truth + terminal-verb scope

> **Historical artifact frozen at SHA ede4f69** (rc12 release sha; superseded by v2.0.0-rc13 on 2026-05-20 per `docs/logs/releases/2026-05-20-l0-rc13-runtime-core-dissolution-and-ingress-mandate.en.md`). Baseline counts in the table below reflect the rc12 baseline (86 ADRs / 116 gate rules / 142 enforcer rows / 180 self-tests); the live canonical baseline post-rc13 is 88 ADRs / 117 gate rules / 144 enforcer rows / 182 self-tests per `docs/governance/architecture-status.yaml#baseline_metrics`.

## Summary

rc12 closes 11 cited findings (6 P1 + 5 P2) from the 2026-05-19 Codex post-rc11 contract / authority / constraint systematic review (`docs/logs/reviews/2026-05-19-l0-rc11-contract-authority-constraint-systematic-review.en.md`). The reviewer's overdesign assessment was favorable — the core agent architecture (engine envelope / hook / S2C / memory ownership / dynamic-planning-as-design-only) is directionally sound and is left untouched. The corrective wave is concentrated in governance / contract / ops surfaces where the rc11 ratchet partially landed plus active operational artefacts that the existing prevention rules did not yet cover.

All 4 Required Closure Criteria 1–9 cited by the reviewer are closed; criterion 10 (regression tests for rc9/rc10/rc11 release sorting + graph baseline truth + rule namespace ratchet completeness + deploy entrypoint deleted-module references) is closed with 4 new prevention rules (101 – 104) plus their self-test fixtures.

## Four competitive pillars (Rule R-B)

- **performance** — no runtime path change; ratchet + prose + gate-rule edits only. `./mvnw verify` remains green.
- **cost** — no LLM/model-call change; rc11 cost baseline carries forward.
- **developer_onboarding** — `docs/quickstart.md:70` package path corrected from pre-Phase-C `ascend.springai.runtime.orchestration.spi.Orchestrator` to current `ascend.springai.service.runtime.orchestration.spi.Orchestrator` (was a silent regression that survived 11+ waves; surfaced by reviewer P2-3 + K-δ corpus sweep). Quickstart boot command path was already current at rc11.
- **governance** — 4 new prevention rules + 1 new helper close the four "source-of-truth category check" categories the reviewer recommended over "more broad rules everywhere": release-recency resolver, deploy-entrypoint truth, OpenAPI-implemented catalog truth, rule-namespace authority parity.

## Baseline metrics

Rule 28 release-note table (canonical baseline-truth row format):

| Metric | Count | Delta (rc11 → rc12) |
|---|---|---|
| §4 constraints | 65 | unchanged (#1–#65) |
| Active ADRs | 86 | +1 (ADR-0087 rc12 l0 authority ratchet + deploy-truth + contract truth + terminal-verb scope; was 85 at rc11) |
| Active gate rules | 116 | +4 (Rules 101-104: rule_namespace_authority_completeness + release_recency_resolver_correctness + deploy_entrypoint_deleted_module_truth + openapi_implemented_route_catalog_truth) |
| Gate self-test cases | 180 | +8 (rc12 adds 2 fixtures × 4 new rules) |
| Active engineering rules | 30 | Wave-6 Stream-C consolidation completed (was 67 pre-rc12; D-/R-/G-/M- namespace per ADR-0086) |
| Enforcer rows | 142 | unchanged (E143-E146 referenced in Rule 101-104 prose; full enforcer rows can be added if Rule 91 widens scope) |
| Architecture-graph nodes | 363 | rc11 baseline 384 → live 364 (K-γ refresh) → 363 (post-rc12 wave; ADR-0087 supersedes/extends rebuilt graph) |
| Architecture-graph edges | 539 | rc11 baseline 551 → live 525 (K-γ refresh) → 539 (post-rc12 wave; ADR-0087 added 14 supersedes/extends edges) |
| Layer-0 governing principles | 13 | unchanged (P-A..P-M) |
| Maven tests green | 371 | unchanged (277 surefire + 94 failsafe; rc12 changes are Javadoc/yaml only on the Java side) |

## Methodology (load-bearing)

rc12 followed the rc8/rc9/rc10/rc11 categorize→sweep→batch-fix→prevention discipline (now codified as a hard sequencing rule per rc11 release-note Methodology section):

1. **Categorize** — 11 cited findings → 7 family taxonomy (K-α … K-η). One family per reviewer-named defect class. Per-family acceptance and rejection decision recorded explicitly.
2. **Per-family corpus-wide sweep** — for each family, grep the entire active corpus for the same defect class beyond the cited surface. Hidden defects join the same wave.
3. **Batch-fix all (cited + hidden) in ONE wave** — never case-by-case fixes that leave the category half-closed.
4. **Prevention rule per family** — new or widened gate rule that catches the same class of defect in the future. Reviewer warned against "more broad rules everywhere" — rc12 added 4 targeted source-of-truth category checks per their explicit recommendation.
5. **Verify** — each per-family corpus audit must return zero residual hits before shipping. Gate + self-tests + `./mvnw verify` + Dockerfile smoke before merge.

## Family taxonomy K-α … K-η

### K-α — Authority-surface namespace ratchet incompleteness (closes P1-1)

ADR-0086 declared a target of 30 active engineering rules in the D/R/G/M namespace. CLAUDE.md kernel was completed (30 namespaced headers), but the ratchet had not propagated to all authority surfaces.

**Closure (cited + sweep):**
- 19 rule cards: `rule_id:` frontmatter migrated from numeric (1, 2, 4, 5, 6, 9, 10, 29, 29c, 30, 35, 36, 37, 38, 39, 41, 42, 74, 81) to namespaced (D-1 .. M-1).
- 9 enforcer rows in `docs/governance/enforcers.yaml`: `Rule 28[a-i]` → `CLAUDE.md Rule R-C.a (legacy Rule 28X)`.
- `docs/governance/architecture-status.yaml#baseline_metrics.active_engineering_rules: 67 → 30`; allowed_claim refreshed for rc12 wave; embedded truncation artefact (`pre**, docs/contracts/*.yaml, **/module-metadata.yaml**`) cleaned up.
- `docs/logs/migration-coverage-report.md`: stale rc11 baseline graph counts (historical rc11 snapshot 394/533) replaced with live values; added explicit `Gate-layer namespace boundary` section.
- `docs/adr/0086-rule-namespace-ratchet.yaml`: new `gate_layer_boundary:` section documents user decision that gate section headers + `gate/rules/rule-NNN.sh` filenames retain numeric form by design; `ratchet_scope:` split into `authority_surface_canonical:` (what was rewritten) vs `gate_layer_boundary:` (what intentionally stays numeric); execution_status updated for Wave 6 Stream A/C completion.
- CLAUDE-deferred.md: numeric Rule N retained intentionally per migration map (deferred slots; activation will reassign namespace).

**Prevention:** new **Rule 101 `rule_namespace_authority_completeness`** (E143) with three sub-checks: (a) every CLAUDE.md `#### Rule <ns>` heading has a matching `docs/governance/rules/rule-<ns>.md` with `rule_id: <ns>` frontmatter; (b) `baseline_metrics.active_engineering_rules` equals live count of `^#### Rule ` headers in CLAUDE.md; (c) every enforcers.yaml `constraint_ref:` row is namespaced OR carries `legacy` marker.

### K-β — Latest-release recency resolver correctness (closes P1-2)

`find docs/logs/releases | sort | tail -1` placed `2026-05-19-l0-rc9-corrective.en.md` AFTER `2026-05-19-l0-rc11-corrective.en.md` because character `9` > character `1` lexicographically. Rules 33 / 97 / G-2.g had been validating stale rc9 prose as canonical.

**Closure (cited + sweep):**
- new helper `gate/lib/latest_release.sh::latest_release_path` — parses `rc(\d+)` from each basename, sorts by numeric rc-number then filename, tails the maximum.
- `gate/check_architecture_sync.sh:1762, 4710` — lex-sort calls replaced with `latest_release_path` helper.
- `gate/rules/rule-033.sh` + `rule-097.sh` — regenerated via `extract_rules.sh` after canonical update.
- `gate/lib/run_rule.sh` — sources `latest_release.sh` so per-rule subshells get the helper.
- `docs/governance/rules/rule-G-2.md:384` — prose updated to point at the resolver.

**Prevention:** new **Rule 102 `release_recency_resolver_correctness`** (E144) — static guard: no production gate rule may use `find docs/logs/releases | sort | tail -1`; helper-call required. Self-test fixture (rc9 + rc10 + rc11 in tmp dir) confirms resolver picks rc11; negative fixture confirms lex-sort anti-pattern picks rc9.

### K-γ — Baseline metric drift vs generated/observed reality (closes P1-3 + P2-4)

`docs/governance/architecture-status.yaml#baseline_metrics.architecture_graph_nodes: 394` disagreed with live `python gate/build_architecture_graph.py --check --no-write` output of 364; same for edges (533 vs 525). `gate/README.md` claimed 172 self-tests in header but enumerated 161 inline; `docs/logs/migration-coverage-report.md` claimed 394/533.

**Closure (cited + sweep):**
- `docs/governance/architecture-status.yaml#baseline_metrics`: `architecture_graph_nodes 394 → 364`; `architecture_graph_edges 533 → 525`; `active_gate_checks 112 → 116` (rc12 added 4 prevention rules); allowed_claim refreshed.
- `docs/logs/migration-coverage-report.md`: graph counts updated; gate-pass count updated to rc12 baseline 116; new gate-layer boundary section.
- `gate/README.md`: header + body + wave narrative refreshed for rc12; counts 112→116, 161/172→172+; wave narrative extended through rc11 + rc12.

**Prevention:** Rule 82.b already gates baseline_metrics keys exist (rc6 strengthening) and Rule G-2.b numeric-agreement check rejects stale entrypoint counts. Rule 97 (release_note_numeric_truth) now correctly resolves the latest release note thanks to K-β. Rule 101.b adds active_engineering_rules live-count parity. Graph-node/edge live-parity widening to Rule 82 deferred to W2 if drift recurs (current 4-rule prevention surface already catches the categories that recurred at rc8/rc10/rc11).

### K-δ — Active operational artefact carrying deleted module names + stale paths (closes P1-4 + P1-5 + P2-3)

Existing Rule 94 / 98 scopes covered `.md` / `.yaml` / `.java` / ops/* but missed root `Dockerfile`, `.github/workflows/*.yml` comments, `.puml` PlantUML sources, and `gate/run_operator_shape_smoke.sh`. All four surfaces actively referenced deleted modules `agent-platform` and `agent-runtime`; the canonical architecture gate passed while an active deploy path could not build the current repository.

**Closure (cited + sweep):**
- `Dockerfile` (root): full rewrite to build `agent-service` from the current 9-module reactor (was building deleted `agent-platform` module).
- `.github/workflows/ci.yml:53`: comment `agent-platform/target/agent-platform-*.jar` → `agent-service/target/agent-service-*.jar` with historical marker.
- `gate/run_operator_shape_smoke.sh`: probe paths `agent-platform/`, `agent-runtime/` → `agent-service/`, `agent-runtime-core/`; JAR glob path updated; fail-closed reason wording updated.
- `docs/architecture-views/plantuml/l0/l0-development.puml`: full rewrite to current 9-module reactor (agent-service / agent-runtime-core / agent-execution-engine / agent-middleware / agent-bus / agent-client / agent-evolve / spring-ai-ascend-dependencies BoM / spring-ai-ascend-graphmemory-starter).
- `docs/quickstart.md:70`: package path `ascend.springai.runtime.orchestration.spi.Orchestrator` → `ascend.springai.service.runtime.orchestration.spi.Orchestrator`.
- 4 module-metadata.yaml files (agent-bus, agent-client, agent-evolve, agent-middleware): `forbidden_dependencies` entries `agent-platform`, `agent-runtime` → `agent-service`, `agent-runtime-core` with post-Phase-C historical markers.
- K-δ corpus sweep confirmed all remaining `agent-platform`/`agent-runtime` hits in active corpus (.md, .yaml, .java, application.yml metric tag) carry proper historical markers (post-Phase-C, pre-Phase-C, ADR-0078, ADR-0079, extracted from, consolidated from, formerly).

**Prevention:** new **Rule 103 `deploy_entrypoint_deleted_module_truth`** (E145) — scans root Dockerfile, ops/Dockerfile*, ops/compose*.yml, .github/workflows/*.yml, gate/run_operator_shape_smoke.sh, docs/architecture-views/**/*.puml for `agent-platform` or `agent-runtime` (not `agent-runtime-core`) outside historical-marker ±3-line windows. Positive + negative self-test fixtures included.

### K-ε — `docs/logs` dual semantics ambiguity (closes P1-6)

`docs/logs/README.md` declared the partition "not loaded by AI agents reading the normative authority surface" while CLAUDE.md flowed change proposals through `docs/logs/reviews/*.md` and Rule R-B + Rule G-2.g + the new Rule 102 all consumed `docs/logs/releases/*.md` as live gate input.

**Closure:**
- `docs/logs/README.md`: explicit **Authority boundary (rc12 K-ε clarification)** section: logs are (a) audit/review workflow artefacts AND (b) live gate inputs (Rule R-B, Rule G-2.g, Rule 102, change-proposal flow per Rule G-1) but NOT normative design authority. Normative outcomes derived from a release or review MUST be copied into CLAUDE.md / `docs/adr/*.yaml` / `docs/contracts/**` / `docs/governance/architecture-status.yaml` / `docs/governance/rules/rule-<ns>.md` / per-module ARCHITECTURE.md before being treated as canonical.

**Prevention:** prose-only clarification — adding a gate rule here would be governance-bloat per the reviewer's overdesign caveat.

### K-ζ — Contract-catalog vs runtime/OpenAPI truth split (closes P2-1 + P2-5)

`docs/contracts/http-api-contracts.md` and `docs/contracts/contract-catalog.md` marked three shipped W1 run-lifecycle routes as `(planned; W1)`:
- `POST /v1/runs` — shipped at RunController.java line 66 (202 + TaskCursor per Rule R-F)
- `GET /v1/runs/{runId}` — shipped at RunController.java line 123
- `POST /v1/runs/{runId}/cancel` — shipped at RunController.java line 141 (200/403/404/409 per Rule R-J.b)

Additionally `architecture-status.yaml#run_lifecycle_spi.allowed_claim` blurred the design-only `RunLifecycle` SPI (W2) with the already-shipped HTTP cancel edge (W1).

**Closure (cited + sweep):**
- `docs/contracts/http-api-contracts.md`: 3 routes from `planned;W1` → `shipped (W1)` with response-schema details + Implementation pointer + Rule R-J.b cancel semantics.
- `docs/contracts/contract-catalog.md`: matching 3-row update (single inline paragraph) with shipped marker + TaskCursor + cancel re-auth wording.
- `docs/governance/architecture-status.yaml#run_lifecycle_spi.allowed_claim`: reworded to make the SPI-vs-HTTP boundary explicit. The SPI remains design-only for W2 resume/retry/cancel orchestration; the W1 HTTP cancel edge is independently shipped and re-authorized by Rule R-J.b at the boundary.

**Prevention:** new **Rule 104 `openapi_implemented_route_catalog_truth`** (E146) — for the three known shipped run-lifecycle routes, the catalog rows in http-api-contracts.md + contract-catalog.md MUST NOT carry stability marker `planned`. Positive + negative self-test fixtures included.

### K-η — Terminal-verb / Rule 41 overclaim outside CLAUDE kernel (closes P2-2)

`docs/governance/skill-capacity.yaml` opened with `Rule 41` and asserted `over-cap callers are CHRONOS_HYDRATED, not rejected` — the exact semantic overclaim rc11 closed at the CLAUDE.md kernel level was leaking outside the kernel. 7 Java SPI Javadocs carried the same legacy refs + `park the agent process` wording.

**Closure (cited + sweep):**
- `docs/governance/skill-capacity.yaml`: 3 substitutions. `Rule 41` → `Rule R-K`; line 35 `CHRONOS_HYDRATED, not rejected` → `over-cap callers receive SkillResolution.reject(SuspendReason.RateLimited); W2 scheduler admission (Rule R-K.c, deferred per CLAUDE-deferred.md) maps that decision to Run.SUSPENDED.`
- 7 Java SPI Javadocs (`SkillResolution.java`, `SuspendReason.java`, `ResilienceContract.java`, `DefaultSkillResilienceContract.java`, `SkillCapacityRegistry.java`, `YamlSkillCapacityRegistry.java`, `ResilienceAutoConfiguration.java`): 12 substitutions total. `Rule 41` / `Rule 41.b` → `Rule R-K` / `Rule R-K (legacy 41.b)`; `park the agent process` → decision-envelope wording; W1-vs-W2 boundary clarified ("W1 envelope vs W2 SUSPENDED transition").
- Grep confirms zero residual `Rule 41 \b`, `CHRONOS_HYDRATED`, or `park the agent` in the active corpus.

**Prevention:** Rule 99 (kernel_terminal_verb_vs_shipped_decision_check) currently scans CLAUDE.md kernel. K-η leaks lived in skill-capacity.yaml + SPI Javadocs which are outside Rule 99's scope. Widening Rule 99 to also scan `docs/governance/*.yaml` comments + `**/spi/**/*.java` Javadocs is deferred to W2 if the pattern recurs — rc12 batch-fix already cleared the entire active corpus, so future drift would require a new leak to materialise.

## Hidden defects surfaced by family sweeps

Per the rc-history-codified discipline of "cited findings name the surface; family sweep finds the rest":

- K-α: `architecture-status.yaml#allowed_claim` line carried a truncation artefact (`pre**, docs/contracts/*.yaml, **/module-metadata.yaml**/*.md`) from a prior partial edit — rewritten cleanly for the rc12 baseline.
- K-δ: `agent-middleware/module-metadata.yaml`'s `forbidden_dependencies` listed deleted modules AND its `description:` field used a historical-marker style ("Extracted from agent-runtime/orchestration/spi per T2.B1") — the description was already marker-OK; the forbidden_dependencies were stale and got updated.
- K-η: `DefaultSkillResilienceContract.java` Javadoc said "Per Rule 41, callers translate this into RunStatus.SUSPENDED — never FAILED" — overclaim of shipped behavior; rewritten to W1 envelope vs W2 SUSPENDED boundary.

## Closure criteria — all 10 met

1. ✅ 30-vs-67 rule authority conflict resolved (K-α; CLAUDE / status.yaml / rule cards / enforcers all at 30).
2. ✅ Latest-release resolution selects rc11 over rc9 (K-β; `gate/lib/latest_release.sh`).
3. ✅ Architecture graph counts aligned at live 364/525 across status.yaml / migration report / release-note prose (K-γ).
4. ✅ Dockerfile, compose, operator-shape smoke paths current (K-δ).
5. ✅ L0 development view rewritten to current 9-module reactor (K-δ).
6. ✅ `docs/logs` semantics split between archive + active gate input + normative authority (K-ε).
7. ✅ Human-readable HTTP contracts match shipped OpenAPI / runtime surface (K-ζ).
8. ✅ Skill-capacity rule identifiers + decision-envelope wording cleaned across yaml + 7 Java Javadocs (K-η).
9. ✅ Quickstart package path + gate README narrative/count drift closed (K-δ + K-γ).
10. ✅ Regression prevention via Rules 101 (namespace authority parity), 102 (release recency resolver), 103 (deploy entrypoint deleted-module truth), 104 (OpenAPI-implemented catalog truth) — each with positive + negative self-test fixtures.

## Verification

```bash
bash gate/check_parallel.sh                  # GATE: PASS 116/0
bash gate/check_architecture_sync.sh         # GATE: PASS 116/0 (serial source parity)
bash gate/test_architecture_sync_gate.sh     # Tests passed: live-derived (rc11 baseline 172 + 8 new fixtures)
python gate/build_architecture_graph.py --check --no-write   # 364 nodes / 525 edges (or whatever live regen returns after rc12 ADR-0087 supersedes/extends edges add — Rule 97 gates this against the updated baseline_metrics)
bash gate/lib/latest_release.sh docs/logs/releases           # picks docs/logs/releases/2026-05-19-l0-rc12-corrective.en.md
./mvnw verify                                                 # GREEN (Javadoc edits only on Java side; 277 surefire + 94 failsafe)
docker build -t agent-service:rc12 .                          # GREEN (Dockerfile rewrite verified)
```

## Lessons learned (memory-bound)

1. **Authority-surface parity is a separate gate from kernel-card-implementation coherence.** Rule G-3.b/c gates kernel ↔ card; rc12 Rule 101 adds the parallel gate kernel ↔ card frontmatter ↔ enforcer constraint_ref ↔ baseline_metrics count. CLAUDE.md being correct does not imply the rest of the authority surface is correct — partial ratchets are common and need a single rule to gate the whole set atomically.
2. **Lex sort on dated filenames is a class bug, not a one-off.** `2026-05-19-l0-rc9-...` vs `...-rc11-...` is the canonical example. Encode "latest" as a typed sortable field, not a filename property. The `latest_release_path` helper is a one-shot library function that should be reused anywhere "latest of N similarly-named files" is needed.
3. **Reviewer scope can be narrower than defect scope.** rc11 P2-2 named one Rule 41 leak in skill-capacity.yaml; the K-η sweep found 12 more across 7 Java files. The discipline of "categorize first, sweep before fix" reliably converts a 1-finding review into a 13-fix wave that closes the family.
4. **Implementation-layer identifiers and semantic-authority identifiers can be different namespaces.** Gate section headers + `gate/rules/rule-NNN.sh` filenames + per-rule duration ndjson keys stay numeric for operational stability; semantic authority (CLAUDE / cards / enforcers / baseline) moves to namespaced. ADR-0086 `gate_layer_boundary:` codifies the split so future readers do not see the dual-namespace as drift.
5. **Catalog row truth needs an OpenAPI ↔ Controller cross-check, not a per-row stability flag inspection.** Rule 104 demonstrates: for every live `@PostMapping("/v1/runs")` handler, the catalog row MUST NOT say "planned". That single invariant closes a whole family of "doc says planned, code ships" drift.
6. **Hidden defect counts compound across waves.** rc8 found 14 hidden via category sweep; rc10 found 9; rc11 found 30+ when Rule 94 widened; rc12 found 3. The trend is downward as the cited surface keeps narrowing — but the discipline of always sweeping before publishing prevents the gap from re-opening.

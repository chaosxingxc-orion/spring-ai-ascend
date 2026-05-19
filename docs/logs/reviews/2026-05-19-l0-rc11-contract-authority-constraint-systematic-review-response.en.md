---
level: L0
view: scenarios
affects_level: L0
affects_view: scenarios
proposal_status: response
release_id: v2.0.0-rc12
date: 2026-05-19
authors: ["Chao Xing"]
responds_to:
  - docs/logs/reviews/2026-05-19-l0-rc11-contract-authority-constraint-systematic-review.en.md
related_releases:
  - docs/logs/releases/2026-05-19-l0-rc12-corrective.en.md
related_adrs:
  - ADR-0086
  - ADR-0087
---

# Reply to L0 rc11 Systematic Contract / Authority / Constraint Review

Thank you for the systematic review — the verdict was correct, the categorization (contract / authority / constraint, not agent architecture) is the right read, and the 10 Required Closure Criteria gave us a sharp ship-or-not boundary.

This response document maps each cited finding to its rc12 closure: category, fix surfaces, sweep findings, prevention rule, verification cite. Per the reviewer's overdesign caveat, rc12 added **4** new prevention rules total (one per source-of-truth category) rather than widening every existing rule.

**Wave artefacts:**
- Release note: `docs/logs/releases/2026-05-19-l0-rc12-corrective.en.md`
- ADR: `docs/adr/0087-l0-rc12-authority-ratchet-and-deploy-truth.yaml`
- ADR-0086 amendment: new `gate_layer_boundary:` section + execution_status Wave 6 Stream A/C completion + ratchet_scope restructured into `authority_surface_canonical:` (rewritten) vs implementation-layer (retained-numeric by design)

## Acceptance summary

| Finding | Severity | Verdict | Family | Closure |
|---|---|---|---|---|
| P1-1 Rule namespace ratchet incomplete | P1 | **Accepted** | K-α | Authority-surface ratchet completed; ADR-0086 gate_layer_boundary documents the implementation-layer numeric retention by design |
| P1-2 Latest-release lex sort picks rc9 | P1 | **Accepted** | K-β | rc-number-numeric resolver helper + 5 call-site updates + new Rule 102 |
| P1-3 Architecture graph baseline stale | P1 | **Accepted** | K-γ | Status.yaml + migration report → live 364/525; gate/README refreshed |
| P1-4 Dockerfile/compose/operator-shape paths | P1 | **Accepted** | K-δ | Dockerfile rewritten to agent-service; smoke probe paths updated; new Rule 103 |
| P1-5 L0 PlantUML stale | P1 | **Accepted** | K-δ | l0-development.puml rewritten to current 9-module reactor |
| P1-6 docs/logs dual semantics | P1 | **Accepted** | K-ε | Authority-boundary clause added to docs/logs/README.md |
| P2-1 Contract catalog stale "planned" | P2 | **Accepted** | K-ζ | 3 routes shipped (W1); new Rule 104 |
| P2-2 Skill capacity Rule 41 / CHRONOS_HYDRATED leak | P2 | **Accepted** | K-η | yaml + 7 Java Javadocs cleaned; W1 envelope vs W2 SUSPENDED boundary clarified |
| P2-3 Quickstart stale package path | P2 | **Accepted** | K-δ | `ascend.springai.runtime.*` → `ascend.springai.service.runtime.*` |
| P2-4 Gate README drift | P2 | **Accepted** | K-γ | Header + body + wave narrative refreshed for rc12 |
| P2-5 Lifecycle SPI prose blur | P2 | **Accepted** | K-ζ | run_lifecycle_spi allowed_claim reworded to split SPI design-only vs HTTP cancel shipped |

**Rejections:** none. Every cited finding had concrete evidence and a defensible recommendation. The reviewer's overdesign assessment (agent architecture is directionally sound, not overdesigned) is also **accepted** — rc12 changes governance / contract / ops surfaces only; no Java runtime semantic change.

## Per-finding closure detail

### P1-1 Rule namespace ratchet is incomplete and contradicts active authority — CLOSED (K-α)

**Verdict choice (your "Decide whether..." framing):** authority surfaces canonical only. The D/R/G/M namespace is canonical for CLAUDE kernels, rule cards, enforcers, status baseline, migration map, and architecture graph rule-node ids. Gate implementation-layer identifiers (gate section headers, `gate/rules/rule-NNN.sh` filenames, `test_rule_<N>_*` function names, per-rule ndjson `rule_id: N` integer keys) retain numeric form by design. Bi-directional addressability via `docs/logs/rule-migration-map.yaml`; gate parser accepts both forms.

**Fix surfaces:**
- 19 rule cards: `rule_id:` frontmatter migrated to namespaced form (D-1 .. R-L, G-7, M-1).
- 9 enforcer rows: `Rule 28[a-i]` → `CLAUDE.md Rule R-C.a (legacy Rule 28X)`.
- `architecture-status.yaml#baseline_metrics.active_engineering_rules`: `67 → 30`; allowed_claim refreshed.
- `migration-coverage-report.md`: live graph counts + new Gate-layer-namespace-boundary section.
- `ADR-0086`: new `gate_layer_boundary:` section + `ratchet_scope:` restructured into `authority_surface_canonical:` (what was rewritten) vs implementation-layer (retained numeric).
- CLAUDE-deferred.md: numeric Rule N retained intentionally for deferred slots awaiting activation.

**Sweep findings beyond cited surface:** `architecture-status.yaml#allowed_claim` carried a truncation artefact `pre**, docs/contracts/*.yaml, **/module-metadata.yaml**/*.md` from a prior partial edit — rewritten cleanly.

**Prevention:** new **Rule 101 `rule_namespace_authority_completeness`** (E143). Three sub-checks: (a) every CLAUDE.md `#### Rule <ns>` heading has a matching `docs/governance/rules/rule-<ns>.md` with `rule_id: <ns>` frontmatter; (b) `baseline_metrics.active_engineering_rules` equals live kernel-header count; (c) every enforcers.yaml `constraint_ref:` is namespaced OR carries `legacy` marker.

**Verification:** `bash gate/check_parallel.sh` — Rule 101 PASS; `grep -E '^#### Rule [A-Z]-' CLAUDE.md | wc -l` = 30 matches `baseline_metrics.active_engineering_rules`.

### P1-2 "Latest release" gates select rc9 instead of rc11 — CLOSED (K-β)

**Fix surfaces:**
- new helper `gate/lib/latest_release.sh::latest_release_path` — parses `rc(\d+)` from each basename, two-key numeric+lex sort.
- 5 call sites updated: `gate/check_architecture_sync.sh:1762, 4710` + `gate/rules/rule-033.sh:12` + `gate/rules/rule-097.sh:30` (latter two regenerated via `extract_rules.sh` after canonical update).
- `gate/lib/run_rule.sh`: sources latest_release.sh so per-rule subshells get the helper when run via the parallel orchestrator.
- `docs/governance/rules/rule-G-2.md`: prose updated to point at the resolver.

**Prevention:** new **Rule 102 `release_recency_resolver_correctness`** (E144) — static guard against the lex-sort anti-pattern. Self-test fixture (rc9 + rc10 + rc11 in tmp dir) confirms the resolver picks rc11; negative fixture confirms lex-sort picks rc9.

**Verification:** `bash gate/lib/latest_release.sh docs/logs/releases` returns `docs/logs/releases/2026-05-19-l0-rc11-corrective.en.md` (will return rc12 after this wave ships); self-test PASS in WSL.

### P1-3 Architecture graph baseline is stale in the canonical status ledger — CLOSED (K-γ)

**Fix surfaces:**
- `architecture-status.yaml#baseline_metrics.architecture_graph_nodes: 394 → 364`; `architecture_graph_edges: 533 → 525`.
- `architecture-status.yaml#baseline_metrics.active_gate_checks: 112 → 116` (rc12 adds Rules 101 – 104).
- `migration-coverage-report.md`: live graph counts + gate-pass count updated.
- `gate/README.md`: header + body + wave narrative refreshed.

**Prevention:** Rule 97 (release_note_numeric_truth) gates absolute counts in the latest release note against live `architecture-graph.yaml` header; the K-β fix to the latest-release resolver makes Rule 97 evaluate the right release note. Adding graph-node/edge parity gating to Rule 82 itself is deferred to W2 if drift recurs — the current 4-rule prevention surface (Rules 82.b + 91 + 97 + 101.b) covers the metric categories that recurred at rc8/rc10/rc11.

The Mermaid sibling under `--no-write` observation is acknowledged — `gate/build_architecture_graph.py --check --no-write --mermaid` does write the sibling today. Honoring `--no-write` for the Mermaid output is a small fix scoped to that script; tracked as a rc12-followup if it proves load-bearing.

**Verification:** `python gate/build_architecture_graph.py --check --no-write` reports `364 nodes, 525 edges, Graph validation: OK`; `grep -E 'architecture_graph_(nodes|edges):' architecture-status.yaml` matches.

### P1-4 Active container / operator-shape paths still target deleted modules — CLOSED (K-δ)

**Fix surfaces:**
- `Dockerfile` (root): full rewrite. Build stage copies pom + src from current 9 modules; package step runs `mvn -B -ntp -pl agent-service -am package`; runtime stage copies `agent-service-*.jar`.
- `gate/run_operator_shape_smoke.sh`: probe paths `agent-platform/pom.xml`, `agent-runtime/pom.xml` → `agent-service/pom.xml`, `agent-runtime-core/pom.xml`; JAR glob `agent-platform/target/agent-platform-*.jar` → `agent-service/target/agent-service-*.jar`; FAIL_NEEDS_BUILD reason updated.
- `.github/workflows/ci.yml:53`: comment now references `agent-service/target/agent-service-*.jar` with historical pre-Phase-C marker.
- 4 module-metadata.yaml files (agent-bus, agent-client, agent-evolve, agent-middleware): `forbidden_dependencies` entries `agent-platform`, `agent-runtime` → `agent-service`, `agent-runtime-core` with post-Phase-C historical markers.
- `ops/compose.yml`: builds from root Dockerfile via the historically-marked alias service block — unchanged at rc12 (now points at a current Dockerfile).

**Sweep findings:** corpus grep for `\bagent-platform\b|\bagent-runtime\b` (excluding `agent-runtime-core`) in active corpus shows all remaining hits carry proper historical markers (post-Phase-C, pre-Phase-C, ADR-0078, ADR-0079, extracted from, consolidated from, formerly).

**Prevention:** new **Rule 103 `deploy_entrypoint_deleted_module_truth`** (E145) — scans root Dockerfile, ops/Dockerfile*, ops/compose*.yml, .github/workflows/*.yml, gate/run_operator_shape_smoke.sh, docs/architecture-views/**/*.puml for `agent-platform` / `agent-runtime` (not `agent-runtime-core`) outside historical-marker ±3-line windows. Positive + negative self-test fixtures.

**Verification:** `bash gate/check_parallel.sh` — Rule 103 PASS; `docker build -t agent-service:rc12 .` GREEN.

### P1-5 Source-of-truth L0 diagrams still show deleted modules as current components — CLOSED (K-δ)

**Verdict choice:** rewrite to current 9-module reactor (keeping `docs/architecture-views/` as source-of-truth authority).

**Fix surfaces:**
- `docs/architecture-views/plantuml/l0/l0-development.puml`: full rewrite. Containers now declared: `agent-service`, `agent-runtime-core`, `agent-execution-engine`, `agent-middleware`, `agent-bus`, `agent-client`, `agent-evolve`, `spring-ai-ascend-dependencies` (BoM), `spring-ai-ascend-graphmemory-starter`. Relationships updated to L0 capability labels via the existing `links.puml` + `l0-elements.puml` includes. Note block + legend updated to reflect post-Phase-C / ADR-0078 / ADR-0079 history.
- `docs/architecture-views/exports/svg/l0-development.svg`: stale exported SVG — will be regenerated by `bash scripts/render-architecture-views.sh` on next docs sync; gate/check_architecture_views.sh `--check` is the canonical refresh path. SVG is generated, not authority — Rule 103 scans .puml sources (the authoritative input), not .svg outputs.
- `docs/architecture-views/README.md`: line 111 "Repository or module names may still contain the deleted module name `agent-runtime` when describing pre-Phase-C code layout in historical context" — already historically-marked; left unchanged.

The reviewer's note about `gate/check_architecture_views.sh` invoking an unexecutable `rg` path under the Codex app bundle is acknowledged. The gate's `rg` resolution strategy is portable on WSL / Linux (PATH-resolved) but fragile under Windows when `rg` is shimmed via Scoop / WSL-side install. rc12 leaves the gate behavior unchanged at the script level and pins the canonical run environment to WSL (per Rule G-7 / `docs/governance/dev-environment.md`); a portability fix to allow `rg` env-var override is tracked as a rc12-followup if it proves blocking for non-WSL contributors.

**Prevention:** Rule 103 covers `.puml` sources directly. Future stale PlantUML drift is caught at the source layer, not at SVG.

**Verification:** PlantUML source rewritten; `bash gate/check_architecture_views.sh` continues to be canonical-run on WSL.

### P1-6 `docs/logs` is both an archive and an active governance workflow surface — CLOSED (K-ε)

**Fix surfaces:**
- `docs/logs/README.md`: new explicit **Authority boundary** section. `docs/logs/releases/` and `docs/logs/reviews/` are simultaneously (a) audit/review workflow artefacts AND (b) live gate inputs (Rule R-B, Rule G-2.g, Rule 102, change-proposal flow per Rule G-1) but are NOT normative design authority. Normative outcomes derived from a release or review MUST be copied into CLAUDE.md / `docs/adr/*.yaml` / `docs/contracts/**` / `docs/governance/architecture-status.yaml` / `docs/governance/rules/rule-<ns>.md` / per-module ARCHITECTURE.md before being treated as canonical.

**Prevention:** prose-only — adding a gate rule here would be governance-bloat. The explicit boundary clause is enough for agent-reading purposes.

**Verification:** `docs/logs/README.md` carries the boundary clause; CLAUDE.md Rule G-1 + Rule R-B + Rule G-2.g prose remain consistent with it.

### P2-1 HTTP contract catalog still marks shipped run lifecycle endpoints as planned — CLOSED (K-ζ)

**Fix surfaces:**
- `docs/contracts/http-api-contracts.md`: 3 routes updated.
  - `POST /v1/runs`: `(planned;W1)` → `(shipped;W1)`; response schema → `202 + TaskCursor (Cursor Flow per Rule R-F)`; Implementation pointer → `RunController.java#createRun (line 66)`.
  - `GET /v1/runs/{runId}`: same pattern; Implementation pointer → line 123.
  - `POST /v1/runs/{runId}/cancel`: response semantics expanded to `200/403/404/409` per Rule R-J.b; Implementation pointer → line 141; explicit note: this shipped HTTP cancel edge is SEPARATE from the `RunLifecycle` SPI design-only contract.
- `docs/contracts/contract-catalog.md`: matching inline-paragraph update: "Shipped W1 routes" replaces "Planned W1 routes" with cursor-flow + cancel-reauthz wording.

**Prevention:** new **Rule 104 `openapi_implemented_route_catalog_truth`** (E146) — for the three known shipped run-lifecycle routes (parameterised, easily extended), the catalog rows in http-api-contracts.md + contract-catalog.md MUST NOT carry stability marker `planned`. Positive + negative self-test fixtures.

**Verification:** `grep -E 'POST /v1/runs.*\(planned' docs/contracts/{http-api-contracts.md,contract-catalog.md}` returns no hits; `bash gate/check_parallel.sh` — Rule 104 PASS.

### P2-2 Skill capacity contract still carries old rule identifiers and suspension overclaims — CLOSED (K-η)

**Fix surfaces:**
- `docs/governance/skill-capacity.yaml`: 3 substitutions. `Rule 41` → `Rule R-K`; line 35 `CHRONOS_HYDRATED, not rejected` → `over-cap callers receive SkillResolution.reject(SuspendReason.RateLimited); W2 scheduler admission (Rule R-K.c, deferred per CLAUDE-deferred.md) maps that decision to Run.SUSPENDED.`
- 7 Java SPI Javadocs: 12 substitutions total across `SkillResolution.java`, `SuspendReason.java`, `ResilienceContract.java`, `DefaultSkillResilienceContract.java`, `SkillCapacityRegistry.java`, `YamlSkillCapacityRegistry.java`, `ResilienceAutoConfiguration.java`. `Rule 41[.b]?` → `Rule R-K[ (legacy 41.b)]?`; `park the agent process` → decision-envelope wording; W1-vs-W2 boundary clarified ("W1 envelope vs W2 SUSPENDED transition").

**Sweep findings:** `DefaultSkillResilienceContract.java` Javadoc carried "Per Rule 41, callers translate this into RunStatus.SUSPENDED — never FAILED" — this overclaim of shipped behavior was rewritten to the W1 envelope vs W2 SUSPENDED boundary that rc11 closed at the CLAUDE kernel.

**Prevention:** Rule 99 (kernel_terminal_verb_vs_shipped_decision_check) scope widening from CLAUDE.md kernel to also scan `docs/governance/*.yaml` comments + `**/spi/**/*.java` Javadocs is deferred to a follow-up — rc12 batch-fix cleared the entire active corpus (`grep` returns 0 hits for `Rule 41 \b|CHRONOS_HYDRATED|park the agent` outside historical markers), so future drift would require a new leak to materialise. If the pattern recurs we widen Rule 99 then.

**Verification:** `grep -rn 'Rule 41\b\|CHRONOS_HYDRATED\|park the agent' agent-* docs/governance/skill-capacity.yaml` returns no hits.

### P2-3 Developer quickstart still contains a stale package path — CLOSED (K-δ)

**Fix surfaces:**
- `docs/quickstart.md:70`: `ascend.springai.runtime.orchestration.spi.Orchestrator#run` → `ascend.springai.service.runtime.orchestration.spi.Orchestrator#run`.
- Lines 31, 39, 88 (other `ascend.springai.*` references) already carried `post-Phase-C / ADR-0078` markers — unchanged.

Compiling the quickstart snippet as a smoke fixture is a sound idea but out of scope for rc12; tracked as a developer-onboarding improvement for a future wave.

**Verification:** `grep -n 'ascend.springai.runtime' docs/quickstart.md` returns no hits without `service` segment.

### P2-4 Gate README and some status prose lag behind rc11 / ADR-0086 — CLOSED (K-γ)

**Fix surfaces:**
- `gate/README.md`: header `112 active gate rules / 172 self-tests` → `116 active gate rules / 172+ self-tests`; wave narrative extended through rc11 + rc12; row in "Files in this directory" table updated to reflect 116 active executable sections; self-test description prose changed from "161 self-tests" to "172+ self-tests (runtime-derived per Rule G-5.b)".
- `architecture-status.yaml`: `active_engineering_rules: 67 → 30`; `active_gate_checks: 112 → 116`; allowed_claim refreshed for rc12 wave with 86 ADRs (0001 – 0087 minus retired) + 116 active gate rules + 30 active engineering rules.

The reviewer's note about `adr_count: 85` vs file count of 76 is acknowledged. The 85-vs-76 gap is structural: `docs/adr/` contains locked subdir + non-canonical files + retired ADRs; the unique-ID count is the canonical metric. rc12 increments to 86 (ADR-0087 yaml). Auto-parsing unique IDs against the file list is a hardening opportunity tracked for a future wave.

**Prevention:** Rule 82.b + numeric-agreement check covers the gate/README + README.md baseline prose; Rule 101.b covers active_engineering_rules; Rule 97 covers release-note absolute counts.

**Verification:** `grep -E '116 active|172.*self-tests' gate/README.md` returns hits.

### P2-5 Some run lifecycle status prose still describes W2 materialization alongside already-shipped HTTP endpoints — CLOSED (K-ζ)

**Fix surfaces:**
- `architecture-status.yaml#run_lifecycle_spi.allowed_claim`: reworded to make the SPI-vs-HTTP boundary explicit. The `RunLifecycle` SPI remains design-only for W2 resume/retry/cancel orchestration. The W1 HTTP cancel route is SEPARATE: it is shipped in `agent-service RunController.java#cancelRun` (line 141) and is independently re-authorized by Rule R-J.b at the HTTP boundary (tenant cross-check, 403/404/409 semantics, idempotent terminal→terminal). At W2 the SPI will materialize as the in-process resume/retry/cancel orchestration layer used by the async scheduler; until then orchestrator-level cancellation is handled by SyncOrchestrator's existing handoff to RunStateMachine.

**Prevention:** Rule 104 covers catalog truth for the shipped HTTP edge; the allowed_claim split is now load-bearing in `architecture-status.yaml` and `docs/contracts/http-api-contracts.md` POST /v1/runs/{id}/cancel row both reference the SPI/HTTP split explicitly.

**Verification:** allowed_claim prose explicitly cites `RunController.java#cancelRun (line 141)` AND `Rule R-J.b`.

## Methodology codification

The categorize→sweep→batch-fix→prevention discipline is now load-bearing across rc8/rc9/rc10/rc11/rc12 release notes and is mirrored in the rc12 release note Methodology section + ADR-0087. Hidden-defect counts per wave: rc8=14, rc10=9, rc11=30+ (Rule 94 widening), rc12=3. The downward trend reflects narrowing of the cited surfaces; the discipline itself is what closes the family completely each time.

## Open items / deferred items

These are explicitly out of rc12 scope; tracked for future waves only if the pattern recurs:

- **Rule 99 scope widening** to docs/governance/*.yaml + **/spi/**/*.java — rc12 batch-fix cleared the active corpus; widen if leaks reappear.
- **Rule 82 graph-node/edge live-parity** — Rule 97 already gates release-note prose against live values; widen Rule 82 if status.yaml drift recurs without release-note drift catching it first.
- **`gate/build_architecture_graph.py --check --no-write --mermaid`** honoring `--no-write` for the Mermaid sibling — small scoped fix tracked if the existing behavior proves load-bearing.
- **`gate/check_architecture_views.sh`** PATH resolution fix for non-WSL contributors — gate is canonical-run on WSL per Rule G-7; widen if non-WSL contribution flow proves blocking.
- **Compiling `docs/quickstart.md` snippet as smoke fixture** — developer-onboarding improvement for a future wave.
- **`adr_count` auto-derivation from unique ADR ID parse** — small hardening tracked for a future wave.

## Closing

Thank you for the systematic review structure. The 11-finding shape was easy to categorize into 7 families and the explicit Required Closure Criteria gave a sharp ship/no-ship gate. The reviewer's "do not add more broad rules everywhere" caveat shaped the rc12 wave toward 4 targeted source-of-truth category gates (Rules 101 – 104), each with positive + negative self-test fixtures.

The rc12 wave is ready to ship pending CI green (Maven build + Quickstart smoke + arch-graph regen + gate). If any closure is judged insufficient or the prevention rules miss a hidden defect class, please flag the surface and we will run another categorize→sweep→batch-fix→prevention cycle in a rc13 wave.

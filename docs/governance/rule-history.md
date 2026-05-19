# Engineering Rule History

Narrative companion to `CLAUDE.md`. Captures **why** rules entered or left the
active set. `CLAUDE.md` is the normative contract; this file is the record.

Authority: ADR-0064 (governing principles + cleanup) — promotes `CLAUDE.md` to
the layered "Layer-0 Principles / Layer-1 Rules" structure and sinks
review-cycle scaffolding here.

---

## Lifecycle markers

- **Active** — currently in `CLAUDE.md`, enforced by an entry in `docs/governance/enforcers.yaml`.
- **Deferred** — staged in `docs/CLAUDE-deferred.md` with an explicit re-introduction trigger.
- **Retired** — replaced or merged into another rule; not enforced.

---

## Rules by introduction cycle

| Rule | Introduced | Status | Origin |
|---|---|---|---|
| 1–4 | First cycle | Active | Daily engineering principles — root cause, simplicity, pre-commit, three-layer testing. |
| 5–6 | Second cycle | Active | Class-level patterns — async lifetime, single construction path. |
| 7 | Second cycle | Deferred (W2) | Resilience signal masking — re-arms at first soft-fallback path. |
| 8 | Second cycle | Deferred (W2) | Operator-shape readiness gate — re-arms at first shippable JAR with real external dep. |
| 9 | Second cycle | Active | Self-audit ship gate. |
| 10 | Second cycle | Active | Posture-aware defaults. |
| 11 | Second cycle | Deferred (W1) | Contract-spine completeness — re-arms at first persistent record. |
| 12 | Early cycles | **Retired** | Maturity levels L0–L4 — replaced by binary `shipped:` in `docs/governance/architecture-status.yaml`. |
| 13 | Second cycle | Deferred (W3) | P1 cost-of-use constraints. |
| 14 | Second cycle | Deferred (W3) | P3 self-evolution constraints. |
| 15 | Third-review cycle | Deferred (W2) | Streamed handoff mode conformance. |
| 16 | Third-review cycle | Deferred (W2) | Cognitive resource arbitration. |
| 17 | Third-review cycle | Deferred (W2) | Degradation authority + resume re-authorization. |
| 18 | Third-review cycle | Deferred (W4) | Eval harness gate. |
| 19 | Third-review cycle | Deferred (W2) | Runtime hook conformance. |
| 20 | Third-review cycle | Active | Run state transition validity. ADR-0020. |
| 21 | Third-review cycle | Active | Tenant propagation purity. ADR-0023. |
| 22 | Third-review cycle | Deferred (W2) | PayloadCodec discipline. ADR-0022. |
| 23 | Third-review cycle | Deferred (W2) | Suspension write atomicity. ADR-0024. |
| 24 | Third-review cycle | Deferred (W2) | RunLifecycle re-authorization. |
| 25 | Fourth-review cycle | Active | Architecture-text truth gate. ADR-0025/0026/0027. |
| 26 | Fifth-review cycle | Deferred (W2) | Skill lifecycle conformance. ADR-0030. |
| 27 | Fifth-review cycle | Deferred (W3) | Untrusted skill sandbox mandate. ADR-0030. |
| 28 | Fifth-review cycle | Active (L1 governing) | Code-as-Contract. ADR-0059. Forbids prose-only constraints. |
| 29 | Layer-0 governing principles cycle (2026-05-14) | Active | Business/platform decoupling enforcement. ADR-0064. |
| 30 | Layer-0 governing principles cycle (2026-05-14) | Active | Competitive baselines required. ADR-0065. |
| 31 | Layer-0 governing principles cycle (2026-05-14) | Active | Independent module evolution. ADR-0066. |
| 32 | Layer-0 governing principles cycle (2026-05-14) | Active | SPI + DFX + TCK co-design. ADR-0067. |
| 33 | Layered 4+1 + Graph wave (2026-05-14) | Active | Layered 4+1 discipline — every architecture artefact declares level: + view: front-matter; phase-released L0/L1 docs are frozen. ADR-0068. |
| 34 | Layered 4+1 + Graph wave (2026-05-14) | Active | Architecture-Graph truth — docs/governance/architecture-graph.yaml is generated from authoritative inputs and validated for DAG-ness + endpoint resolution + anchor resolution + idempotency. ADR-0068. |
| 35 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | Three-Track Channel Isolation — control/data/rhythm channels physically separated; bus-channels.yaml schema. ADR-0069. |
| 36 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | Cursor Flow Mandate — long-horizon endpoints return Task Cursor immediately; no synchronous blocking. ADR-0069. |
| 37 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | Reactive External I/O — runtime main MUST NOT import RestTemplate or JdbcTemplate. ADR-0069. |
| 38 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | No Thread.sleep in business code — long waits via SuspendSignal + bus Tick Engine (Chronos Hydration). ADR-0069. |
| 39 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | Five-Plane Manifest — every module-metadata.yaml declares deployment_plane:. ADR-0069. |
| 40 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | Storage-Engine Tenant Isolation — new Flyway migrations enable RLS on tenant-scoped tables. ADR-0069. |
| 41 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | Skill Capacity Matrix — skill-capacity.yaml per-tenant + global capacity for every skill. ADR-0069. |
| 42 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | Sandbox Permission Subsumption — sandbox-policies.yaml default_policy + per-skill physical limits. ADR-0069. |

## Gate-Rule additions (Layer-1 enforcement scripts, not engineering rules)

The following are gate-script rules in `gate/check_architecture_sync.sh` introduced by the W1 + Phase-M waves. They enforce CLAUDE.md Rules 33-34; they are not themselves engineering rules.

| Gate Rule | Cycle | Status | Origin |
|---|---|---|---|
| 37 | W1 Layered 4+1 + Graph (2026-05-14) | Active | architecture_artefact_front_matter — every ADR.yaml / L2.md / ARCHITECTURE.md declares level: + view:. Enforcer E55. |
| 38 | W1 Layered 4+1 + Graph (2026-05-14) | Active | architecture_graph_well_formed — graph builds without validation errors. Enforcer E56. |
| 39 | W1 Layered 4+1 + Graph (2026-05-14) | Active | review_proposal_front_matter — docs/logs/reviews/*.md declare affects_level: + affects_view:. Enforcer E57. |
| 40 | W1 Layered 4+1 + Graph (2026-05-14) | Active | enforcer_reachable_from_principle — every enforcer has at least one rule→enforcer edge. Enforcer E58. |
| 41 | Phase M (2026-05-14) | Active | enforcer_anchor_resolves — every artefact anchor resolves to a real method/heading/key. Enforcer E60. |
| 42 | Phase M (2026-05-14) | Active | architecture_graph_idempotent — twice-run graph build is byte-identical. Enforcer E61. |
| 43 | Phase M (2026-05-14) | Active | new_adr_must_be_yaml — highest-numbered ADR is .yaml, not .md. Enforcer E62. |
| 44 | Phase M (2026-05-14) | Active | frozen_doc_edit_path_compliance — modifications to freeze_id-tagged files require an accompanying docs/logs/reviews/*.md proposal. Enforcer E63. |
| 45 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | bus_channels_three_track_present — bus-channels.yaml schema check (Rule 35). Enforcer E64. |
| 46 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | cursor_flow_documented — openapi-v1.yaml declares 202 + cursor for long-horizon endpoint (Rule 36). Enforcer E65. |
| 47 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | no_blocking_io_in_runtime_main — historical wording for the deleted agent-runtime module; post-Phase-C / ADR-0078 the rule scope is `agent-service/src/main/java/ascend/springai/service/runtime/**` which excludes RestTemplate / JdbcTemplate (Rule 37). Enforcer E66. |
| 48 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | no_thread_sleep_in_business_code — main java sources exclude Thread.sleep / TimeUnit.sleep (Rule 38). Enforcer E67. |
| 49 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | deployment_plane_in_module_metadata — every module-metadata.yaml declares deployment_plane (Rule 39). Enforcer E68. |
| 50 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | rls_for_new_tenant_tables — Flyway migrations with tenant_id enable RLS or are grandfathered (Rule 40). Enforcer E69. |
| 51 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | skill_capacity_yaml_present_and_wellformed — skill-capacity.yaml schema check (Rule 41). Enforcer E70. |
| 52 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | sandbox_policies_yaml_present_and_wellformed — sandbox-policies.yaml schema check (Rule 42). Enforcer E71. |

---

## 2026-05-18 — Rules 80-83 added (rc4 cross-constraint review response prevention wave)

- **Rule 80** `s2c_callback_signal_historical_only_in_authority` — closes rc4 review P0-1 (ADR-0074 + s2c-callback.v1.yaml + enforcers.yaml E82 still described deleted `S2cCallbackSignal` as current ship). Enforcer E113.
- **Rule 81** `skeleton_module_has_no_production_java` — closes rc4 review P0-2 (agent-execution-engine claimed skeleton across README + ARCHITECTURE.md + pom.xml + architecture-status while ADR-0079 had extracted production code). Enforcer E114.
- **Rule 82** `baseline_metrics_single_source` — closes rc4 review P1-1 (baseline counts contradicted across README + AGENTS.md + gate/README + architecture-status). New `baseline_metrics:` structured block added under `architecture_sync_gate:`. Enforcer E115.
- **Rule 83** `design_only_contract_registered_in_catalog` — closes rc4 review P1-3 (plan-projection.v1.yaml unregistered, W2/W4 staging ambiguity). ADR-0032 amended with PlanProjection staging note. Enforcer E116.

Authority documents: `docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md` (review) and `docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review-response.en.md` (response).

---

## 2026-05-18 — Rules 84-85 added + Rule 82 strengthened + ADR-0080 (rc5 post-response review response prevention wave, v2.0.0-rc6)

- **Rule 82** STRENGTHENED — original kernel (substring-pointer presence) extended with a numeric-agreement check: every active `N <phrase>` count in README.md / gate/README.md (outside fenced code blocks and outside historical-marker lines) MUST equal the parsed baseline_metrics value for the phrase's canonical key (e.g. "N active gate rules" MUST equal `active_gate_checks`). Closes rc5 post-response review P1-1 — the rule no longer passes vacuously when a stale count sits adjacent to a correct pointer. E115 `asserts:` widened. Three new self-tests (numeric_agreement_pos / pointer_present_but_stale_count_neg / historical_marker_exempts_neg).
- **Rule 84** `active_module_architecture_path_truth` — closes rc5 post-response review P0-1 (`agent-service/ARCHITECTURE.md` still cited engine + S2C SPI paths under `service.runtime.engine/` and `service.runtime.s2c/spi/` after ADR-0079 had moved them to `agent-execution-engine` and `agent-runtime-core`). The rule catches active-module path-claim drift that Rule 81 (skeleton-only) cannot reach. Enforcer E117.
- **Rule 85** `catalog_spi_row_matches_module_spi_metadata` — closes rc5 post-response review P1-2 (`ResilienceContract` listed as 1 of 11 SPIs in `contract-catalog.md` but its package `ascend.springai.service.runtime.resilience` had no `.spi` token and was not in `module-metadata.yaml#spi_packages` — Rule 77 passed vacuously). Enforcer E118.
- **ADR-0080** "ResilienceContract `.spi` package alignment" — substantive closure half of rc5 P1-2: moves `ResilienceContract` + value types (`ResiliencePolicy`, `SkillResolution`, `SuspendReason`, `SkillCapacityRegistry`) to `ascend.springai.service.runtime.resilience.spi.*`; implementations stay at the parent package. `agent-service/module-metadata.yaml#spi_packages` and `docs/dfx/agent-service.yaml#spi_packages` both gain the new entry. Extends ADR-0030, ADR-0070; relates to ADR-0072, ADR-0079.

Authority documents: `docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md` (review) and `docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review-response.en.md` (response).

---

## 2026-05-18 — Rules 86-87 added + ADR-0081 + RunRepository/GraphMemoryRepository surface enumeration (rc6 post-response review response prevention wave, v2.0.0-rc7)

- **Rule 86** `root_architecture_count_and_path_truth` — closes rc6 post-response review P0-2 (`ARCHITECTURE.md:77-79` declared "Eight-module post-Phase-C state" + "**8 modules**" while `pom.xml` had 9 and `architecture-status.yaml#repository_counts.reactor_modules: 9`; tree lines 140-193 listed deleted `agent-platform/` + `agent-runtime/` as current). Rule 84 covers `agent-*/ARCHITECTURE.md`; Rule 86 covers the L0 root entrypoint. Enforcer E119.
- **Rule 87** `status_yaml_allowed_claim_module_name_truth` — closes rc6 post-response review P1-2 (`architecture-status.yaml` `allowed_claim:` text at lines 720 / 1054 / 1391 / 1409 carried current-tense `agent-platform` + `agent-runtime` references after Phase C deleted those modules; family self-check found the 4th spot at line 720 the reviewer missed). Negative-lookahead on `agent-runtime-core` (the new shared-kernel module from ADR-0079). Enforcer E120.
- **ADR-0081** "ResilienceContract dual-surface reconciliation" — closes rc6 P1-1: formally codifies that ResilienceContract is dual-surface (operation-policy `resolve(operationId)` + skill-capacity `resolve(tenant, skill)` per ADR-0070, Rule 41.b) and SUPERSEDES the pre-ADR-0070 plan in ADR-0030 / ADR-0044 to extend the operation surface to `(tenantId, operationId)`. Java surface unchanged; contract catalog + ADR-0030 + ADR-0044 + ResilienceContract.java Javadoc amended in place with @see cross-refs.
- **ADR-0021 + ADR-0034 doc-precision addendum** — `RunRepository` 6-method surface (findById, save, findByTenant, findByParentRunId, findByTenantAndStatus, findRootRuns) and `GraphMemoryRepository` 3-method surface (addFact, query, search) explicitly enumerated to harden multi-axis SPI documentation discipline alongside the F-β1 reconciliation. Per-method axis classification recorded so W2 implementers have a stable target surface.

Authority documents: `docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md` (review) and `docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review-response.en.md` (response).

---

## 2026-05-18 — Rules 88-89 added + Rule 86 fenced-tree-block extension + ADR-0082 (rc7 post-corrective review response prevention wave, v2.0.0-rc8)

- **Rule 88** `serial_parallel_gate_slug_parity` — closes rc7 post-corrective review P0-2 (`gate/check_parallel.sh` exited 0 but silently skipped Rules 86 and 87 due to a compound defect: its awk terminated at `^# Summary$` AND its rule-header regex required em-dash `—` while Rules 86-87 used double-dash `--`). The canonical `gate/check_architecture_sync.sh` was refactored to use an explicit `# === END OF RULES ===` terminator marker and em-dash separators throughout; the parallel wrapper's awk follows the new contract. Rule 88 asserts at gate time that the slug set extracted by both scripts is equal, that every rule header uses em-dash, and that the END marker is present. Enforcer E121.
- **Rule 89** `self_test_harness_fail_closed_coverage` — closes rc7 post-corrective review P1-1 (`gate/test_architecture_sync_gate.sh:43` carried dead `TOTAL=138` and line 4098 carried `TOTAL=143`, yet only ~37 named `test_rule*()` functions existed; exit logic at lines 4163-4168 ignored TOTAL entirely — `failed=0` was the only condition for exit 0, so the harness exited 0 while reporting `Tests passed: 37/143`). Inline Rule 86/87 fixtures (top-level blocks at lines 3942-4087) emitted PASS/FAIL but the parallel orchestrator at line 4098 OVERWROTE the global counters with function-dispatched results only — losing the inline contributions. Track D wrapped each inline block as a proper `test_rule86_*()` / `test_rule87_*()` function so the parallel orchestrator picks them up; TOTAL is now derived (`TOTAL=$((passed + failed))`); a `passed != TOTAL` fail-closed clause was added. Rule 89 asserts these three invariants. Enforcer E122.
- **Rule 86 fenced-tree-block extension** (amendment to the rc7 rule) — closes rc7 post-corrective review P0-1 (GraphMemoryRepository ownership drift hid inside the root ARCHITECTURE.md tree fenced block where the original Rule 86 pass intentionally skipped all lines). The rc8 amendment adds a SECOND pass that scans ONLY fenced tree blocks, tracks module-header indent context, and for each indented `<pkg>/spi/` leaf checks that the parent module's `module-metadata.yaml#spi_packages` declares an entry containing `.<pkg>.spi`. Historical markers within ±3 lines still exempt. Enforcer E119 (same; scope widened).
- **ADR-0082** "GraphMemoryRepository canonical ownership + module-metadata.yaml is single source of truth for SPI ownership topology" — closes rc7 post-corrective review P0-1 plus 5 hidden defects surfaced by the family sweep (G-α taxonomy): (a) `agent-service/module-metadata.yaml` description still claimed `service.runtime` owned orchestration SPI + Run lifecycle (pre-ADR-0079), (b) `agent-runtime-core/ARCHITECTURE.md` §2 Contents under-enumerated its surfaces (missing TraceContext, ExecutorDefinition, S2C SPI trio; mis-pathed RunRepository), (c) `docs/dfx/agent-runtime.yaml` was still on disk for the now-deleted pre-Phase-C module, (d) root ARCHITECTURE.md tree mentioned a ghost `S2cCallbackOutcome` Java type with no source file, (e) ADR-0081 verification line carried stale `≥142/142` count. Decision: GraphMemoryRepository stays on agent-service (documentation-only correction; ADR-0079 deliberately did NOT extract it); `module-metadata.yaml#spi_packages` ∩ actual Java file path is canonical SSOT for SPI ownership across every authority surface.

Authority documents: `docs/logs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md` (review) and `docs/logs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review-response.en.md` (response).

---

## Retired-rule notes

### Rule 12 — Maturity L0–L4

Originally a four-step maturity ladder (`L0` design → `L1` impl → `L2` tested →
`L3` shipped → `L4` audited). Replaced because in practice every audit reduced
to a binary "is the row in `architecture-status.yaml` marked `shipped: true`
and backed by a real test class?". Multiple maturity buckets produced status
drift and gave reviewers an excuse to claim partial credit. The binary
`shipped:` + the `tests:` evidence list is the truth.

---

## Cleanup notes (2026-05-14)

The following content moved out of `CLAUDE.md` into this file:

- Narrative paragraph ("Twelve active rules. Rules 1–4 are daily-use…") — replaced by the Layer-0 / Layer-1 framing in `CLAUDE.md`.
- Per-rule "added in N-th review cycle" annotations — captured in the table above.
- "Rule 12 replaced by binary `shipped:`" sentence — captured in the retired-rule note above.
- "Constraint Coverage by First Principle" section — moved to [`principle-coverage.yaml`](principle-coverage.yaml) (Phase M retired the prior `.md` form per ADR-0068).
- "W0 posture coverage" table inside Rule 10 — moved to [`posture-coverage.md`](posture-coverage.md).

## 2026-05-19 — Rules 91-96 added + Rules 42/46/89 narrowed + ADR-0083 + CI-green restoration (rc8 post-corrective review response prevention wave, v2.0.0-rc9)

- **Rule 91** `baseline_metric_matches_executable_manifest` — closes rc8 post-corrective review P0-1 (`active_gate_checks: 74` published baseline vs `parallel_summary: executed 102 rules` executable manifest — 28-section gap persisted across 8 release notes because nothing checked manifest-vs-ledger agreement). ADR-0083 adopts the executable-section count as the canonical meaning of `active_gate_checks`; the historical "rule families" count is preserved in `active_engineering_rules_post_rcN`. Enforcer E123 (positive) + E124 (negative self-test fixture).
- **Rule 92** `gate_rules_corpus_freshness` — closes rc8 post-corrective review P2-1 (`gate/rules/` had 83 files vs 102 canonical headers; the production parallel gate consumes the canonical monolith directly, so the shadow corpus is IDE-only). Enforces that every `# Rule N — slug` header in canonical has a matching `gate/rules/rule-NNN[a-z]?.sh` file. Enforcer E125 + E126.
- **Rule 93** `dfx_stem_matches_module` — closes rc8 post-corrective review P0-3 (`docs/dfx/agent-platform.yaml` orphan after ADR-0078 deleted the module; ADR-0082 mandated removal but the gate had no orphan-detection check). Enforces that every `docs/dfx/*.yaml` stem matches a `<module>` entry in root `pom.xml`. Enforcer E127 + E128.
- **Rule 94** `active_corpus_deleted_module_name_truth` — closes rc8 post-corrective review P1-3 (Rule 87 only covered `architecture-status.yaml#allowed_claim`; ARCHITECTURE.md #59, McpReplaySurfaceArchTest Javadoc, rule-37.md still carried current-tense `agent-platform` / `agent-runtime` claims). Widens Rule 87's discipline across active `.md`, `.yaml`, `*.java` files (excluding `docs/archive/`, `docs/logs/reviews/`, `docs/logs/releases/2026-05-1[0-7]-*.md`, fenced code blocks, yaml comment lines). Uses POSIX bracket-class word-boundary regex (GNU awk doesn't honor `\b`); ±3-line historical-marker window; `forbidden_dependencies` + `Forbidden imports` accepted as exemption keys for legitimate sentinel-list usages. Enforcer E129 + E130.
- **Rule 95** `spi_catalog_exhaustiveness` — closes rc8 post-corrective review P1-2 (`SkillCapacityRegistry` public `.spi` interface absent from `contract-catalog.md` §2 "Active SPI interfaces (11 total)" — Rule 85 enforced the catalog-to-metadata direction but not the metadata-to-catalog direction). Asserts that every `public interface` (non-sealed; sealed types are structural carriers per catalog convention) under any `*/spi/*` path appears in the catalog as an active SPI row OR marked `(internal)`. Enforcer E131 + E132.
- **Rule 96** `kernel_deferred_clause_coherence` — closes rc8 post-corrective review P1-1 (Rule 42 + Rule 46 active kernels overclaimed runtime enforcement that `docs/CLAUDE-deferred.md` correctly deferred to W2; downstream readers couldn't reconcile the two authoritative sources). For every `## Rule N.<letter>` sub-clause in CLAUDE-deferred.md, asserts that either the matching `#### Rule N` kernel block in CLAUDE.md OR the matching `docs/governance/rules/rule-NN.md` card contains the literal string `Rule N.<letter>`. The card-OR-kernel disjunction lets rules with lengthy deferred discussions cite from the card (which has no `kernel_cap`) without bloating CLAUDE.md. Enforcer E133 + E134.
- **Rule 42 kernel narrowed** — removed the "runtime `SandboxExecutor` MUST refuse" sentence (deferred to Rule 42.b per `docs/CLAUDE-deferred.md`). CLAUDE.md kernel + `docs/governance/rules/rule-42.md` card updated; the deferred sub-clause unchanged.
- **Rule 46 kernel narrowed + sub-clause naming corrected** — capacity is now `declared in skill-capacity.yaml`; runtime admission deferred to Rule 46.b (W2). Cross-references list in `rule-46.md` corrected: 46.b = `ResilienceContract` capacity wiring (matching CLAUDE-deferred.md 46.b); 46.c = non-blocking lifecycle. Invalid-response handling is shipped at L1.x (per the kernel's `BEFORE-resume` validation clause + `S2cCallbackEnvelopeValidationTest` enforcer E89), NOT a deferred sub-clause.
- **Rule 89 scope narrowed across surfaces** — `enforcers.yaml` E122 `asserts:` + `gate/README.md` line 68 updated to say "every prevention-wave Rule (`N >= 80`)" matching CLAUDE.md Rule 89 kernel and the gate implementation. Pre-rc4 Rules 1-79 explicitly grandfathered (covered by ArchUnit / IT at design time). Closes rc8 P1-4.
- **ADR-0083** "rc8 post-corrective response: baseline taxonomy + orphan-authority retirement + SPI catalog exhaustiveness + kernel-deferred coherence + CI as release-acceptance gate" — authoritative record of the rc9 wave; accepts all 7 findings of `docs/logs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` (Codex, 2026-05-18); H-α/H-β/H-γ/H-δ/H-ε family taxonomy. Records the alternative considered for Rule 90 (release-note CI evidence) and its deferral to the next wave.
- **CI-green restoration** — bundled into the same wave: dropped `@ConditionalOnMissingBean(AsyncRunDispatcher.class)` from `NoOpAsyncRunDispatcher` (annotation unreliable on `@Component`); dropped `@ConditionalOnBean(DataSource.class)` from `IdempotencyStoreAutoConfiguration#jdbcIdempotencyStore` (Spring Boot 4 ordering hazard); added `@ConditionalOnWebApplication(type = SERVLET)` to `WebSecurityConfig`; added JdbcIdempotencyStore fixture to `PostureBindingIT.RunRepositoryFixture`; declared dummy `spring.ai.openai.api-key` + `spring.ai.anthropic.api-key` in `application.yml` for Spring AI 2.0.0-M5 eager autoconfig; annotated `RunResponse` record components with `@Schema(requiredMode = REQUIRED)`. First green CI run on `main` since rc1 (previously 48/50 runs failed).
- **Orphan-authority retirement** — `docs/STATE.md` archived to `docs/archive/2026-05-19-STATE-md-archived/STATE.md` with non-authoritative front-matter banner; `docs/dfx/agent-platform.yaml` deleted (orphan after ADR-0078).

---

## 2026-05-20 — rc13 wave: dissolve agent-runtime-core + lock client→bus→server ingress

Authority: ADR-0088 (agent-runtime-core dissolution) + ADR-0089 (Edge-Plane Ingress Gateway Mandate). Plan: D:/.claude/plans/l0-agent-runtime-core-agent-client-agen-staged-kay.md.

### Structural changes

- **Reactor 9 → 8 modules.** ADR-0088 dissolved `agent-runtime-core`. Its 16 production Java sources (+ 4 tests) were redistributed:
  - `Run / RunStatus / RunStateMachine / RunRepository (+ package-info) / IdempotencyRecord` → `agent-service/src/main/java/ascend/springai/service/runtime/{runs,idempotency}/**` (same package paths).
  - `RunMode + 6 orchestration SPI types (Checkpointer / Orchestrator / RunContext / SuspendSignal / TraceContext / ExecutorDefinition)` → `agent-execution-engine/src/main/java/ascend/springai/engine/orchestration/spi/**` (renamed from `service.runtime.orchestration.spi`).
  - `S2cCallbackTransport / S2cCallbackEnvelope / S2cCallbackResponse` → `agent-bus/src/main/java/ascend/springai/bus/spi/s2c/**` (renamed from `service.runtime.s2c.spi`).
- **ADR-0079 superseded** by ADR-0088. `superseded_by: [ADR-0088]` added.
- **New cross-plane control surface**: ADR-0089 adds `ascend.springai.bus.spi.ingress.IngressGateway` SPI + `IngressEnvelope` + `IngressResponse` + `docs/contracts/ingress-envelope.v1.yaml` (status `design_only`; runtime binding W3+ with agent-client SDK). Symmetric with ADR-0088's `bus.spi.s2c` placement: bus plane owns the entirety of cross-plane traffic in both directions (C2S + S2C).

### Rule changes

- **Rule R-C sub-clause .c** (Contract Spine Completeness): path scope updated from `agent-runtime-core/src/main/java/ascend/springai/service/runtime/**/*.java` to `agent-service/src/main/java/ascend/springai/service/runtime/{runs,idempotency}/**/*.java`. Authority refs: `ADR-0079` dropped, `ADR-0088` added.
- **Rule R-I** (Five-Plane Manifest): sub-clause `.b` ADDED — "Edge↔Compute Ingress Routing" per ADR-0089. Modules whose `deployment_plane` is `edge` MUST NOT import any production class under `ascend.springai.{service,engine,middleware}..` and MUST NOT invoke compute_control HTTP routes directly; cross-plane traffic flows through `bus.spi.ingress.IngressGateway`. Enforcer E143 (ArchUnit `EdgeToComputeDirectLinkArchTest`) + gate Rule 105 (`edge_no_direct_compute_link`, enforcer E144).
- **Rule 11** (`contract_spine_tenant_id_required`): path scope updated alongside Rule R-C.c — now scans `agent-service/.../runs/` and `agent-service/.../idempotency/` (was `agent-runtime-core/.../service/runtime/`).
- **Rule 28e** (`module_count_invariant`): expected count 9 → 8 (BoM + 6 substantive domain modules + GraphMemory starter).
- **Rule 87** (`status_yaml_allowed_claim_module_name_truth`): deleted-module-name set widened to include `agent-runtime-core`. Marker vocabulary extended with `dissolution|dissolved|relocated|rc13|ADR-0088|ADR-0089`.
- **Rule 94** (`active_corpus_deleted_module_name_truth`): awk pattern widened — now scans for `agent-platform | agent-runtime | agent-runtime-core` (all three deleted-module names post-rc13). Marker vocabulary externalised file (`gate/active-corpus-name-exemption-markers.txt`) extended.
- **Rule 98** (`broad_corpus_deleted_module_name_truth`): same widening as Rule 94 in the awk pattern.

### New gate rules

- **Rule 105** (`edge_no_direct_compute_link`, enforcer E144): source-grep complement to E143 ArchUnit. Scans `<module>/src/main/java/**/*.java` for every `deployment_plane: edge` module — fails closed on forbidden `import ascend.springai.{service,engine,middleware}.*` lines OR `new RestTemplate(...)` / `WebClient.builder(...)` construction.

### New enforcer rows

- **E143** — ArchUnit test `EdgeToComputeDirectLinkArchTest` in `agent-client/src/test/java/`.
- **E144** — gate-script Rule 105 source-grep complement.

### Contract changes

- New `docs/contracts/ingress-envelope.v1.yaml` (status `design_only`; authority ADR-0089).
- `s2c-callback.v1.yaml` java type ownership moved to `agent-bus` per ADR-0088 (schema unchanged).
- `docs/contracts/contract-catalog.md` §2 / §3 / §7 rewritten to reflect new module ownership + 8-row Maven BoM table + IngressGateway row.

### Module-metadata changes

- `agent-service/module-metadata.yaml`: `+spi_packages: runs.spi`; `allowed_dependencies` swapped `agent-runtime-core` for `agent-execution-engine` + `agent-bus`.
- `agent-execution-engine/module-metadata.yaml`: `+spi_packages: engine.orchestration.spi`; `allowed_dependencies` dropped `agent-runtime-core` (engine self-contains its SPI).
- `agent-bus/module-metadata.yaml`: `spi_packages: [bus.spi.ingress, bus.spi.s2c]` (was `[bus.spi]` placeholder).
- `agent-client/module-metadata.yaml`: `forbidden_dependencies` dropped `agent-runtime-core`; description amended to declare IngressGateway as the sole cross-plane consumption surface.

### Files deleted

- `agent-runtime-core/` directory entirely (pom.xml, module-metadata.yaml, ARCHITECTURE.md, src/, target/).
- `docs/dfx/agent-runtime-core.yaml`.

### Baseline_metrics updates

- `repository_counts.reactor_modules`: 9 → 8.
- `repository_counts.internal_modules`: 7 → 6.
- `repository_counts.total_reactor_modules`: 9 → 8.
- `repository_counts.skeleton_modules`: 3 → 2 (agent-bus is no longer skeleton — owns active ingress + s2c SPIs).
- `baseline_metrics.adr_count`: 86 → 88.
- `baseline_metrics.active_gate_checks`: 116 → 117.
- `baseline_metrics.enforcer_rows`: 142 → 144.
- `baseline_metrics.gate_executable_test_cases`: 180 → 182.
- `baseline_metrics.architecture_graph_{nodes,edges}`: regenerated post-merge.

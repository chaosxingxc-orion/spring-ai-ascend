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
| 39 | W1 Layered 4+1 + Graph (2026-05-14) | Active | review_proposal_front_matter — docs/reviews/*.md declare affects_level: + affects_view:. Enforcer E57. |
| 40 | W1 Layered 4+1 + Graph (2026-05-14) | Active | enforcer_reachable_from_principle — every enforcer has at least one rule→enforcer edge. Enforcer E58. |
| 41 | Phase M (2026-05-14) | Active | enforcer_anchor_resolves — every artefact anchor resolves to a real method/heading/key. Enforcer E60. |
| 42 | Phase M (2026-05-14) | Active | architecture_graph_idempotent — twice-run graph build is byte-identical. Enforcer E61. |
| 43 | Phase M (2026-05-14) | Active | new_adr_must_be_yaml — highest-numbered ADR is .yaml, not .md. Enforcer E62. |
| 44 | Phase M (2026-05-14) | Active | frozen_doc_edit_path_compliance — modifications to freeze_id-tagged files require an accompanying docs/reviews/*.md proposal. Enforcer E63. |
| 45 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | bus_channels_three_track_present — bus-channels.yaml schema check (Rule 35). Enforcer E64. |
| 46 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | cursor_flow_documented — openapi-v1.yaml declares 202 + cursor for long-horizon endpoint (Rule 36). Enforcer E65. |
| 47 | W1.x L0 ironclad-rules wave (2026-05-15) | Active | no_blocking_io_in_runtime_main — agent-runtime main excludes RestTemplate / JdbcTemplate (Rule 37). Enforcer E66. |
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

Authority documents: `docs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md` (review) and `docs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review-response.en.md` (response).

---

## 2026-05-18 — Rules 84-85 added + Rule 82 strengthened + ADR-0080 (rc5 post-response review response prevention wave, v2.0.0-rc6)

- **Rule 82** STRENGTHENED — original kernel (substring-pointer presence) extended with a numeric-agreement check: every active `N <phrase>` count in README.md / gate/README.md (outside fenced code blocks and outside historical-marker lines) MUST equal the parsed baseline_metrics value for the phrase's canonical key (e.g. "N active gate rules" MUST equal `active_gate_checks`). Closes rc5 post-response review P1-1 — the rule no longer passes vacuously when a stale count sits adjacent to a correct pointer. E115 `asserts:` widened. Three new self-tests (numeric_agreement_pos / pointer_present_but_stale_count_neg / historical_marker_exempts_neg).
- **Rule 84** `active_module_architecture_path_truth` — closes rc5 post-response review P0-1 (`agent-service/ARCHITECTURE.md` still cited engine + S2C SPI paths under `service.runtime.engine/` and `service.runtime.s2c/spi/` after ADR-0079 had moved them to `agent-execution-engine` and `agent-runtime-core`). The rule catches active-module path-claim drift that Rule 81 (skeleton-only) cannot reach. Enforcer E117.
- **Rule 85** `catalog_spi_row_matches_module_spi_metadata` — closes rc5 post-response review P1-2 (`ResilienceContract` listed as 1 of 11 SPIs in `contract-catalog.md` but its package `ascend.springai.service.runtime.resilience` had no `.spi` token and was not in `module-metadata.yaml#spi_packages` — Rule 77 passed vacuously). Enforcer E118.
- **ADR-0080** "ResilienceContract `.spi` package alignment" — substantive closure half of rc5 P1-2: moves `ResilienceContract` + value types (`ResiliencePolicy`, `SkillResolution`, `SuspendReason`, `SkillCapacityRegistry`) to `ascend.springai.service.runtime.resilience.spi.*`; implementations stay at the parent package. `agent-service/module-metadata.yaml#spi_packages` and `docs/dfx/agent-service.yaml#spi_packages` both gain the new entry. Extends ADR-0030, ADR-0070; relates to ADR-0072, ADR-0079.

Authority documents: `docs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md` (review) and `docs/reviews/2026-05-18-l0-rc5-post-response-architecture-review-response.en.md` (response).

---

## 2026-05-18 — Rules 86-87 added + ADR-0081 + RunRepository/GraphMemoryRepository surface enumeration (rc6 post-response review response prevention wave, v2.0.0-rc7)

- **Rule 86** `root_architecture_count_and_path_truth` — closes rc6 post-response review P0-2 (`ARCHITECTURE.md:77-79` declared "Eight-module post-Phase-C state" + "**8 modules**" while `pom.xml` had 9 and `architecture-status.yaml#repository_counts.reactor_modules: 9`; tree lines 140-193 listed deleted `agent-platform/` + `agent-runtime/` as current). Rule 84 covers `agent-*/ARCHITECTURE.md`; Rule 86 covers the L0 root entrypoint. Enforcer E119.
- **Rule 87** `status_yaml_allowed_claim_module_name_truth` — closes rc6 post-response review P1-2 (`architecture-status.yaml` `allowed_claim:` text at lines 720 / 1054 / 1391 / 1409 carried current-tense `agent-platform` + `agent-runtime` references after Phase C deleted those modules; family self-check found the 4th spot at line 720 the reviewer missed). Negative-lookahead on `agent-runtime-core` (the new shared-kernel module from ADR-0079). Enforcer E120.
- **ADR-0081** "ResilienceContract dual-surface reconciliation" — closes rc6 P1-1: formally codifies that ResilienceContract is dual-surface (operation-policy `resolve(operationId)` + skill-capacity `resolve(tenant, skill)` per ADR-0070, Rule 41.b) and SUPERSEDES the pre-ADR-0070 plan in ADR-0030 / ADR-0044 to extend the operation surface to `(tenantId, operationId)`. Java surface unchanged; contract catalog + ADR-0030 + ADR-0044 + ResilienceContract.java Javadoc amended in place with @see cross-refs.
- **ADR-0021 + ADR-0034 doc-precision addendum** — `RunRepository` 6-method surface (findById, save, findByTenant, findByParentRunId, findByTenantAndStatus, findRootRuns) and `GraphMemoryRepository` 3-method surface (addFact, query, search) explicitly enumerated to harden multi-axis SPI documentation discipline alongside the F-β1 reconciliation. Per-method axis classification recorded so W2 implementers have a stable target surface.

Authority documents: `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md` (review) and `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review-response.en.md` (response).

---

## 2026-05-18 — Rules 88-89 added + Rule 86 fenced-tree-block extension + ADR-0082 (rc7 post-corrective review response prevention wave, v2.0.0-rc8)

- **Rule 88** `serial_parallel_gate_slug_parity` — closes rc7 post-corrective review P0-2 (`gate/check_parallel.sh` exited 0 but silently skipped Rules 86 and 87 due to a compound defect: its awk terminated at `^# Summary$` AND its rule-header regex required em-dash `—` while Rules 86-87 used double-dash `--`). The canonical `gate/check_architecture_sync.sh` was refactored to use an explicit `# === END OF RULES ===` terminator marker and em-dash separators throughout; the parallel wrapper's awk follows the new contract. Rule 88 asserts at gate time that the slug set extracted by both scripts is equal, that every rule header uses em-dash, and that the END marker is present. Enforcer E121.
- **Rule 89** `self_test_harness_fail_closed_coverage` — closes rc7 post-corrective review P1-1 (`gate/test_architecture_sync_gate.sh:43` carried dead `TOTAL=138` and line 4098 carried `TOTAL=143`, yet only ~37 named `test_rule*()` functions existed; exit logic at lines 4163-4168 ignored TOTAL entirely — `failed=0` was the only condition for exit 0, so the harness exited 0 while reporting `Tests passed: 37/143`). Inline Rule 86/87 fixtures (top-level blocks at lines 3942-4087) emitted PASS/FAIL but the parallel orchestrator at line 4098 OVERWROTE the global counters with function-dispatched results only — losing the inline contributions. Track D wrapped each inline block as a proper `test_rule86_*()` / `test_rule87_*()` function so the parallel orchestrator picks them up; TOTAL is now derived (`TOTAL=$((passed + failed))`); a `passed != TOTAL` fail-closed clause was added. Rule 89 asserts these three invariants. Enforcer E122.
- **Rule 86 fenced-tree-block extension** (amendment to the rc7 rule) — closes rc7 post-corrective review P0-1 (GraphMemoryRepository ownership drift hid inside the root ARCHITECTURE.md tree fenced block where the original Rule 86 pass intentionally skipped all lines). The rc8 amendment adds a SECOND pass that scans ONLY fenced tree blocks, tracks module-header indent context, and for each indented `<pkg>/spi/` leaf checks that the parent module's `module-metadata.yaml#spi_packages` declares an entry containing `.<pkg>.spi`. Historical markers within ±3 lines still exempt. Enforcer E119 (same; scope widened).
- **ADR-0082** "GraphMemoryRepository canonical ownership + module-metadata.yaml is single source of truth for SPI ownership topology" — closes rc7 post-corrective review P0-1 plus 5 hidden defects surfaced by the family sweep (G-α taxonomy): (a) `agent-service/module-metadata.yaml` description still claimed `service.runtime` owned orchestration SPI + Run lifecycle (pre-ADR-0079), (b) `agent-runtime-core/ARCHITECTURE.md` §2 Contents under-enumerated its surfaces (missing TraceContext, ExecutorDefinition, S2C SPI trio; mis-pathed RunRepository), (c) `docs/dfx/agent-runtime.yaml` was still on disk for the now-deleted pre-Phase-C module, (d) root ARCHITECTURE.md tree mentioned a ghost `S2cCallbackOutcome` Java type with no source file, (e) ADR-0081 verification line carried stale `≥142/142` count. Decision: GraphMemoryRepository stays on agent-service (documentation-only correction; ADR-0079 deliberately did NOT extract it); `module-metadata.yaml#spi_packages` ∩ actual Java file path is canonical SSOT for SPI ownership across every authority surface.

Authority documents: `docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md` (review) and `docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review-response.en.md` (response).

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

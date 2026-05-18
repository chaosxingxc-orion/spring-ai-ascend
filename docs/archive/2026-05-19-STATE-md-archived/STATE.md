# Current State (W0) — ARCHIVED 2026-05-19, NON-AUTHORITATIVE

> **Historical artifact frozen at SHA d4ee319 (pre-rc9, 2026-05-18).** This file
> was the pre-Phase-C per-capability state ledger. It is superseded by
> `docs/governance/architecture-status.yaml` per ADR-0083 (2026-05-19, rc9 wave
> corpus-truth response). All paths in this file use the pre-ADR-0078 module
> names (`agent-platform/...`, `agent-runtime/...`); current paths live under
> `agent-service/...` and `agent-runtime-core/...`. Do not cite this file as
> a current authority; consult `docs/governance/architecture-status.yaml`.

<!-- columns: capability | shipped | code-path | test-path | posture-coverage | claim -->

## Shipped (W0)

| capability | shipped | code-path | test-path | posture-coverage | claim |
|------------|---------|-----------|-----------|------------------|-------|
| health-endpoint | true | `agent-platform/src/main/java/ascend/springai/platform/web/HealthController.java` | `agent-platform/src/test/java/ascend/springai/platform/HealthEndpointIT.java` | dev/research/prod | GET /v1/health returns 200 |
| tenant-filter | true | `agent-platform/src/main/java/ascend/springai/platform/tenant/TenantContextFilter.java` | `agent-platform/src/test/java/ascend/springai/platform/tenant/TenantContextFilterTest.java` | dev/research/prod | X-Tenant-Id validated; dev default on missing |
| idempotency-filter | true | `agent-platform/src/main/java/ascend/springai/platform/idempotency/IdempotencyHeaderFilter.java` | `agent-platform/src/test/java/ascend/springai/platform/idempotency/IdempotencyHeaderFilterTest.java` | dev/research/prod | Idempotency-Key validated; dev accepts missing |
| idempotency-store | false | `agent-platform/src/main/java/ascend/springai/platform/idempotency/IdempotencyStore.java` | `agent-platform/src/test/java/ascend/springai/platform/idempotency/IdempotencyStoreTest.java` | dev (W0); research/prod throws | W0 stub; W1 will add Postgres-backed claimOrFind |
| graphmemory-spi | false | `agent-runtime/src/main/java/ascend/springai/runtime/memory/spi/GraphMemoryRepository.java` (interface) | `agent-runtime/src/test/java/ascend/springai/runtime/memory/spi/MemorySpiArchTest.java` | no runtime path | SPI contract only; no impl; ArchUnit enforces isolation |
| oss-api-probe | true | `agent-runtime/src/main/java/ascend/springai/runtime/probe/OssApiProbe.java` | `agent-runtime/src/test/java/ascend/springai/runtime/probe/OssApiProbeTest.java` | dev | Smoke test: Spring AI + MCP + Temporal + Tika compile |
| run-entity | true | `agent-runtime/src/main/java/ascend/springai/runtime/runs/Run.java` | `agent-runtime/src/test/java/ascend/springai/runtime/runs/RunTest.java` | dev | Run entity with mode (GRAPH\|AGENT_LOOP), parentRunId, parentNodeKey, SUSPENDED status; contract-spine for Rule 11 |
| idempotency-record-entity | true | `agent-runtime/src/main/java/ascend/springai/runtime/idempotency/IdempotencyRecord.java` | `agent-runtime/src/test/java/ascend/springai/runtime/idempotency/IdempotencyRecordTest.java` | dev | IdempotencyRecord entity with mandatory tenantId; contract-spine for Rule 11 |
| orchestration-spi | true | `agent-runtime/src/main/java/ascend/springai/runtime/orchestration/spi/` | `agent-runtime/src/test/java/ascend/springai/runtime/orchestration/spi/` | dev | Orchestrator + GraphExecutor + AgentLoopExecutor + SuspendSignal + Checkpointer + RunContext + ExecutorDefinition SPIs; pure java.* only (ArchUnit enforced) |
| inmemory-orchestrator | true | `agent-runtime/src/main/java/ascend/springai/runtime/orchestration/inmemory/` | `agent-runtime/src/test/java/ascend/springai/runtime/orchestration/NestedDualModeIT.java` | dev | SyncOrchestrator + SequentialGraphExecutor + IterativeAgentLoopExecutor; 3-level graph↔agent-loop nesting proved; dev-posture only |

---

## Designed not shipped — third-reviewer response (2026-05-12)

*Added via third architecture reviewer response. Category-classified: Cat-A (suspend taxonomy), Cat-B (lifecycle DFA), Cat-C (layered SPI), Cat-D (payload codec), Cat-E (context propagation + atomicity). Surfaced 15 hidden defects beyond reviewer's 9 issues. See `docs/reviews/2026-05-12-third-reviewer-response.en.md`.*

| capability | ADR | wave | claim |
|---|---|---|---|
| suspend_reason_taxonomy (upgraded) | ADR-0019 | W2 | Sealed SuspendReason (ChildRun\|AwaitChildren\|AwaitTimer\|AwaitExternal\|AwaitApproval\|RateLimited) + deadline() + JoinPolicy + ChildFailurePolicy |
| parallel_child_dispatch | ADR-0019 | W2 | AwaitChildren fan-out with JoinPolicy (ALL\|ANY\|N_OF) |
| suspend_deadline_watchdog | ADR-0019 | W2 | Sweeper: SUSPENDED → EXPIRED when deadline() elapses |
| **run_status_transition_validator** | ADR-0020 | **W0 (shipped)** | RunStateMachine.validate wired into Run.withStatus/withSuspension; EXPIRED added; RunStateMachineTest |
| run_lifecycle_spi | ADR-0020 | W2 | RunLifecycle interface (cancel/resume/retry) separate from Orchestrator |
| run_state_change_audit_log | ADR-0020 | W2 | run_state_change table: runId, from, to, actor, reason, occurred_at, tenant_id |
| run_optimistic_lock | ADR-0020 | W2 | Run.version field; ConcurrentModificationException on stale write |
| layered_spi_taxonomy | ADR-0021 | W0 (doc) | Layer 1 cross-tier core / Layer 2 tier-specific / Layer 3 tier-internal; W4 bypasses Layer 3 |
| payload_codec_spi | ADR-0022 | W2 | PayloadCodec<T>; EncodedPayload(bytes, codecId, typeRef); RunEvent sealed interface |
| **tenant_propagation_purity** | ADR-0023 | **W0 (shipped)** | TenantPropagationPurityTest ArchUnit; Rule 21; RunContext.tenantId() canonical |
| **logbook_mdc_tenant_id** | ADR-0023 | **W0 (shipped)** | TenantContextFilter MDC.put/remove("tenant_id") in both posture branches |
| suspension_write_atomicity_contract | ADR-0024 | W2 | Tiered: W0 single-thread / W2 @Transactional / W2-Redis outbox / W4 bypass |

---

## Designed not shipped — competitive analysis (2026-05-12)

*Added via competitive analysis vs SAA + AgentScope-Java. See `docs/reviews/2026-05-12-competitive-analysis-and-enhancements.en.md`.*

| capability | code-path | wave | claim |
|---|---|---|---|
| runtime-hook-spi | `agent-runtime/.../action/spi/RuntimeHook.java` (future) | W2 | HookChain at PRE/POST LLM_CALL, TOOL_INVOKE, AGENT_TURN; PII + token counter + summariser + tool-call-limit ref hooks; Rule 19 gate |
| graph-dsl-conformance | `agent-runtime/.../orchestration/spi/ExecutorDefinition.java` (extend) | W3 | StateReducer registry (Overwrite/Append/DeepMerge) + typed Edge with predicate + JSON/Mermaid export; backward-compat factory retained |
| eval-harness-contract | `docs/eval/` (future) | W4 | corpus.jsonl + evaluator.yaml + thresholds.yaml per capability; EvalThresholdGate blocks merge on regression; Rule 18 gate |
| trace-replay-dev-surface | `agent-runtime/.../trace/TraceReplayMcpServer.java` (future) | W4 | MCP tools get_run_trace + list_runs; OTel-driven from trace_store; no Admin UI |
| sandbox-executor-spi | `agent-runtime/.../action/spi/SandboxExecutor.java` (future) | W3 | ActionGuard Bound stage; NoOp default; GraalVM polyglot pluggable; ADR-0018 |
| a2a-federation-strategic | `docs/adr/0016-a2a-federation-strategic-deferral.md` | post-W4 | AgentCard + AgentRegistry + RemoteAgentClient contract surface; registry-binding pluggable |
| multi-backend-checkpointer | `agent-runtime/.../orchestration/inmemory/` (extend) | W2 | Postgres + Redis + file Checkpointer impls behind existing SPI |
| hybrid-rag-bm25 | `agent-runtime/.../memory/` (extend) | W3 | MemoryService L2 + BM25 keyword index + alpha-blended scoring |
| planner-as-tool-pattern | `agent-runtime/.../orchestration/spi/AgentLoopDefinition.java` (extend) | W4 | RunPlanSheet toolset for IterativeAgentLoopExecutor; plan rows in run_memory |

---

## Shipped (fourth-review cycle, 2026-05-12)

*Added via fourth architecture reviewer response. Category-classified: Cat-A (source-of-truth duplication), Cat-B (temporal-mood drift), Cat-C (physical-vs-logical structure), Cat-D (ownership boundary ambiguity), Cat-E (enforcement gaps). Surfaced 44+ hidden defects beyond the 6 reviewer findings. See `docs/reviews/2026-05-12-fourth-reviewer-response.en.md`.*

| capability | ADR | wave | claim |
|---|---|---|---|
| **checkpoint_ownership_boundary** | ADR-0025 | **W0 (doc)** | Executors own `_`-prefixed resume cursor keys via `Checkpointer.save()`; orchestrator owns Run row via `RunRepository.save()`. Two owners, two stores. |
| **module_dependency_direction_w0** | ADR-0026 | **W0 (resolved)** | `agent-runtime/pom.xml` dep on `agent-platform` removed (was speculative — zero source imports). Neither module depends on the other at Maven level. W1 will create `agent-platform-contracts` when a genuine shared type is first needed. |
| **idempotency_scope_w0** | ADR-0027 | **W0 (shipped)** | `IdempotencyHeaderFilter` validates UUID shape on POST/PUT/PATCH only; GET/DELETE/HEAD/OPTIONS bypass. `IdempotencyStore` `@Component` removed — wired in W1. |
| idempotency_store_promotion_to_interface | ADR-0027 | W1 | Wire `IdempotencyStore` with Postgres `idempotency_dedup` table; `claimOrFind` semantics; 409 on replay. |
| micrometer_mandatory_tenant_id_tag | ADR-0023 | W1 | Every `springai_ascend_*` counter/timer/gauge MUST include `.tag("tenant_id", ...)`. Enforced by `MicrometerTenantTagTest` (activate at W1). |
| otel_trace_propagation_across_suspend | ADR-0023 | W2 | W-carrier propagation across `SuspendSignal` boundary; trace context survives checkpoint/resume. |
| agent_platform_contracts_module | ADR-0026 | W1 | `agent-platform-contracts` Maven module: `TenantContext`, `TenantConstants`, `Run`, `RunStatus`, `RunMode`, `RunStateMachine`, `SuspendSignal`, `Checkpointer`, `RunContext` extracted. Both modules depend on contracts; neither on the other. |

---

## Designed not shipped — fifth-reviewer response (2026-05-12)

*Added via fifth architecture reviewer response. Category-classified: Cat-A (data-plane typing + semantic ontology), Cat-B (cognition-action separation), Cat-C (skill SPI lifecycle), Cat-D (bus discipline + channel isolation). Surfaced 29 hidden defects beyond reviewer's 4 named findings. See `docs/reviews/2026-05-12-fifth-reviewer-response.en.md`.*

| capability | ADR | wave | claim |
|---|---|---|---|
| causal_payload_envelope | ADR-0028 | W2 | CausalPayloadEnvelope wraps Payload (ADR-0022) with SemanticOntology tag + SHA-256 fingerprint + byteSize + decayed flag |
| semantic_ontology_tags | ADR-0028 | W2 | SemanticOntology enum {FACT, PLACEHOLDER, HYPOTHESIS, REDACTED}; W2 enforcement via PayloadCodec boundary gate |
| **payload_fingerprint_precommit** | ADR-0028 | **W0 (shipped)** | InMemoryCheckpointer enforces §4 #13 16-KiB cap; dev: WARN; research/prod: ISE. Gate Rule 11 verifies enforcement. |
| cognition_action_separation | ADR-0029 | W2 | Cognition-Action separation principle named; Python-as-mandatory rejected; SkillKind {JAVA_NATIVE, MCP_TOOL, SANDBOXED_CODE_INTERPRETER} taxonomy |
| skill_spi_lifecycle | ADR-0030 | W2 | Skill SPI with init/execute/suspend/teardown + SkillCostReceipt + SkillResumeToken |
| skill_resource_matrix | ADR-0030 | W2 | SkillResourceMatrix(tenantQuotaKey, globalCapacityKey, tokenBudget, wallClockMs, cpuMillis, maxMemoryBytes, concurrencyCap) |
| untrusted_skill_sandbox_mandatory | ADR-0030 | W3 | research/prod + UNTRUSTED SkillTrustTier MUST route through non-NoOp SandboxExecutor; Rule 27 gate |
| three_track_channel_isolation | ADR-0031 | W2 | Control (RunControlSink) + Data (Flux<RunEvent>) + Heartbeat (Flux<Instant>) physical channels; RunDispatcher SPI |
| **iterative_agent_loop_typed_cursor** | ADR-0022 | **W0 (shipped)** | IterativeAgentLoopExecutor throws ISE (ADR-0022 ref) when non-String payload would be silently corrupted by Object.toString() concat (HD-A.8 fix) |

---

## Designed + Governance — post-seventh follow-up (2026-05-13)

*P1/P2 findings from post-seventh L0 readiness follow-up. 6 findings ACCEPTED (all). No rejections. No new Java code; all changes are documentation, gate rules, and ADRs. See `docs/reviews/2026-05-13-post-seventh-l0-readiness-followup-response.en.md`.*

| capability | ADR | wave | claim |
|---|---|---|---|
| w1_http_contract_reconciliation | ADR-0040 | W1 (design) | X-Tenant-Id stays required; W1 adds JWT cross-check; initial status = PENDING; cancel = POST /cancel |
| active_corpus_truth_sweep | ADR-0041 | W0 (gate) | 11 ADR stale plan paths fixed; architecture-systems-engineering-plan.md archived; Gate Rule 15 prevents recurrence |
| contract_catalog_split_tables | — | W0 (doc) | §2 rewritten: 4 sub-tables (SPI/carriers/probes/deferred); OssApiProbe moved to probe table; 3 orchestration SPIs added; Gate Rule 17 |
| http_contract_consistency | ADR-0040 | W0 (doc) | http-api-contracts.md CREATED→PENDING; openapi-v1.yaml x-w1-note DELETE→POST /cancel; agent-platform ARCH replace→cross-check |
| memory_sidecar_consistency | ADR-0034 | W0 (doc) | MANIFEST.md + oss-bill-of-materials.md stripped of mem0/Docling/langchain4j-profile; GraphMemoryRepository JavaDoc updated; Gate Rule 18 |
| module_tree_comment_truth | — | W0 (doc) | IdempotencyHeaderFilter/IdempotencyStore comments fixed; health response format corrected; Testcontainers claim corrected |

---

## Designed — post-seventh second-pass response (2026-05-13)

*8 clusters of peripheral-drift findings. META pattern: central docs cleaned; peripheral entry-points left pointing to deleted/non-existent contracts. §4 #39–#41 + ADR-0042–0044 + Gate Rules 19–23 + widen Rules 17/18. See `docs/reviews/2026-05-13-post-seventh-l0-readiness-second-pass-response.en.md`.*

| capability | ADR | wave | claim |
|---|---|---|---|
| shipped_row_tests_evidence | ADR-0042 | W0 (gate) | Gate Rule 19: every shipped: true row must have non-empty tests:; architecture_sync_gate.tests populated |
| active_normative_doc_catalog | ADR-0043 | W0 (gate) | ACTIVE_NORMATIVE_DOCS + HISTORICAL_EXCLUSIONS canonical sets defined; 8 peripheral violations fixed; Gate Rules 18 (widened), 20, 21, 22, 23 installed |
| spi_contract_precision | ADR-0044 | W0 (doc) | RunContext classified as interface; per-SPI scope table added; ResilienceContract W0 operation-scoped documented; Gate Rule 17 extended |
| memory_metadata_normalization | ADR-0044 | W0 (doc) | embeddingModelVersion canonical across ARCHITECTURE.md + status.yaml; GraphMetadata documented as pre-W2 minimal |

---

## Shipped + Designed — sixth+seventh reviewer combined response (2026-05-13)

*8 clusters from combined review surface. ~50 hidden defects surfaced beyond 12 reviewer-named findings. §4 #29–#36 + ADR-0032–0039 + Gate Rules 12–14 + AppPostureGate shipped. See `docs/reviews/2026-05-13-sixth-reviewer-response.en.md` and `docs/reviews/2026-05-13-seventh-reviewer-response.en.md`.*

| capability | ADR | wave | claim |
|---|---|---|---|
| **posture_single_construction_path** | ADR-0035 | **W0 (shipped)** | AppPostureGate centralizes all APP_POSTURE reads; SyncOrchestrator + InMemoryRunRegistry + InMemoryCheckpointer delegate to it; Gate Rule 12 enforces literal presence |
| **run_find_root_runs** | ADR-0032 | **W0 (shipped)** | RunRepository.findRootRuns(tenantId); InMemoryRunRegistry impl filters parentRunId == null with tenant scoping |
| scope_based_run_hierarchy | ADR-0032 | W2 | RunScope{STEP_LOCAL, SWARM} discriminator; SuspendReason.SwarmDelegation variant; PlanState/RunPlanRef minimal contract |
| logical_identity_equivalence | ADR-0033 | doc | S-Cloud/S-Edge/C-Device deployment-locus vocabulary; Rule 17 S-side/C-side substitution-authority preserved |
| memory_knowledge_taxonomy | ADR-0034 | W2 | 6 categories M1–M6 + common MemoryMetadata schema; Graphiti=W1 ref sidecar; mem0/Cognee=not-selected |
| contract_surface_truth_generalization | ADR-0036 | **W0 (Gate Rules 13+14)** | Gate 13: no deleted SPI names in contract-catalog; Gate 14: method names in ARCHITECTURE.md code-fences must exist in named Java class |
| wave_authority_consolidation | ADR-0037 | **W0 (archived)** | Both stale plan docs archived; ARCHITECTURE.md §1 + YAML + CLAUDE-deferred = single wave authority |
| skill_spi_resource_tiers | ADR-0038 | W2 | 4 enforceability tiers: hard/sandbox-enforceable/advisory/hints; enforcement claims in docs must qualify tier |
| payload_migration_adapter | ADR-0039 | W2 | Single normative path Object→Payload→CausalPayloadEnvelope; PayloadAdapter.wrap(Object); @Deprecated window mandatory |
| run_dispatcher_spi | ADR-0031 | W2 | RunDispatcher SPI; terminal events use reserved slot (never dropped); non-terminal use DROP_OLDEST with counter metric |

---

## Deferred

- Rule 8 gate runs (N≥3 real-LLM sequential runs) and Rule 11 contract-spine fields (`tenant_id` on all
  persistent records) are tracked in [`docs/CLAUDE-deferred.md`](CLAUDE-deferred.md).
- Architecture-level capability status and L-level assignments are tracked in
  [`docs/governance/architecture-status.yaml`](governance/architecture-status.yaml).

---

## Design rationale

Archived pre-refresh docs: `docs/v6-rationale/`

---

## Reading order for new team members

1. `README.md` — project name, status, modules, quick start
2. `docs/STATE.md` — this file; per-capability shipped/deferred table
3. `ARCHITECTURE.md` — system boundary, decision chains, SPI contracts

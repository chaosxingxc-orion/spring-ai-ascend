---
formal_release: true
evidence_bundle: gate/release-ci-evidence/2026-05-25-l0-rc39-formal-release-transaction.evidence.yaml
release_candidate_commit: b5694853a92b095bca3c360a7d7ceaa3c2e1609e
status: formal-release-candidate
---

# v2.0.0-rc39 - formal release transaction closure

> Historical artifact frozen at SHA b5694853a92b095bca3c360a7d7ceaa3c2e1609e. Later corrective waves move live baseline counts; preserve the generated rc39 evidence table as the frozen release record.

## Release Decision

- Decision: ship
- Frozen commit: `b5694853a92b095bca3c360a7d7ceaa3c2e1609e`
- Evidence bundle: `gate/release-ci-evidence/2026-05-25-l0-rc39-formal-release-transaction.evidence.yaml`
- Formal release validator: `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc39-formal-release-transaction.evidence.yaml`

rc39 closes the rc38 post-audit findings and the May 25 release-readiness root
cause analysis with a frozen candidate commit, generated evidence, and a formal
release note. No known blocking L0 architecture defect remains after this
transaction. Forward-only items remain explicitly deferred and must not be
claimed as shipped before their promotion triggers fire.

## Generated Evidence

| Metric | Baseline | Live | Match |
|---|---:|---:|---|
| active_engineering_rules | 42 | 42 | true |
| active_gate_checks | 135 | 135 | true |
| gate_executable_test_cases | 239 | 239 | true |
| enforcer_rows | 168 | 168 | true |
| adr_count | 103 | 103 | true |
| maven_tests_green | 409 | 409 | true |
| architecture_graph_nodes | 471 | 471 | true |
| architecture_graph_edges | 844 | 844 | true |
| recurring_defect_families | 13 | 13 | true |

## Architecture Baseline

| Metric | Count | Evidence |
|---|---:|---|
| §4 constraints | 65 | `architecture-status.yaml` canonical baseline |
| ADRs | 103 | generated evidence baseline/live match |
| gate rules | 135 | generated evidence baseline/live match |
| Gate self-test cases | 239 | `gate/test_architecture_sync_gate.sh` evidence run |
| active engineering rules | 42 | generated evidence baseline/live match |
| active governing principles | 13 | `architecture-status.yaml` canonical baseline |
| enforcer rows | 168 | generated evidence baseline/live match |
| maven_tests_green | 409 | Surefire/Failsafe XML report extraction |
| architecture_graph_nodes | 471 | `python3 gate/build_architecture_graph.py --check --no-write` |
| architecture_graph_edges | 844 | `python3 gate/build_architecture_graph.py --check --no-write` |
| recurring_defect_families | 13 | generated evidence baseline/live match |

## Fixes Completed

1. Run lifecycle writes now have an atomic SPI boundary. `RunRepository.updateIfNotTerminal` is no longer a non-atomic default method; implementations must provide their own conditional update. `InMemoryRunRegistry` remains atomic through per-key `computeIfPresent`, and a production-source guard prevents non-create `RunRepository.save` use outside the repository implementation.
2. Task state persistence no longer has a check-then-put tenant race. `InMemoryTaskStateStore.save` now uses one atomic `compute` operation, with a deterministic race regression covering cross-tenant overwrite attempts.
3. HTTP run contracts now distinguish shipped W1 behavior from W2 replay behavior. Same-key idempotency duplicates return `409 idempotency_conflict`, body drift returns `409 idempotency_body_drift`, and cancel run-owner tenant mismatch returns `404 not_found`; `403 tenant_mismatch` is limited to JWT/header mismatch in the shipped path.
4. Agent engine boundary carriers are immutable and typed where L0 needs contract strength. `AgentInvokeRequest` and `StateDelta` defensively copy nested collections, and `StateDelta` uses a typed `RunStatusTransition` instead of an unbounded string.
5. Runtime-role contract catalog entries now state the real promotion boundary. `StatelessEngine`, `ContextProjector`, and `TaskStateStore` are reference implementations with tests, not fully wired production orchestration paths.
6. Stale Java anchors for S2C and online-evolution SPI packages were corrected, and the gate now blocks known pre-`.spi` anchor patterns in main Java source.
7. The release transaction workflow is now executable: skill, schema, evidence builder, validator, formal release note template, and regression tests were added.
8. Gate self-test and Maven baselines were regenerated from WSL evidence: gate self-tests are 239/239 and Maven Surefire/Failsafe cases are 409.

## Current-vs-Forward Claims

| Subject | Current shipped behavior | Verified by | Forward behavior | Promotion trigger | Must not claim before |
|---|---|---|---|---|---|
| Run lifecycle atomicity | Status mutation goes through an implementation-owned conditional update; in-memory uses per-key atomic remap. | `RunRepositoryAtomicContractTest`, `RunRepositorySaveGuardTest`, `SyncOrchestratorCancelRaceTest`, `./mvnw -Pquality clean verify` | Durable repositories provide equivalent SQL/transactional CAS semantics. | First durable `RunRepository` implementation is introduced. | Durable cancel/complete atomicity exists outside the in-memory implementation. |
| Task state tenant guard | In-memory task state save is atomic for task-id ownership checks. | `InMemoryTaskStateStoreTest` | Durable task store enforces the same invariant with a unique key or transactional CAS. | First non-memory `TaskStateStore` implementation is introduced. | Durable task state isolation is shipped. |
| HTTP tenant mismatch | JWT/header mismatch returns 403; cancel run-owner tenant mismatch returns 404. | `RunHttpContractIT`, `JwtTenantClaimCrossCheckTest`, OpenAPI pinned fixture | A wider 403 policy may be introduced with an explicit audit/logging contract. | ADR-backed contract update plus controller and OpenAPI tests. | Cross-tenant cancel-owner mismatch returns 403 today. |
| Idempotency replay | W1 duplicate creates are explicit 409 conflicts; original-response replay is not shipped. | `RunHttpContractIT`, `docs/contracts/openapi-v1.yaml`, pinned OpenAPI fixture | W2 may return replayed original responses with a durable idempotency ledger. | Durable idempotency ledger and replay contract land together. | Duplicate POST replay is available in W1. |
| Agent engine value boundary | Request/delta records are immutable, typed, and reference-tested; orchestration wiring is deferred. | `AgentInvokeRequestTest`, `StateDeltaTest`, `InMemoryStatelessEngineTest`, `InMemoryContextProjectorTest` | Orchestrator constructs `AgentInvokeRequest` and consumes `StateDelta` through `StatelessEngine`. | First production orchestrator path invokes the SPI. | Agent engine SPI is production-orchestrated. |
| Formal release transaction | Evidence builder, validator, schema, skill, template, and this release note are present. | `gate/test_release_readiness_tools.py`, `bash gate/check_formal_release_transaction.sh --evidence ...` | CI may promote the formal transaction validator into a required release check. | CI workflow includes the formal validator and evidence artifact publication. | CI-enforced formal release publishing exists. |

## Recurring Family Closure

| Family | Cited findings | Sibling surfaces checked | Closure result | Residual risk |
|---|---|---|---|---|
| F-nonatomic-run-status-write | rc38 P1-1, P1-4 | `RunRepository`, `InMemoryRunRegistry`, `SyncOrchestrator`, `RunController`, `TaskStateStore` | closed for in-memory L0; monitoring for future durable stores | Future durable repositories must prove CAS/unique-key semantics before promotion. |
| F-cross-authority-agreement | rc38 P1-2, P1-3, P2-3 | HTTP contract, OpenAPI, pinned fixture, contract catalog, deferred ledger, architecture-status | closed | Future W2 replay or 403 widening must update all authority surfaces atomically. |
| F-shadow-corpus-prose-staleness | rc38 P2-4 | Main Java comments, package anchors, gate source guards | closed | New package moves still need source-pattern guards when they affect authority comments. |
| F-numeric-drift | rc38 P2-1, release-readiness root cause R2 | `architecture-status.yaml`, README/gate README, evidence bundle | closed | Metric changes must be generated from evidence, not hand-authored. |
| F-progressive-loading-weak-enforcement | release-readiness root cause R1, R8 | Formal skill, schema, template, validator, release note | closed for L0 release workflow | The current validator checks scaffold/evidence integrity; semantic completeness still depends on review until CI promotes richer model validation. |

## Authority Refresh

| Surface | Role | Freshness proof |
|---|---|---|
| `docs/governance/architecture-status.yaml` | normative baseline | Evidence bundle baseline/live comparison matches generated metrics. |
| `docs/contracts/http-api-contracts.md` | normative HTTP behavior | Realigned with `RunHttpContractIT` and OpenAPI fixtures. |
| `docs/contracts/openapi-v1.yaml` | normative HTTP schema | Realigned W1 idempotency and cancel tenant semantics. |
| `agent-service/src/test/resources/contracts/openapi-v1-pinned.yaml` | executable pinned schema | Realigned with canonical OpenAPI. |
| `docs/contracts/contract-catalog.md` | contract ledger | Runtime-role entries now separate reference implementations from production wiring. |
| `docs/contracts/agent-invoke-request.v1.yaml` | agent boundary contract | Marked `schema_shipped` with runtime enforcement deferred. |
| `docs/CLAUDE-deferred.md` | deferred authority | W2 replay and future 403 widening no longer overclaim shipped behavior. |
| `docs/governance/recurring-defect-families.yaml` | recurring-family ledger | Non-atomic family updated for broader runtime-state write class. |
| `gate/check_architecture_sync.sh` | canonical gate | Added source guards for stale package anchors and HTTP semantic drift. |
| `gate/release-ci-evidence/2026-05-25-l0-rc39-formal-release-transaction.evidence.yaml` | generated evidence | Generated from frozen commit with `dirty: false`. |

## Four Competitive Pillars

- performance: unchanged on the hot path; atomic runtime guards use existing per-key remap semantics and defensive copies are bounded to boundary records.
- cost: unchanged for L0 runtime; the new checks are test/gate-time controls with no additional shipped infrastructure.
- developer_onboarding: improved because contract status, HTTP semantics, release evidence, and formal publishing commands are now executable and colocated.
- governance: strengthened by generated evidence, recurring-family closure records, and source guards for stale authority anchors.

## Verification Commands

```bash
./mvnw -Pquality clean verify
python3 gate/lib/build_release_evidence.py --run-self-tests --include-maven-reports --output gate/release-ci-evidence/2026-05-25-l0-rc39-formal-release-transaction.evidence.yaml
bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc39-formal-release-transaction.evidence.yaml
bash gate/test_architecture_sync_gate.sh
python3 gate/test_release_readiness_tools.py
```

## Residual Risk

No accepted residual blocks L0 release readiness. The remaining risk is
promotion risk only: durable stores, W2 idempotency replay, wider 403 semantics,
and production orchestration of the agent-engine SPI must each land with their
own ADR-backed contract, implementation tests, and authority refresh.

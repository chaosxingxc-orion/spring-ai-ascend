---
formal_release: true
evidence_bundle: gate/release-ci-evidence/2026-05-25-l0-rc49-agentic-contract-surface-corrective.evidence.yaml
release_candidate_commit: 1d4b3f95fae39088e79524aef53aa1bf308b7be9
status: formal-release-ready
supersedes: docs/logs/releases/2026-05-25-l0-rc48-agentic-contract-surface.en.md
responds_to: docs/logs/reviews/2026-05-25-l0-rc48-agentic-contract-surface-architecture-review.en.md
---

# v2.1.0-rc49 — Agentic Contract Surface Corrective Formal Release

> **Historical artifact frozen at SHA 1d4b3f95fae39088e79524aef53aa1bf308b7be9 (rc49 agentic-contract-surface corrective publication).** Baseline counts in this document (113 ADRs, 33 active SPI interfaces, 565 graph nodes, 1005 graph edges) reflect the rc49 publication baseline and are superseded at rc51 by the L0 Agentic-Completeness wave (120 ADRs, 38 active SPI interfaces, 587 graph nodes, 1065 graph edges). The rc49 baseline remains the canonical record for the rc43-rc49 primitive-tier scope.

> This formal release note is valid only for frozen candidate commit
> `1d4b3f95fae39088e79524aef53aa1bf308b7be9` and evidence bundle
> `gate/release-ci-evidence/2026-05-25-l0-rc49-agentic-contract-surface-corrective.evidence.yaml`.

## Release Decision

- Decision: **ship** the rc49 corrective publication for the L0 agentic contract surface.
- Frozen commit: `1d4b3f95fae39088e79524aef53aa1bf308b7be9`.
- Evidence bundle: `gate/release-ci-evidence/2026-05-25-l0-rc49-agentic-contract-surface-corrective.evidence.yaml`.
- Formal release validator: `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc49-agentic-contract-surface-corrective.evidence.yaml`.
- Four-pillar coverage: performance, cost, developer_onboarding, governance.
- Active SPI interfaces: 33 total (19 pre-rc43 + 14 rc43).
- Architecture graph: 565 nodes / 1005 edges.
- Gate self-tests: 258/258 self-tests.

## Corrective Closure

The rc48 review findings are closed by this release:

| Finding | Closure |
|---|---|
| P0-1 rc48 release evidence placeholder | rc48 is marked superseded; this note binds to a real candidate SHA and generated evidence bundle. Rule 127 rejects future current release notes with placeholder evidence. |
| P0-2 `ModelGateway` ADR/code drift | ADR-0121 now matches `com.huawei.ascend.middleware.model.spi.ModelGateway` and `ModelResponse invoke(ModelInvocation invocation);`. Rule 128 locks ADR, Java, and catalog truth together. |
| P0-3 active SPI count drift | Contract catalog, L1 appendices, baseline prose, and release note use 33 active SPI interfaces total, including 14 rc43 additions. Rule 129 rejects count drift. |
| P1-1 mixed baseline delta | rc49 uses evidence-derived current counts rather than narrative deltas against mixed historical baselines. |
| P1-2 stale deferred design names | The catalog now separates promoted names from deferred names; `Skill`, `SkillRegistry`, `SkillContext`, and `AgentRegistry` are promoted active surfaces. |
| P1-3 hook-chain non-vacuity claim | `LlmGatewayHookChainOnlyTest` now asserts the Spring AI chat gateway remains a design-only shell until W2 hook binding. |
| P1-4 mutable public SPI carriers | Agent, planner, executor, model, skill, retrieval, memory, embedding, and vector carrier records defensively copy public collection and array fields; new tests cover the promoted SPI surfaces. |

## Canonical Baseline

| Metric | Count |
|---|---:|
| §4 constraints | 65 |
| ADRs | 113 |
| Active gate rules | 143 |
| Gate self-test cases | 258 |
| Active engineering rules | 43 |
| Enforcer rows | 176 |
| Architecture graph nodes | 565 |
| Architecture graph edges | 1005 |
| Recurring defect families | 15 |
| Maven tests green | 423 |

## Generated Evidence

| Metric | Baseline | Live | Match |
|---|---:|---:|---|
| active_engineering_rules | 43 | 43 | true |
| active_gate_checks | 143 | 143 | true |
| gate_executable_test_cases | 258 | 258 | true |
| enforcer_rows | 176 | 176 | true |
| adr_count | 113 | 113 | true |
| maven_tests_green | 423 | 423 | true |
| architecture_graph_nodes | 565 | 565 | true |
| architecture_graph_edges | 1005 | 1005 | true |
| recurring_defect_families | 15 | 15 | true |

## Current-vs-Forward Claims

| Subject | Current shipped behavior | Verified by | Forward behavior | Promotion trigger | Must not claim before |
|---|---|---|---|---|---|
| `ModelGateway` SPI | Pure-Java middleware SPI in `com.huawei.ascend.middleware.model.spi` with synchronous `ModelResponse invoke(ModelInvocation invocation);`. | Rule 128; ADR-0121; `gate/lib/check_model_gateway_authority_truth.py`. | Runtime implementation wires Spring AI invocation behind hook dispatch. | W2 LLM gateway wave. | Provider-call path is hook-bound and tested. |
| Spring AI chat gateway | `SpringAiChatModelGateway` is a design-only shell and throws before provider invocation. | `LlmGatewayHookChainOnlyTest`; `./mvnw clean verify`. | Functional Spring AI chat invocation with `BEFORE_LLM` / `AFTER_LLM` hooks. | W2 hook-binding implementation. | The shell can invoke `ChatModel` through hook dispatch. |
| Agentic SPI surface | 33 active SPI interfaces, including 14 rc43 additions. | Rule 129; contract catalog; L1 SPI appendices. | Implementations may be added behind the shipped contracts. | Per-capability W2-W4 implementation waves. | Capability implementation evidence exists. |
| Public SPI carriers | Records defensively copy mutable collection and array inputs. | `SpiCarrierImmutabilityTest`; `PlannerSpiCarrierImmutabilityTest`; `AgentSpiCarrierImmutabilityTest`. | Additional carrier tests follow when new SPI records ship. | New promoted SPI carriers. | Tests cover the new public carrier. |
| Release publication | Current formal release notes bind to a concrete candidate commit and evidence bundle. | Rule 127; formal release validator. | Future release notes use the same formal transaction pattern. | Each release publication. | Evidence bundle is generated from the candidate commit. |

## Recurring Family Closure

| Family | Cited findings | Sibling surfaces checked | Closure result | Residual risk |
|---|---|---|---|---|
| `F-l0-agentic-primitive-gap` | rc48 P0-2, P0-3, P1-2, P1-3, P1-4 | ADR-0121, ADR-0125, contract catalog, L1 appendices, DFX docs, quickstart, tests, gate rules | closed by rc49 corrective release | Runtime implementations remain scheduled by their W2-W4 triggers and are not claimed here. |
| `F-numeric-and-baseline-drift` | rc48 P0-1, P0-3, P1-1 | release notes, `architecture-status.yaml`, README, gate README, enforcers, graph, evidence bundle | closed for this wave by Rules 127 and 129 | Future releases must regenerate evidence from the frozen candidate commit. |

## Authority Refresh

| Surface | Role | Freshness proof |
|---|---|---|
| `docs/adr/0121-model-gateway-spi.yaml` | normative | Rule 128 confirms ADR/package/signature parity. |
| `docs/contracts/contract-catalog.md` | normative | Rule 129 confirms active SPI totals and promoted/deferred names. |
| `agent-service/src/test/java/com/huawei/ascend/service/runtime/architecture/LlmGatewayHookChainOnlyTest.java` | executable evidence | Targeted and full Maven verification passed. |
| `gate/check_architecture_sync.sh` | enforcement | `bash gate/check_architecture_sync.sh` passed on candidate commit. |
| `gate/test_architecture_sync_gate.sh` | enforcement self-test | `258/258` self-tests passed on candidate commit. |
| `gate/release-ci-evidence/2026-05-25-l0-rc49-agentic-contract-surface-corrective.evidence.yaml` | workflow evidence | Generated from clean WSL-native clone at candidate commit. |
| `docs/logs/releases/2026-05-25-l0-rc48-agentic-contract-surface.en.md` | historical | Superseded by this rc49 corrective note. |

## Verification Commands

All commands were driven from WSL.

```bash
./mvnw -pl agent-middleware,agent-execution-engine,agent-service -am -Dtest=SpiCarrierImmutabilityTest,PlannerSpiCarrierImmutabilityTest,AgentSpiCarrierImmutabilityTest,LlmGatewayHookChainOnlyTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw clean verify
./mvnw -Pquality -DskipTests verify
bash gate/test_architecture_sync_gate.sh
bash gate/build_architecture_graph.sh
bash gate/check_architecture_sync.sh
python3 gate/lib/build_release_evidence.py --run-self-tests --include-maven-reports --output gate/release-ci-evidence/2026-05-25-l0-rc49-agentic-contract-surface-corrective.evidence.yaml
bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc49-agentic-contract-surface-corrective.evidence.yaml
```

## Residual Risk

No accepted residual blocks this release. Runtime implementation waves remain explicitly forward-scoped: the Spring AI chat gateway shell must keep throwing until W2 hook binding ships, and the agentic SPI surfaces must not be described as fully implemented until their per-capability implementation evidence exists.

---
formal_release: true
evidence_bundle: gate/release-ci-evidence/2026-05-26-l0-rc54-agentic-composition-corrective.evidence.yaml
release_candidate_commit: 49de909468784ce159121fcf97f83726fc0bff73
status: formal-release-ready
supersedes: docs/logs/releases/2026-05-26-l0-rc52-agentic-completeness-corrective.en.md
responds_to: docs/logs/reviews/2026-05-26-l0-rc53-post-closure-agentic-composition-review.en.md
---

# v2.1.0-rc54 — Agentic Composition Corrective Formal Release

> **Historical artifact frozen at SHA 49de909468784ce159121fcf97f83726fc0bff73** (rc55 W6 freeze — per Gate Rule 28). All numeric baselines in this note (124 ADRs, 606 graph nodes, 1112 graph edges, 20 recurring defect families, etc.) reflect the rc54 state at the frozen candidate commit. The rc55 agent-service L1 canonical-materialization wave bumped these baselines to 130 ADRs / 612 nodes / 1153 edges / 27 families; see `docs/logs/releases/2026-05-26-rc55-agent-service-l1-canonical-materialization.en.md` for the rc55 baselines.

> This formal release note is valid only for frozen candidate commit
> `49de909468784ce159121fcf97f83726fc0bff73` and evidence bundle
> `gate/release-ci-evidence/2026-05-26-l0-rc54-agentic-composition-corrective.evidence.yaml`.

## Release Decision

- Decision: **ship** the rc54 corrective publication for L0 agentic-composition closure.
- Frozen commit: `49de909468784ce159121fcf97f83726fc0bff73`.
- Evidence bundle: `gate/release-ci-evidence/2026-05-26-l0-rc54-agentic-composition-corrective.evidence.yaml`.
- Formal release validator: `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-26-l0-rc54-agentic-composition-corrective.evidence.yaml`.
- Four-pillar coverage: performance, cost, developer_onboarding, governance.
- Active SPI interfaces: 40 total.
- Architecture graph: 606 nodes / 1112 edges.
- Gate self-tests: 260.
- Maven verification: 461 XML-counted tests from WSL-native `./mvnw -Pquality verify`.
- Recurring defect families: 20.

## Baseline Metrics

| Metric | Value |
|---|---:|
| §4 constraints | 65 |
| ADRs | 124 |
| gate rules | 143 |
| Gate self-test cases | 260 |
| Maven tests | 461 |
| architecture graph nodes | 606 |
| architecture graph edges | 1112 |
| recurring defect families | 20 |

## Corrective Closure

The rc53 post-closure review is closed by this release:

| Finding | Closure |
|---|---|
| P0-1 `ChatAdvisor` not bound into `AgentDefinition` | `AgentDefinition` now has `List<AdvisorBinding> advisorBindings`, `Agent` exposes `advisorBindings()`, ADR-0128 / ADR-0132 / contracts / quickstart all document name-based advisor binding. |
| P0-2 schema-less advisor envelopes | `AdvisedRequest` and `AdvisedResponse` now carry typed same-package `AdvisedModelRequest` / `AdvisedModelResponse` payloads; `AdvisedModelEnvelopeAdapter` proves lossless translation outside `.spi`. |
| P1-1 streaming advisor hook ordering | ADR-0129, ADR-0132, `chat-advisor.v1.yaml`, `model-streaming.v1.yaml`, and Javadocs all cite `advisor-model-hook-order/v1`. |
| P1-2 planner/skill invariants in comments only | `Plan`, `PlanStep`, `StepBudget`, `PlanningBudget`, and `SkillDefinition` now enforce constructor-owned semantic invariants with focused tests. |
| P2-1 live placeholder token in rc53 closure note | The rc53 note no longer carries the live `TBD` line-count token, and Rule 127 now blocks current-release/current-response placeholder leaks while allowing historical defect-family citations. |

## Classification And Sweep

Before systemic fixes, the findings were classified into two existing
recurring families: `F-agentic-contract-composition-gap` and
`F-placeholder-leaks-into-active-corpus`. No unrecorded problem type was
found, so no new recurring family was added. The classification and the
repository-wide sweep are recorded in
`docs/logs/reviews/2026-05-26-l0-rc54-agentic-composition-corrective-response.en.md`.

The sweep covered agent/advisor binding claims, raw advisor envelopes,
quickstart accessor truth, streaming hook-order claims, planner/skill carrier
invariants, and current release-note placeholder tokens. Latent instances
were fixed in the same wave rather than left as local follow-up notes.

## SPI Purity Decision

rc54 keeps the strict purity interpretation requested for this wave:
new advisor SPI payloads do not import adjacent model SPI carriers or
framework/provider types. Typed same-package carriers define the public
contract, and translation into model SPI types happens in the non-SPI adapter
package.

## Evidence

| Check | Result |
|---|---|
| Red phase focused tests before implementation | FAIL as expected: missing advisor binding and typed advisor envelope carriers. |
| `./mvnw -pl agent-middleware,agent-execution-engine -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdvisorSpiCarrierImmutabilityTest,AdvisedModelEnvelopeAdapterTest,SkillDefinitionCarrierInvariantTest,PlannerSpiCarrierImmutabilityTest test` | PASS, 18 tests |
| WSL-native `/tmp` copy: `./mvnw -pl agent-service -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentSpiCarrierImmutabilityTest,SpiPurityGeneralizedArchTest test` | PASS, 11 tests |
| WSL-native `/tmp` copy: `./mvnw -Pquality verify` | PASS, 461 XML-counted tests |
| `bash gate/test_architecture_sync_gate.sh` | PASS, 260/260 self-tests |
| `bash gate/build_architecture_graph.sh` | PASS, 606 nodes / 1112 edges |
| `bash gate/check_architecture_sync.sh` | PASS |
| `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-26-l0-rc54-agentic-composition-corrective.evidence.yaml` | PASS |

## Residuals

- Maven quality verification remains WSL-native-ext4 first. Running the full
  quality profile directly from `/mnt/d` can hit DrvFS metadata/copy behavior,
  so formal evidence uses a WSL-native `/tmp` verification copy while every
  script is still launched from WSL.
- PMD reports contain review-trigger maintainability findings only; Rule 121
  treats those as informational, with no SpotBugs high-confidence or
  Checkstyle hard-style blocker.

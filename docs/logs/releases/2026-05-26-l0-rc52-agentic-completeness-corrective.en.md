---
formal_release: true
evidence_bundle: gate/release-ci-evidence/2026-05-26-l0-rc52-agentic-completeness-corrective.evidence.yaml
release_candidate_commit: d263bad31f80f72d919a13066bbbaa31628b8f1d
status: formal-release-ready
supersedes: docs/logs/releases/2026-05-25-l0-rc51-agentic-completeness.en.md
responds_to: docs/logs/reviews/2026-05-25-l0-rc51-agentic-completeness-architecture-review.en.md
---

# v2.1.0-rc52 — Agentic Completeness Corrective Formal Release

> This formal release note is valid only for frozen candidate commit
> `d263bad31f80f72d919a13066bbbaa31628b8f1d` and evidence bundle
> `gate/release-ci-evidence/2026-05-26-l0-rc52-agentic-completeness-corrective.evidence.yaml`.

## Release Decision

- Decision: **ship** the rc52 corrective publication for L0 agentic-completeness closure.
- Frozen commit: `d263bad31f80f72d919a13066bbbaa31628b8f1d`.
- Evidence bundle: `gate/release-ci-evidence/2026-05-26-l0-rc52-agentic-completeness-corrective.evidence.yaml`.
- Formal release validator: `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-26-l0-rc52-agentic-completeness-corrective.evidence.yaml`.
- Four-pillar coverage: performance, cost, developer_onboarding, governance.
- Active SPI interfaces: 40 total.
- Architecture graph: 594 nodes / 1073 edges.
- Gate self-tests: 258.
- Maven verification: 453 XML-counted tests from WSL-native `./mvnw clean verify`.
- Recurring defect families: 16.

## Baseline Metrics

| Metric | Value |
|---|---:|
| §4 constraints | 65 |
| ADRs | 120 |
| gate rules | 143 |
| Gate self-test cases | 258 |
| Maven tests | 453 |
| architecture graph nodes | 594 |
| architecture graph edges | 1073 |
| recurring defect families | 16 |

## Corrective Closure

The rc51 review is closed by this release:

| Finding | Closure |
|---|---|
| P0-1 formal release transaction truth | rc52 uses a candidate-commit-first publication: clean evidence from frozen commit `d263bad31f80f72d919a13066bbbaa31628b8f1d`, then this formal note binds the evidence path and candidate SHA. The validator now rejects dirty evidence, candidate mismatch, and evidence-path mismatch. |
| P0-2 streaming/advisor composition | Added `StreamingChatAdvisor` and `StreamingAdvisorChain` plus same-package `AdvisedStreamChunk`; ADR-0129, ADR-0132, `chat-advisor.v1.yaml`, and `model-streaming.v1.yaml` now agree. |
| P0-3 conversation-memory contradiction | `ConversationMemory` now extends `MemoryStore<String, ConversationWindow>`; `ConversationTurn` is role/content/token/metadata based and no longer imports model `Message`. |
| P0-4 finish reason string drift | `ModelFinishReason` is a closed SPI enum; `ModelResponse.finishReason` uses it and provider-native strings are parsed before SPI entry. |
| P1-1 SPI purity ambiguity | rc52 agent-middleware SPI purity is strict: no cross-SPI dependencies. Same-package carriers replace advisor/model, memory/model, and retrieval/vector coupling. Broader historical non-middleware SPI coupling is documented as a legacy residual, not precedent. |
| P1-2 D-9 gate/text drift | Rule D-9 now distinguishes forbidden change-history metadata from stable `Authority: ADR-NNNN` contract markers; touched production Javadocs were scrubbed of wave-history tags. |
| P1-3 streaming terminal semantics | Successful streams emit exactly one `Complete`; cancellation may close before `Complete`; provider/runtime errors throw mapped exceptions. |

## Classification And Sweep

Before fixing code, the rc51 findings were grouped into four classes:
formal-release transaction truth, cross-contract carrier mismatch,
strict SPI-purity ambiguity, and terminal-stream semantic mismatch.
The new recurring family
`F-agentic-contract-composition-gap` records the common root cause:
individual agentic primitives existed, but their composed developer
contract did not close semantically.

The repository-wide sweep also found and fixed latent retrieval carrier
drift (`Retriever` now returns `RetrievedDocument`) and template/human-view
drift in the recurring-family ledger. It found historical cross-package SPI
relationships outside agent-middleware; those are documented in root
`ARCHITECTURE.md §3.7` for a future repo-wide migration.

## Evidence

| Check | Result |
|---|---|
| `python3 -m unittest gate.test_release_readiness_tools...` focused formal-release tests | PASS, 5 tests |
| `./mvnw -pl agent-middleware ... test` focused SPI/carrier tests | PASS, 28 tests |
| `./mvnw -pl agent-service -am ... SpiPurityGeneralizedArchTest test` in WSL-native ext4 copy | PASS, 6 tests |
| `./mvnw clean verify` in WSL-native ext4 copy | PASS, 453 XML-counted tests |
| `bash gate/build_architecture_graph.sh` | PASS, 594 nodes / 1073 edges |
| `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-26-l0-rc52-agentic-completeness-corrective.evidence.yaml` | PASS |

## Residuals

- The strict no-dependency rule is now enforced for rc52 agent-middleware SPI
  surfaces. Historical non-middleware SPI coupling remains explicitly tracked
  as a legacy residual for a future migration.
- Running Maven directly from `/mnt/d` can fail in WSL because DrvFS rejects
  Maven resource copies; formal verification was therefore driven from a
  WSL-native ext4 copy, while all commands were still launched from WSL.

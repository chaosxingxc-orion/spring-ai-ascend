---
review_kind: corrective-response
target_review: docs/logs/reviews/2026-05-25-l0-rc51-agentic-completeness-architecture-review.en.md
target_release: docs/logs/releases/2026-05-25-l0-rc51-agentic-completeness.en.md
affects_level: L0
affects_view: development, logical, process
affects_artefact: [ARCHITECTURE.md, CLAUDE.md, README.md, agent-middleware/ARCHITECTURE.md, agent-middleware/module-metadata.yaml, docs/contracts/contract-catalog.md, docs/contracts/chat-advisor.v1.yaml, docs/contracts/memory-store.v1.yaml, docs/contracts/model-invocation.v1.yaml, docs/contracts/model-streaming.v1.yaml, docs/contracts/vector-store.v1.yaml, docs/adr/0124-vector-retrieval-embedding-spi.yaml, docs/adr/0129-streaming-aware-model-gateway.yaml, docs/adr/0132-chat-advisor-spi.yaml, docs/adr/0133-conversation-memory-spi-variant.yaml, docs/adr/0134-tool-call-iteration-loop.yaml, docs/governance/architecture-status.yaml, docs/governance/recurring-defect-families.yaml, docs/governance/recurring-defect-families.md, docs/governance/rules/rule-D-9.md, gate/lib/check_formal_release_transaction.py]
verdict: close-after-formal-release-evidence
related_rules: [Rule D-9, Rule R-D, Rule G-2, Rule G-8, Rule G-9]
related_family: F-agentic-contract-composition-gap
---

# rc52 Agentic-Completeness Corrective Response

## Classification before fixes

The rc51 review findings were grouped before code edits so the corrective
scope could be scanned beyond the cited line numbers:

| Class | rc51 findings | Defect family decision |
|---|---|---|
| Formal release transaction truth | P0-1 | Existing F-progressive-loading-weak-enforcement mechanism, plus new composition-family occurrence because evidence did not compose with latest-release front matter. |
| Cross-contract carrier mismatch | P0-2, P0-3, P0-4 | New family `F-agentic-contract-composition-gap`: the individual L0 primitives existed but their adjacent semantics did not compose. |
| SPI purity ambiguity | P1-1 | Same new family; strict same-package carrier purity is required for the rc52 agent-middleware surfaces. |
| Kernel-vs-gate wording drift | P1-2 | Existing `F-kernel-vs-implementation-drift`; repaired by aligning D-9 text with the executable gate and scrubbing release-history tags from production Javadocs. |
| Streaming terminal semantics drift | P1-3 | Same new composition family; model streaming success, cancellation, and provider-error paths are now distinct. |
| Governance overclaim / latent drift | P2-1, P2-2 | Existing numeric/path drift families; rc52 evidence and graph rebuild close the current release surface. |

`F-agentic-contract-composition-gap` is now recorded in both
`docs/governance/recurring-defect-families.yaml` and its rendered human view.

## Repository-wide sweep

The same issue classes were scanned across the current repository:

- `agent-middleware..spi..` cross-package imports: fixed to zero by replacing
  advisor, memory, and retrieval cross-SPI carriers with same-package carriers.
- Broader non-middleware SPI imports: found historical residuals in
  `agent-bus`, `agent-execution-engine`, `agent-service.agent.spi`, and
  `RunRepository`. These are now explicitly documented in root
  `ARCHITECTURE.md §3.7` as legacy residuals, not precedent for new SPI design.
- Model finish reason drift: fixed with `ModelFinishReason` and strict
  provider-string parsing before SPI entry.
- Conversation-memory generic drift: fixed by making `ConversationMemory`
  extend `MemoryStore<String, ConversationWindow>`.
- Retriever/vector carrier drift: fixed by introducing `RetrievedDocument`
  and updating ADR-0124 plus `vector-store.v1.yaml`.
- D-9 drift: production `Wave ...` / narrative `per ADR-...` metadata in the
  touched Java surfaces was scrubbed; stable `Authority: ADR-NNNN` markers
  remain allowed as public contract source identifiers.
- Formal release transaction drift: validator tests now reject dirty evidence,
  candidate SHA mismatch, non-formal latest release evidence, and evidence path
  mismatch.

## Corrective decisions

- Use Option B for streaming advisors: keep `ChatAdvisor` for non-streaming
  calls and add explicit `StreamingChatAdvisor` / `StreamingAdvisorChain`
  sibling SPIs with same-package `AdvisedStreamChunk`.
- Interpret rc52 agent-middleware SPI purity as no cross-SPI dependencies.
  Adapter layers translate between package-specific carriers.
- Treat formal release as candidate-commit-first: the rc52 release note and
  evidence bundle must agree on the frozen candidate commit and evidence path.

## Validation status

- `python3 -m unittest gate.test_release_readiness_tools.ReleaseReadinessToolTests.test_validator_rejects_dirty_formal_release_evidence ... test_validator_rejects_evidence_bundle_path_mismatch` — PASS, 4 tests.
- `./mvnw -pl agent-middleware -DskipITs -Dtest=AdvisorSpiCarrierImmutabilityTest,ConversationMemoryCarrierImmutabilityTest,ModelFinishReasonTest,ModelStreamingChunkCarrierImmutabilityTest,SpiCarrierImmutabilityTest test` — PASS, 28 tests.
- WSL-native `/tmp` verification copy: `./mvnw -pl agent-service -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SpiPurityGeneralizedArchTest test` — PASS, 6 tests.
- WSL-native `/tmp` verification copy: `./mvnw clean verify` — PASS, 453 XML-counted Maven tests.
- `bash gate/build_architecture_graph.sh` — PASS, 594 nodes / 1073 edges.

Final closure is the rc52 formal release note plus evidence bundle generated
from the frozen candidate commit.

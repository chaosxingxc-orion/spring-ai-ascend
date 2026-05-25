---
affects_level: L0
affects_view: development
proposal_status: response
authors: ["spring-ai-ascend architecture team"]
responds_to: docs/logs/reviews/2026-05-25-l0-rc48-agentic-contract-surface-architecture-review.en.md
related_adrs:
  - ADR-0121
  - ADR-0125
related_rules:
  - Rule 43
  - Rule 127
  - Rule 128
  - Rule 129
affects_artefact:
  - ARCHITECTURE.md
  - agent-middleware/ARCHITECTURE.md
  - agent-service/ARCHITECTURE.md
  - docs/governance/templates/root-architecture.md.j2
  - docs/governance/templates/agent-middleware-architecture.md.j2
  - docs/governance/templates/agent-service-architecture.md.j2
  - docs/contracts/contract-catalog.md
  - docs/governance/templates/contract-catalog.md.j2
  - docs/governance/architecture-status.yaml
  - docs/governance/enforcers.yaml
  - docs/governance/recurring-defect-families.md
  - docs/governance/recurring-defect-families.yaml
  - docs/governance/templates/recurring-defect-families.md.j2
  - gate/check_architecture_sync.sh
  - gate/test_architecture_sync_gate.sh
  - gate/README.md
  - gate/historical-release-grandfathered.txt
---

# Response — L0 rc49 Agentic Contract Surface Corrective Review

## Executive decision

All findings in the rc48 agentic contract surface architecture review are accepted. The corrective release ships as rc49 and supersedes the rc48 publication note where that note carried pending evidence placeholders, stale SPI counts, and a hook-chain closure claim that was not aligned with the actual Spring AI adapter shell.

The rc49 scope is deliberately limited to the reviewed agentic contract surface. Nodegraph and codegraph remediation remain out of scope for this wave.

## Finding closure

| Finding | rc49 closure |
|---|---|
| P0-1 rc48 release evidence placeholder | rc48 is marked superseded; rc49 introduces Rule 127 (`release_note_no_pending_evidence`) so current formal release notes cannot carry placeholder candidate commits or evidence bundles. |
| P0-2 ADR-0121 `ModelGateway` authority drift | ADR-0121 now matches the shipped pure-Java SPI: `com.huawei.ascend.middleware.model.spi.ModelGateway` with `ModelResponse invoke(ModelInvocation invocation);`. Rule 128 prevents future ADR/code/catalog divergence. |
| P0-3 active SPI count drift | The contract catalog and architecture baselines use `33 total` active SPI interfaces, including the 14 rc43 additions (`MemoryReader` and `MemoryWriter` included). Rule 129 prevents module-total and release-note drift. |
| P1-1 ambiguous baseline delta | rc49 publishes from a concrete candidate commit and evidence bundle instead of mixing historical baselines with current counts. |
| P1-2 stale deferred design-name table | The contract catalog now distinguishes promoted names from still-deferred names so `Skill`, `SkillRegistry`, `SkillContext`, and `AgentRegistry` are no longer presented as deferred. |
| P1-3 hook-chain non-vacuity claim | `LlmGatewayHookChainOnlyTest` now asserts the current Spring AI adapter shell remains design-only and throws before any provider call until W2 hook binding ships. |
| P1-4 mutable public SPI carriers | Agent, planner, executor, model, skill, retrieval, memory, embedding, and vector carriers now defensively copy public collection and array fields; new immutability tests cover the promoted SPI surfaces. |

## Freeze authorization

This response authorizes the rc49 edits to frozen L0/L1 architecture artefacts listed in `affects_artefact`. The changes are truth-alignment updates only: root/module architecture files and their templates now reflect the same hook-chain staging, SPI counts, and publication baselines as the ADR/catalog/gate authority surfaces.

## Prevention rules

| Rule | Slug | Purpose |
|---|---|---|
| 127 | `release_note_no_pending_evidence` | Rejects current release notes with placeholder formal evidence or invalid candidate commit IDs. |
| 128 | `model_gateway_authority_truth` | Keeps ADR-0121, Java SPI, and contract catalog aligned on the `ModelGateway` package and signature. |
| 129 | `contract_spi_count_truth` | Keeps active SPI totals and promoted/deferred catalog rows aligned with current source truth. |

## Verification plan

- `bash gate/test_architecture_sync_gate.sh`
- `bash gate/build_architecture_graph.sh`
- `./mvnw clean verify` from a WSL-native temporary checkout
- `./mvnw -Pquality -DskipTests verify` from a WSL-native temporary checkout
- `bash gate/check_architecture_sync.sh`
- `python3 gate/lib/build_release_evidence.py --run-self-tests --include-maven-reports --output gate/release-ci-evidence/2026-05-25-l0-rc49-agentic-contract-surface-corrective.evidence.yaml`
- `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc49-agentic-contract-surface-corrective.evidence.yaml`

---
status: l0-agentic-contract-surface-shipped
release_candidate_commit: pending-formal-validator-run
re_scopes_release: rc41-final-release-readiness
superseded_by: rc49-agentic-contract-surface-corrective
authority_adrs:
  - ADR-0120
  - ADR-0121
  - ADR-0122
  - ADR-0123
  - ADR-0124
  - ADR-0125
  - ADR-0126
  - ADR-0127
  - ADR-0128
phases_executed:
  - "Phase A — Strategic Foundation (rc43): ADR-0120 brand+KEEP; ADR-0125 Spring AI canonical; ADR-0122 Tool-Skill resolution"
  - "Phase B — Contract Surface SPIs (rc44-46): 6 SPI shape waves, 12 interfaces, 7 contract YAMLs"
  - "Phase C — Integration & Discoverability (rc47): 5 Spring AI adapter shells + Agent factory + quickstart preview + L1 SPI Appendix refresh"
  - "Phase D — Governance Lockstep & Re-Close (rc48): family registration + baseline lockstep + release transaction"
---

# v2.1.0 — L0 Agentic Contract Surface (rc43-rc48 consolidated)

> **Historical artifact frozen at SHA 8b4f244f628cd142075e3593825086b8474e230d (rc48 agentic-contract-surface pre-corrective note).** Baseline counts in this document reflect the rc48 publication draft and are superseded by rc49.

> **Superseded by rc49.** This rc48 note is retained as the review target
> that surfaced the corrective findings. Its pending evidence placeholders,
> 31-total / 12-interface count, and "HookChain guard no longer vacuous"
> claim are not current release authority. The corrective publication is
> `docs/logs/releases/2026-05-25-l0-rc49-agentic-contract-surface-corrective.en.md`.

> **rc48 supersedes the rc41 v2.0.0 framing.** rc41 (commit `d20d1e3`)
> remains the historical record of "Runtime Kernel + Governance
> Skeleton GA" — a real deliverable shipped at that commit. rc48 closes
> the L0-level contract-shape gap that an independent senior-architect
> review (docs/logs/reviews/2026-05-25-l0-senior-architect-reopen-recommendation.en.md)
> identified at rc41, advancing the L0 baseline from
> "structural skeleton + governance" to "full agent-platform contract
> layer" by landing the 7 missing primitive SPIs as design_only
> contracts.

## Release Decision

- Decision: **close the L0 Agentic Contract Surface gap** identified at
  rc41 closure; advance L0 to "agent-platform contract layer complete".
- Source: see ADR-0120 §decision + the rc43→rc48 wave-execution audit
  trail.
- Formal-release evidence bundle: TO BE GENERATED. The team runs
  `bash gate/check_formal_release_transaction.sh --evidence
  gate/release-ci-evidence/2026-05-25-l0-rc48-agentic-contract-surface.evidence.yaml`
  after generating the evidence bundle from current architecture-status
  baselines (see Verification Commands below).

## Architecture Baseline (rc48 — single source `architecture-status.yaml`)

| Metric | Count | Δ vs rc41 |
|---|---:|---:|
| §4 constraints | 65 | 0 |
| ADRs | 113 | +9 (ADR-0120..0128) |
| Active gate rules | 140 | 0 (existing R-D / G-1.1.b enforcers auto-validate new SPIs) |
| Active engineering rules | 43 | 0 |
| Gate self-test cases | 252 | 0 |
| Enforcer rows | 173 | 0 |
| Architecture graph nodes | 562 | +84 (ADRs + SPIs + contracts + new family) |
| Architecture graph edges | 999 | +134 (relates_to + principle-coverage + SPI ownership + contract-citing) |
| Recurring defect families | 15 | +1 (F-l0-agentic-primitive-gap, closed_rc43) |
| Maven tests green | 409 | 0 (Wave C1 ships design-only adapter shells — no functional tests added) |

## What Shipped at rc48

### Strategic decisions (Phase A)

| ADR | Title | Decision |
|---|---|---|
| ADR-0120 | Brand & Audience B alignment | **KEEP** `spring-ai-ascend` brand + ARCHITECTURE.md §1.1 Audience B promise; execute the rc43→rc48 contract surface wave plan |
| ADR-0122 | Tool vs Skill semantic resolution | **Path b** — `Tool` is a `SkillKind` enum value; one unified `Skill` SPI; `SkillCapacityRegistry` arbitrates both |
| ADR-0125 | Spring AI integration boundary | **Path a** — Spring AI is the canonical Model / Tool / Vector / Embedding / Retrieval abstraction; platform SPIs are thin decorators adding tenant scoping + hook binding + capacity arbitration + trace propagation |

### Contract surface SPIs (Phase B)

12 new Java SPI interfaces under correct semantic-home modules
(`Agent` → agent-service [HTTP-edge customer registration surface];
`Planner` → agent-execution-engine [engine-side plan generator];
all others → agent-middleware [cross-cutting middleware concerns]):

| Module | SPI count | Δ vs rc41 |
|---|---:|---:|
| `agent-service` | 9 | +2 (`Agent`, `AgentRegistry`) |
| `agent-execution-engine` | 7 | +1 (`Planner`) |
| `agent-middleware` | 10 | +9 (`ModelGateway`, `Skill`, `SkillRegistry`, `MemoryStore`, `SemanticMemoryStore`, `KnowledgeMemoryStore`, `VectorStore`, `Retriever`, `EmbeddingModel`) |
| `agent-bus` | 4 | 0 |
| `agent-evolve` | 1 | 0 |
| **Total** | **31** | **+12** |

7 new design_only contract YAMLs registered in
`docs/contracts/contract-catalog.md`:

- `agent-definition.v1.yaml` (ADR-0128)
- `model-invocation.v1.yaml` (ADR-0121)
- `skill-definition.v1.yaml` (ADR-0127)
- `memory-store.v1.yaml` (ADR-0123)
- `vector-store.v1.yaml` (ADR-0124)
- `planning-request.v1.yaml` (ADR-0126)
- `plan.v1.yaml` (ADR-0126)

### Integration & discoverability (Phase C)

5 Spring AI reference adapter shells under
`agent-service/src/main/java/com/huawei/ascend/service/integration/springai/`
— first production-side Spring AI imports in the codebase. Shells throw
`UnsupportedOperationException` at L0; W2 LLM gateway / W3 RAG vertical
wire the functional implementations behind the platform's hook +
capacity machinery:

- `SpringAiChatModelGateway` (wraps Spring AI `ChatModel`)
- `SpringAiVectorStoreAdapter` (wraps Spring AI `VectorStore`)
- `SpringAiEmbeddingModelAdapter` (wraps Spring AI `EmbeddingModel`)
- `SpringAiDocumentRetrieverAdapter` (composes platform `VectorStore`)
- `SpringAiToolCallbackSkillAdapter` (adapts Spring AI tool callback)

Plus `AgentExecutorDefinitionFactory` (Agent → ExecutorDefinition
bridge; W3 SDK GA wires translation).

The `LlmGatewayHookChainOnlyTest` ArchUnit guard
(`agent-service/src/test/java/.../runtime/architecture/`) — staged
vacuous since rc1 — arms automatically when the W1 LLM package
directory appears.

### Governance lockstep (Phase D)

- New recurring-defect family `F-l0-agentic-primitive-gap` registered
  in `docs/governance/recurring-defect-families.yaml` and `.md`
  (cleanup_status: `closed_rc43`).
- `architecture-status.yaml#baseline_metrics` updated with new
  adr_count, recurring_defect_families, architecture_graph_nodes,
  architecture_graph_edges.
- README.md baseline phrase updated to match.
- L1 ARCHITECTURE.md §SPI Appendix refreshed for all three modules.
- `docs/contracts/contract-catalog.md` Active SPI table refreshed
  (19 → 31 total).
- DFX docs refreshed for `agent-middleware`, `agent-service`,
  `agent-execution-engine`.
- Quickstart §4.5 adds an L0 Agentic Contract Surface preview showing
  the customer registration pattern.

## Current Agentic Architecture Decision

| Capability | rc48 contract surface | Implementation maturity |
|---|---|---|
| Agent (first-class) | `Agent` SPI in `agent-service.agent.spi`; `AgentDefinition` + `AgentRegistry`; `agent-definition.v1.yaml` design_only | impl deferred to W3 SDK GA |
| Model invocation | `ModelGateway` SPI in `agent-middleware.model.spi`; `model-invocation.v1.yaml` design_only | impl deferred to W2 LLM gateway |
| Skill / Tool | `Skill` SPI in `agent-middleware.skill.spi` with `SkillKind` discriminator; `skill-definition.v1.yaml` design_only | impl deferred to W2 skill registry |
| Memory (M1-M6) | `MemoryStore<K, V>` + `SemanticMemoryStore` (M3) + `KnowledgeMemoryStore` (M5) in `agent-middleware.memory.spi`; `memory-store.v1.yaml` design_only | impl deferred to W2 memory adapters |
| Vector / Retrieval / Embedding | `VectorStore` + `Retriever` + `EmbeddingModel` in `agent-middleware.{vector,retrieval,embedding}.spi`; `vector-store.v1.yaml` design_only | impl deferred to W3 RAG vertical |
| Planner | `Planner` SPI in `agent-execution-engine.planner.spi`; `planning-request.v1.yaml` + `plan.v1.yaml` design_only | impl deferred to W4 planner |
| Spring AI integration | Canonical per ADR-0125; 5 reference adapter shells under `agent-service.integration.springai` | shells design_only; W2/W3 wire functional bindings |

## Contract, Authority, and Constraint Closure

| Surface | Closure check | Result |
|---|---|---|
| 9 new ADRs (`docs/adr/0120-0128`) | All accepted; all relates_to existing rc1-rc42 ADRs | closed |
| 12 new SPI interfaces | Compile under `agent-{middleware,service,execution-engine}` per Rule R-D.d (java.* + same-spi-package siblings only); package-info.java documents the L0 vs W2-W4 boundary | closed |
| 7 new contract YAMLs | Listed in `docs/contracts/contract-catalog.md`; each cites its authority ADR per Rule M-2.b | closed |
| `module-metadata.yaml` parity | agent-middleware (+9 packages), agent-service (+1), agent-execution-engine (+1) spi_packages lists updated | closed |
| DFX docs parity (Rule R-D.e) | agent-middleware / agent-service / agent-execution-engine DFX `spi_packages` lists updated | closed |
| L1 SPI Appendix parity (Rule G-1.1.b) | All three module ARCHITECTURE.md §SPI Appendix tables refreshed | closed |
| Contract catalog parity (Rule R-D.f / .g) | 19 → 31 Active SPI rows + 9 new tenant-scope rows + new structural-carrier rows | closed |
| Recurring-defect ledger (Rule G-9) | `F-l0-agentic-primitive-gap` registered with prevention_rules + cleanup_status: `closed_rc43`; yaml + md parity per Rule G-9.c | closed |
| Baseline lockstep (Rule G-2.b) | architecture-status.yaml + README.md updated in same wave | closed |
| Spring AI brand-vs-shipped (memo §3) | 5 reference adapter shells under `service.integration.springai`; first production Spring AI imports in the codebase; `LlmGatewayHookChainOnlyTest` no longer vacuous | closed (shells) — W2/W3 closes implementation |

## Over-Design Assessment

The rc43→rc48 wave adds 12 SPI interfaces, 7 contract YAMLs, 9 ADRs,
and 1 recurring-family entry. No new gate rules were added — existing
Rule R-D iteration over `module-metadata.yaml#spi_packages` /
`contract-catalog.md` / `docs/dfx/*.yaml` auto-validates the additions,
and Rule G-1.1.b iteration over L1 SPI Appendix tables catches future
SPI additions automatically. The 12 SPIs are contract-shape only —
implementations are W2-W4 staged with explicit promotion triggers per
the ADRs.

The Spring AI reference adapter shells contain only the constructor
boundary + `UnsupportedOperationException` throws + Javadoc on the W2
implementation plan. No premature implementation; no half-built
runtime paths. Audience B has the extension seams to extend; W2/W3
land the functional bindings behind the platform's hook + capacity
machinery.

## Four Competitive Pillars

- **performance**: no runtime hot-path change at L0 (contract-shape
  only); the W2 LLM gateway wave wires Reactive `WebClient` /
  Virtual-Thread-backed clients per Rule R-G.
- **cost**: no new runtime infrastructure or storage service added
  at L0; Spring AI BoM already imported pre-rc43, the rc48 adapter
  shells add no runtime dependencies.
- **developer_onboarding**: quickstart §4.5 adds the L0 contract
  preview showing the `AgentDefinition` + bindings pattern; Audience
  B (external Spring developers) now has 12 extension seams to
  extend; Rule R-A "Business/Platform Decoupling" is non-vacuously
  satisfied.
- **governance**: 1 new recurring-defect family registered and
  closed_rc43 by construction; baseline lockstep across
  architecture-status.yaml + README + recurring-defect-families
  (.yaml/.md) per Rule G-9; ADR-0120 records the META-lesson that
  scope-conflation between "structural skeleton" and "agent-platform
  contract layer" must be flagged at every L0 final-release-readiness
  review.

## Recurring Family Closure

| Family | Closure result | Residual risk |
|---|---|---|
| F-l0-agentic-primitive-gap | registered + `closed_rc43` by construction (12 SPIs + 7 contracts + 9 ADRs + 4-way parity hold) | implementations W2-W4 staged; future L0-level primitives MUST follow same "contract shape at L0 even when impl defers" pattern |
| F-numeric-drift | rc43 lockstep applied (architecture-status.yaml + README updated to 113 / 562 / 999 / 15 in same wave per Rule G-9) | continues partial — Rule G-13 forward-prevention link (rc42 W0) closes by construction in W3..W10 |
| F-cross-authority-agreement | rc43 cross-authority parity validated at 8 surfaces (catalog ↔ metadata ↔ DFX ↔ L1 SPI Appendix ↔ ADRs ↔ recurring-families) | continues structurally addressed |

(Other 12 families: no rc43 occurrence; status unchanged from rc42 W0
ledger.)

## Verification Commands

```bash
# Re-run gate against rc43 changes (Linux/WSL per Rule G-7).
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && bash gate/check_parallel.sh'

# Re-run architecture graph build (idempotent per Rule G-1.b):
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && python3 gate/build_architecture_graph.py --check'

# Run Maven verify (incl. ArchUnit SpiPurityGeneralizedArchTest which
# now validates 12 additional SPI packages):
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && ./mvnw -T 1C clean verify'

# Run gate self-tests:
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && bash gate/test_architecture_sync_gate.sh'

# (Once formal-release evidence bundle is generated:)
# bash gate/check_formal_release_transaction.sh --evidence \
#   gate/release-ci-evidence/2026-05-25-l0-rc48-agentic-contract-surface.evidence.yaml
```

## Cross-references

- Originating review: `docs/logs/reviews/2026-05-25-l0-senior-architect-reopen-recommendation.en.md`
- Wave plan: `.claude/plans/d-chao-workspace-spring-ai-ascend-docs-eager-harp.md`
- Re-scoped release: `docs/logs/releases/2026-05-25-l0-rc41-final-release-readiness.en.md`
- Strategic-decision ADRs: ADR-0120, ADR-0122, ADR-0125
- SPI shape ADRs: ADR-0121, ADR-0123, ADR-0124, ADR-0126, ADR-0127, ADR-0128
- Recurring family: `docs/governance/recurring-defect-families.{yaml,md}` §F-l0-agentic-primitive-gap

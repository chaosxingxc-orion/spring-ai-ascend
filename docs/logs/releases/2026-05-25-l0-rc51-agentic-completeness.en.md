---
status: l0-agentic-completeness-shipped
release_candidate_commit: 3b2986cdd14c7b5d9988f17f649558cb254d3df5
re_scopes_release: rc50-codegraph-nodegraph-supplement
responds_to: docs/logs/reviews/2026-05-25-l0-rc50-post-closure-senior-architect-review.en.md
authority_adrs:
  - ADR-0129
  - ADR-0130
  - ADR-0131
  - ADR-0132
  - ADR-0133
  - ADR-0134
  - ADR-0135
waves_executed:
  - "Wave E1 — Model Surface Completeness (closes P0-1, P0-4): ADR-0129 streaming-aware ModelGateway; ADR-0130 StructuredOutputConverter<T>"
  - "Wave E2 — Prompt Layer (closes P0-2): ADR-0131 PromptTemplate SPI"
  - "Wave E3 — Advisor Interceptor Layer (closes P0-3): ADR-0132 ChatAdvisor + AdvisorChain SPI"
  - "Wave E4 — Memory & Tool-Loop Semantics (closes P1-1, P1-2): ADR-0133 ConversationMemory variant; ADR-0134 Tool-Call Iteration Loop semantics"
  - "Wave E5 — Documentation & Forward-Looking Capture (closes P2-1, P2-2): ADR-0135 AgentSession-as-Run-projection deliberate-non-SPI capture"
  - "Wave G2 — AST-aware refactor tooling (closes OE-4): JavaParser + libCST wrappers under gate/lib/refactor/"
  - "Wave G1 — Governance Pruning (closes OE-1, OE-2, OE-3): documentation-only at rc51; the W3..W10 ADR-0119 retirement schedule cannot execute now (depends on Rule G-13 landing). Roadmap captured in the §Wave G1 deferred-execution section below."
---

# v2.1.0 — L0 Agentic-Completeness wave (rc51)

> **Historical artifact frozen at SHA 165e0b69e1ec1f95a91d1eccfd2389ac6ab3c1f5 (rc51 L0 Agentic-Completeness wave).** Baseline counts in this document (e.g. `120 ADRs`, `594 nodes / 1073 edges`, `16 recurring defect families`) reflect state at rc51 publication time and are NOT retroactively updated per the logs-folder snapshot-evidence policy (`docs/governance/logs-folder-policy.md`). Canonical baseline lives in `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`.

> Re-scopes rc50 (CodeGraph Nodegraph Supplement) by closing the
> developer-ergonomics tier residual of the rc41/rc43
> F-l0-agentic-primitive-gap family that the rc50 post-closure
> senior-architect review (see `docs/logs/reviews/2026-05-25-l0-rc50-post-closure-senior-architect-review.en.md`)
> surfaced. rc50 remains the historical CodeGraph supplemental note;
> rc51 advances the L0 baseline from "agent-platform primitive
> contract layer" to "agent-platform primitive + developer-
> ergonomics contract layer".

## Release Decision

- Decision: **ship** the rc51 L0 Agentic-Completeness wave; advance the L0
  Agentic Contract Surface from 33 active SPI interfaces (rc43–rc49
  primitive tier) to 38 active SPI interfaces (rc51 ergonomics tier
  added) and from 113 ADRs to 120 ADRs.
- Frozen commit: **3b2986cdd14c7b5d9988f17f649558cb254d3df5** (a follow-on rc52
  corrective will bind the formal-release-transaction evidence bundle
  generated from this candidate commit).
- Formal release validator command (to be run on the candidate commit):
  `bash gate/check_formal_release_transaction.sh --evidence
  gate/release-ci-evidence/2026-05-25-l0-rc51-agentic-completeness.evidence.yaml`.
- Four-pillar coverage: performance, cost, developer_onboarding, governance.

## What rc48–rc50 got right (preserved unchanged at rc51)

- ADR-0125 Spring AI canonical decision: thin-decorator boundary remains
  the strategic anchor.
- ADR-0122 / ADR-0127 SkillKind unification: rc51 does not split the
  unified `Skill` SPI.
- ADR-0123 Memory M1–M6 taxonomy: rc51 adds `ConversationMemory` as a
  MemoryStore *variant*, not a parallel hierarchy.
- ADR-0126 DAG-shaped `Plan`: rc51 adds tool-call iteration loop
  semantics that explicitly accommodate both agent-driven and
  planner-driven modes (ADR-0134).
- ADR-0128 Agent first-class entity: rc51 keeps `Agent` as the only
  agent-tier identity, and ADR-0135 records the deliberate decision NOT
  to add a separate `AgentSession` SPI.
- 5 Spring AI reference adapter shells from rc43 remain design-only;
  rc51 adds 2 more (`SpringAiBeanOutputConverterAdapter`,
  `SpringAiPromptTemplateAdapter`) under the same package and same
  design-only discipline.

## What rc51 closes (4 P0 + 2 P1 + 2 P2 + 1 OE finding)

| Finding | Closed by |
|---|---|
| **P0-1** `ModelGateway` has no streaming surface | Wave E1 — `default Stream<ModelResponseChunk> stream(ModelInvocation)` added per ADR-0129. SPI purity preserved via `java.util.stream.Stream`, NOT Reactor `Flux`. Default throws `UnsupportedOperationException`; W2 LLM gateway wires Spring AI `ChatModel.stream(...)` behind virtual-thread isolation. |
| **P0-2** No `PromptTemplate` SPI | Wave E2 — `PromptTemplate` interface + sealed `PromptTemplateSource` (InlineString \| ClasspathResource) + `RenderedPrompt` record + `PlaceholderSyntax` enum + `SpringAiPromptTemplateAdapter` shell per ADR-0131. |
| **P0-3** No `ChatAdvisor` interceptor SPI | Wave E3 — `ChatAdvisor` + `AdvisorChain` interfaces + `AdvisedRequest` + `AdvisedResponse` records per ADR-0132. Audience B composes advisors at agent definition time; `HookDispatcher` remains platform-internal. `AudienceBExtensionSeamsArchTest` ArchUnit guard added (vacuous at L0 until `examples/**` land in W3). |
| **P0-4** No `StructuredOutputConverter<T>` SPI | Wave E1 — generic `StructuredOutputConverter<T>` interface + `SpringAiBeanOutputConverterAdapter` shell per ADR-0130. |
| **P1-1** No `ConversationMemory` (windowed FIFO + token-budget) variant | Wave E4 — `ConversationMemory extends MemoryStore<String, ConversationTurn>` + `ConversationTurn` record per ADR-0133. Default category `M2_EPISODIC`. |
| **P1-2** Tool-call iteration loop under-specified | Wave E4 — `model-invocation.v1.yaml` supplemented with `tool_call_loop:` section per ADR-0134; declares `agent_driven_loop` (default) and `planner_driven_dispatch` modes with explicit termination signals and message-accumulation rules. |
| **P2-1** `RetrievalOptions.cacheStrategy` forward-looking shape | Wave E5 — ADR-0135 documents the W3 RAG vertical landing shape (5th nullable record field carrying `{"none", "lru", "redis", "annoy", "hnsw"}`). No L0 code change to `RetrievalOptions.java`; W3 wave adds the field and updates `SpiCarrierImmutabilityTest` in the same change. |
| **P2-2** `AgentSession` design intent uncaptured | Wave E5 — ADR-0135 explicitly records that AgentSession is a `(tenantId, conversationId)` projection over the `Run` sequence + `ConversationMemory`; no separate SPI. Future contributors do not propose a redundant `AgentSession` SPI. |
| **OE-4** `F-bulk-scrub-orphan-syntax` had no W-level | Wave G2 — JavaParser + libCST refactor helper wrappers under `gate/lib/refactor/`; policy doc requires AST-aware tooling for broad renames. Family entry advances from `partial` toward `structurally_addressed` (full advancement requires the first AST-aware refactor to land orphan-free). |

## Canonical Baseline (rc51, single source `architecture-status.yaml`)

| Metric | Count | Δ vs rc49/rc50 |
|---|---:|---:|
| §4 constraints | 65 | 0 |
| ADRs | 120 | +7 (ADR-0129..ADR-0135) |
| Active gate rules | 143 | 0 (existing R-D / G-1.1.b iterating logic auto-validates the new SPIs) |
| Active engineering rules | 43 | 0 |
| Gate self-test cases | 258 | 0 (no new gate rules) |
| Enforcer rows | 176 | 0 |
| Architecture graph nodes | 587 | +22 (7 ADR + 5 SPI + 6 structural-carrier + 2 adapter-shell + 5 carrier-test + new contract-yaml nodes; live count from `bash gate/build_architecture_graph.sh`) |
| Architecture graph edges | 1065 | +60 (relates_to + authority + enforcer + source-test edges; live count) |
| Recurring defect families | 15 | 0 (F-l0-agentic-primitive-gap advances from 1 to 3 occurrences; cleanup_status re-confirmed `closed`) |
| Active SPI interfaces (catalog §2) | 38 | +5 (StructuredOutputConverter, PromptTemplate, ChatAdvisor, AdvisorChain, ConversationMemory) |
| Structural carriers (catalog §2) | +6 | (ModelResponseChunk, PromptTemplateSource, RenderedPrompt, AdvisedRequest, AdvisedResponse, ConversationTurn) |
| Maven tests | ≥423 | pending mvn verify count for the rc51 carrier-immutability tests |

## What Shipped at rc51

### Strategic-foundation ADR (Wave E5)

| ADR | Title | Decision |
|---|---|---|
| **ADR-0135** | AgentSession-as-Run-Projection | Documentation-only: AgentSession is a `(tenantId, conversationId)` projection over the `Run` sequence + `ConversationMemory`; no separate `AgentSession` SPI. RetrievalOptions.cacheStrategy is W3-deferred with declared shape. |

### Contract-shape ADRs (Waves E1–E4)

| ADR | Title | Wave | Contract YAML |
|---|---|---|---|
| **ADR-0129** | Streaming-aware ModelGateway | E1 | `model-streaming.v1.yaml` (design_only) |
| **ADR-0130** | StructuredOutputConverter SPI | E1 | `structured-output.v1.yaml` (design_only) |
| **ADR-0131** | Prompt Template SPI | E2 | `prompt-template.v1.yaml` (design_only) |
| **ADR-0132** | Chat Advisor SPI | E3 | `chat-advisor.v1.yaml` (design_only) |
| **ADR-0133** | Conversation Memory SPI variant | E4 | `memory-store.v1.yaml` `conversation_memory:` supplement |
| **ADR-0134** | Tool-Call Iteration Loop semantics | E4 | `model-invocation.v1.yaml` `tool_call_loop:` supplement |

### SPI surface (Waves E1–E4)

5 new Java SPI interfaces under `agent-middleware`:

| Module | Package | SPI |
|---|---|---|
| `agent-middleware` | `model.spi` | `StructuredOutputConverter<T>` |
| `agent-middleware` | `prompt.spi` (NEW package) | `PromptTemplate` |
| `agent-middleware` | `advisor.spi` (NEW package) | `ChatAdvisor`, `AdvisorChain` |
| `agent-middleware` | `memory.spi` | `ConversationMemory` (variant `extends MemoryStore<String, ConversationTurn>`) |

Plus 1 SPI method addition on the existing `ModelGateway`:
`default Stream<ModelResponseChunk> stream(ModelInvocation)`.

6 new structural carriers (records / sealed types — not SPI per the
catalog §2 inclusion rule):

| Type | Module | Package |
|---|---|---|
| `ModelResponseChunk` (sealed: ContentDelta \| ToolCallDelta \| Complete) | `agent-middleware` | `model.spi` |
| `PromptTemplateSource` (sealed: InlineString \| ClasspathResource) + `PlaceholderSyntax` enum | `agent-middleware` | `prompt.spi` |
| `RenderedPrompt` (record) | `agent-middleware` | `prompt.spi` |
| `AdvisedRequest` (record) | `agent-middleware` | `advisor.spi` |
| `AdvisedResponse` (record) | `agent-middleware` | `advisor.spi` |
| `ConversationTurn` (record) | `agent-middleware` | `memory.spi` |

### Integration & discoverability (Waves E1, E2)

2 new Spring AI reference adapter shells under
`agent-service.service.integration.springai`:

| Shell | Wraps | Throws |
|---|---|---|
| `SpringAiBeanOutputConverterAdapter<T>` | Spring AI `BeanOutputConverter` (held as `Object` to defer FQN to W2) | `UnsupportedOperationException` on `convert(...)` and `getFormatInstructions()` |
| `SpringAiPromptTemplateAdapter` | Spring AI `org.springframework.ai.chat.prompt.PromptTemplate` (held as `Object`) | `UnsupportedOperationException` on `render(...)` |

Plus quickstart §4.6 added (companion to the rc43 §4.5) showing the
customer-side wiring pattern for `StructuredOutputConverter` +
`PromptTemplate` + `ChatAdvisor` + `ConversationMemory`.

### Carrier-immutability + ArchUnit guards (Waves E1–E4)

5 new test classes:

- `ModelStreamingChunkCarrierImmutabilityTest` (agent-middleware)
- `PromptTemplateCarrierImmutabilityTest` (agent-middleware)
- `AdvisorSpiCarrierImmutabilityTest` (agent-middleware)
- `ConversationMemoryCarrierImmutabilityTest` (agent-middleware)
- `AudienceBExtensionSeamsArchTest` (agent-service) — ArchUnit guard
  with `allowEmptyShould(true)` asserting customer code in `examples/**`
  does not import `org.springframework.ai.chat.client.advisor..` or
  `org.springframework.ai.chat.prompt..` directly. Vacuous at L0 until
  `examples/**` lands in W3.

### Governance lockstep (Wave G1 documentation)

- `docs/governance/recurring-defect-families.yaml` advanced: F-l0-agentic-primitive-gap
  occurrences 1 → 3; cleanup_status re-confirmed `closed` at rc51.
- `docs/governance/recurring-defect-families.md` re-rendered byte-identical
  with the updated template per Rule G-13.b.
- `agent-middleware/module-metadata.yaml#spi_packages` widened from 7 to 9
  entries (`prompt.spi` + `advisor.spi`).
- `docs/dfx/agent-middleware.yaml#spi_packages` widened equivalently
  (Rule R-D.e parity).
- `agent-middleware/ARCHITECTURE.md` §SPI Appendix widened from 7 to 9
  spi_packages and from 15 to 19 rows (Rule G-1.1.b parity).
- `docs/contracts/contract-catalog.md` Active SPI table advanced from
  33 to 38 (Rule R-D.f / .g parity); structural carriers table extended
  with 6 new rc51 rows.
- `docs/governance/architecture-status.yaml#baseline_metrics` updated:
  adr_count 113 → 120; architecture_graph_nodes 565 → 587;
  architecture_graph_edges 1005 → 1065.
- `README.md` baseline phrase updated to 120 ADRs / 587 nodes / 1065 edges.

### Wave G2 — AST-aware refactor tooling

- `gate/lib/refactor/java_rename.py` — JavaParser-CLI wrapper for AST-aware
  Java identifier renames (and Javadoc-citation rewrites that today get
  caught by Rule D-9 `no_version_log_metadata_in_code`).
- `gate/lib/refactor/python_rename.py` — libCST wrapper for AST-aware
  Python identifier renames.
- `gate/lib/refactor/POLICY.md` — declares "broad renames MUST use
  AST-aware tooling, not `sed` / `find -exec`" and lists the existing
  bulk-scrub scripts that should migrate over time.

### Wave G1 — Governance Pruning Roadmap (documentation-only at rc51)

Wave G1 cannot execute now: it depends on Rule G-13 landing single-source
rendering for the surfaces it would retire, and the W3..W10 retirement
schedule from ADR-0119 unfolds incrementally with the product waves.
The captured plan:

| Wave | Step | Subsumed rule |
|---|---|---|
| W3 | retire G-2.b (renderable from architecture-status.yaml) | rule-G-2.md sub-clause .b |
| W4 | retire G-2.d root portion (renderable from pom.xml + architecture-status.yaml) | rule-G-2.md sub-clause .d (root segment) |
| W5 | retire G-2.1 (renderable from `gate/active-corpus-name-exemption-markers.txt` + pom.xml diff) | rule-G-2.1.md |
| W6 | retire G-8.a (renderable from architecture-status.yaml + architecture-graph.yaml header) | rule-G-8.md sub-clause .a |
| W7 | retire G-8.c (renderable from pom.xml + module-metadata.yaml) | rule-G-8.md sub-clause .c |
| W8 | retire G-8.e (renderable from contract-catalog.md + filesystem) | rule-G-8.md sub-clause .e |
| W9 | retire G-9.c (renderable from recurring-defect-families.yaml) | rule-G-9.md sub-clause .c |
| W10 | cleanup sweep — remove subsumed rule cards; update `rule-history.md`; re-baseline `active_gate_checks` | — |

Pre-staged complement: `CLAUDE-deferred.md` rows gain a "Deferred-Rule
Readiness Checklist" section per row (what evidence will satisfy the rule
the day its trigger fires) — landing alongside the W2 LLM gateway wave.

## Current-vs-Forward Claims

| Subject | Current shipped behaviour | Verified by | Forward behaviour | Promotion trigger |
|---|---|---|---|---|
| `ModelGateway.stream(...)` | Default `Stream<ModelResponseChunk>` method throws `UnsupportedOperationException` | `ModelStreamingChunkCarrierImmutabilityTest`; ADR-0129; `model-streaming.v1.yaml` | Functional Spring AI `ChatModel.stream(...)` invocation through virtual-thread isolation + `BEFORE/AFTER_LLM` hook brackets | W2 LLM gateway wave |
| `StructuredOutputConverter<T>` | SPI interface compiles; `SpringAiBeanOutputConverterAdapter<T>` shell throws | `SpiCarrierImmutabilityTest` (existing) + ADR-0130 | Functional Spring AI `BeanOutputConverter` invocation; format-instruction injection into `ModelInvocation.parameters` | W2 LLM gateway wave |
| `PromptTemplate` | SPI interface compiles; `SpringAiPromptTemplateAdapter` shell throws | `PromptTemplateCarrierImmutabilityTest`; ADR-0131; `prompt-template.v1.yaml` | Functional rendering against Spring AI `PromptTemplate.create(...)` with placeholder substitution | W2 prompt-rendering wave |
| `ChatAdvisor` chain | SPI interfaces compile; `AdvisorChain.next(...)` has no binding | `AdvisorSpiCarrierImmutabilityTest`; ADR-0132; `chat-advisor.v1.yaml` | Chain binds into `ModelGateway.invoke` decoration inside `HookDispatcher.fire(BEFORE_LLM)`/`AFTER_LLM` brackets; Telemetry Vertical co-arrival per ADR-0061 §7 | W2 LLM gateway + Telemetry Vertical |
| `ConversationMemory` | SPI interface compiles; no production impl | `ConversationMemoryCarrierImmutabilityTest`; ADR-0133 | First production impl backs M2_EPISODIC turn storage with implementation-chosen tokenizer | W2 chat-memory wave |
| Tool-call iteration loop | Two modes declared in `model-invocation.v1.yaml` (`agent_driven_loop` default; `planner_driven_dispatch`) | ADR-0134 | First production AgentDefinition dispatches tool calls per declared mode; `tool-call-loop-mode` metadata key respected | W2 tool-loop wave |
| `AgentSession` as projection | No SPI; design intent captured in ADR-0135 | ADR-0135 | No separate SPI; durable session metadata (if needed) materialises via `Skill` + `RunRepository` query view | — (deliberate non-SPI) |
| `RetrievalOptions.cacheStrategy` | Not in `RetrievalOptions` record; W3 landing shape declared in ADR-0135 | ADR-0135; `vector-store.v1.yaml` | 5th nullable record field added; `SpiCarrierImmutabilityTest` updated in same W3 change | W3 RAG vertical wave |
| `AudienceBExtensionSeamsArchTest` | Vacuous (no `examples/**` directory exists) | `.allowEmptyShould(true)` | Arms automatically when `examples/**` lands | W3 SDK GA wave |
| Wave G1 governance pruning | Documentation-only at rc51; retirement schedule captured above | ADR-0119; rc51 release-note §Wave G1 section | W3..W10 incremental retirement of subsumed rules | Each retirement gated on Rule G-13 covering the subsumed surface |

## Recurring Family Closure

| Family | Cited findings | Sibling surfaces checked | Closure result | Residual risk |
|---|---|---|---|---|
| `F-l0-agentic-primitive-gap` | rc50 P0-1, P0-2, P0-3, P0-4, P1-1, P1-2, P2-1, P2-2 | ADR-0129–0135, contract catalog (catalog ↔ metadata ↔ DFX ↔ L1 SPI Appendix), recurring-defect-families.{yaml,md}, README, quickstart §4.6, architecture-status.yaml baseline | re-confirmed `closed` at rc51 with 3 total occurrences (rc41 primitive-tier discovery → rc43 primitive-tier closure → rc50/rc51 ergonomics-tier rediscovery + closure) | Runtime impl deferred to W2-W3 per per-SPI promotion triggers; META-lesson recorded — closure-by-construction at one tier can mask gaps at the adjacent ergonomics tier; every L0 closure review MUST scan ergonomics surface in addition to primitive surface |
| `F-bulk-scrub-orphan-syntax` | OE-4 (Wave G2) | `gate/lib/refactor/{java_rename.py, python_rename.py, POLICY.md}` | `partial` → progressing toward `structurally_addressed` once the first AST-aware refactor lands orphan-free | Backlog: migrate existing bulk-scrub scripts to AST-aware helpers |
| `F-numeric-drift` | rc51 baseline lockstep (adr_count 113 → 120; graph 565/1005 → 587/1065; SPI catalog 33 → 38) | architecture-status.yaml, README, contract-catalog.md, recurring-defect-families.{yaml,md} | `partial` continues; rc51 release-evidence generation deferred to rc52 corrective | Future releases MUST generate evidence from frozen candidate commit before bumping baseline-metrics |

(Other 12 families: no rc51 occurrence; status unchanged from rc50 ledger.)

## Authority Refresh

| Surface | Role | Freshness proof |
|---|---|---|
| `docs/adr/0129-streaming-aware-model-gateway.yaml` | normative | Rule R-D iterating logic auto-validates the SPI surface across catalog ↔ metadata ↔ DFX |
| `docs/adr/0130-structured-output-converter-spi.yaml` | normative | Rule R-D iterating logic |
| `docs/adr/0131-prompt-template-spi.yaml` | normative | Rule R-D iterating logic |
| `docs/adr/0132-chat-advisor-spi.yaml` | normative | Rule R-D iterating logic |
| `docs/adr/0133-conversation-memory-spi-variant.yaml` | normative | Rule R-D iterating logic |
| `docs/adr/0134-tool-call-iteration-loop.yaml` | normative | `model-invocation.v1.yaml` `tool_call_loop:` section parity |
| `docs/adr/0135-agent-session-as-run-projection.yaml` | normative | ADR-0128 (Agent first-class) cross-reference |
| `docs/contracts/contract-catalog.md` | normative | Rule G-13.b byte-identical template render verified |
| `agent-middleware/ARCHITECTURE.md` | normative L1 | Rule G-1.1.b 4-way parity (catalog ↔ metadata ↔ DFX ↔ L1 SPI Appendix) |
| `docs/governance/recurring-defect-families.yaml` + `.md` | family ledger | Rule G-9.c parity confirmed; Rule G-13.b template render byte-identical |
| `docs/governance/architecture-status.yaml` | baseline | `baseline_metrics` updated in same wave per Rule G-2.b (single source) |
| `docs/quickstart.md` §4.6 | tutorial | Audience B wiring pattern for the rc51 ergonomics tier |

## Verification Commands

All commands driven from WSL/Linux per Rule G-7.

```bash
# Re-run gate against rc51 changes
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && bash gate/check_architecture_sync.sh'

# Re-run architecture graph build (idempotent per Rule G-1.b)
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && bash gate/build_architecture_graph.sh'

# Run Maven verify (includes SpiPurityGeneralizedArchTest which now
# validates the 2 new spi_packages prompt.spi + advisor.spi):
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && ./mvnw -T 1C clean verify'

# Run gate self-tests:
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && bash gate/test_architecture_sync_gate.sh'

# Re-run template idempotency (Rule G-13.b):
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && python3 gate/lib/check_template_render_idempotency.py'

# (Once formal-release evidence bundle is generated — pending rc52 corrective:)
# bash gate/check_formal_release_transaction.sh --evidence \
#   gate/release-ci-evidence/2026-05-25-l0-rc51-agentic-completeness.evidence.yaml
```

## Four Competitive Pillars

- **performance** — no runtime hot-path change at L0 (contract-shape only).
  The W2 LLM gateway wires Reactive WebClient / virtual-thread-backed
  clients per Rule R-G; streaming output via virtual-thread-backed
  `Stream<ModelResponseChunk>` keeps the SPI surface synchronous per
  Rule R-D.
- **cost** — no new runtime infrastructure or storage service added at L0.
  Spring AI BoM 2.0.0-M5 already imported pre-rc43; rc51 adds zero new
  runtime dependencies (the JavaParser + libCST tooling lives under
  `gate/lib/refactor/` and is test-time only).
- **developer_onboarding** — quickstart §4.6 adds the rc51 ergonomics-tier
  wiring pattern showing customer extensions for streaming +
  StructuredOutputConverter + PromptTemplate + ChatAdvisor +
  ConversationMemory. Rule R-A "Business/Platform Decoupling" is now
  non-vacuously satisfied across both the primitive tier (rc43) and the
  ergonomics tier (rc51) — Audience B never imports Spring AI types
  directly.
- **governance** — F-l0-agentic-primitive-gap re-confirmed `closed` with
  rc51 documenting the META-lesson that closure-by-construction at one
  primitive tier can mask gaps at the adjacent ergonomics tier; baseline
  lockstep across architecture-status.yaml + README + recurring-defect-families
  (.yaml/.md) + contract-catalog per Rule G-9; ADR-0135 records the
  deliberate non-SPI design decision for AgentSession.

## Residual Risk

No accepted residual blocks the rc51 publication. The candidate-commit
SHA + formal-release-transaction evidence bundle is intentionally
deferred to a follow-on rc52 corrective so that the bundle is generated
against a frozen candidate commit per the existing rc48 → rc49
supersession pattern. Implementation of the 5 new SPI interfaces +
streaming method is W2-W3 staged per the per-SPI promotion triggers
recorded in each ADR.

## Cross-references

- Originating review: `docs/logs/reviews/2026-05-25-l0-rc50-post-closure-senior-architect-review.en.md`
- Wave plan: `.claude/plans/d-chao-workspace-spring-ai-ascend-docs-quiet-lake.md`
- Re-scoped release: `docs/logs/releases/2026-05-25-l0-rc50-codegraph-nodegraph-supplement.en.md`
- Strategic-foundation ADR: ADR-0135
- Contract-shape ADRs: ADR-0129, ADR-0130, ADR-0131, ADR-0132, ADR-0133, ADR-0134
- Recurring family: `docs/governance/recurring-defect-families.{yaml,md}` §F-l0-agentic-primitive-gap (3 occurrences; cleanup_status `closed`)
- L0 closure-by-construction META-lesson: rc41 → rc43 primitive tier; rc50 → rc51 ergonomics tier

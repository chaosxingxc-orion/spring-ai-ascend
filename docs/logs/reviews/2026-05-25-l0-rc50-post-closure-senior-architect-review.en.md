---
review_kind: post-closure-architecture-review
reviewer_role: "senior Java microservices + agent-platform architect (independent)"
target_release: docs/logs/releases/2026-05-25-l0-rc50-codegraph-nodegraph-supplement.en.md
target_baseline: "113 ADRs / 143 active gate rules / 33 active SPI interfaces / 565 graph nodes / 1005 graph edges / 15 recurring defect families"
verdict: improve-before-closing-rc
follow_up_release: docs/logs/releases/2026-05-25-l0-rc51-agentic-completeness.en.md
---

# Post-Closure Senior-Architect Review of rc50 (L0 Agentic Contract Surface)

## 1. Reviewer scope and method

I read the `spring-ai-ascend` repository at the rc50 frozen commit
`b554d74` against the lens of a senior Java microservices + agent-platform
architect. My audit covered:

- The 14 SPI interfaces landed at rc43 (Agent, AgentRegistry, ModelGateway,
  Skill, SkillRegistry, MemoryStore + MemoryReader + MemoryWriter +
  SemanticMemoryStore + KnowledgeMemoryStore, VectorStore, Retriever,
  EmbeddingModel, Planner) and their value carriers.
- The 7 design-only contract YAMLs (agent-definition, model-invocation,
  skill-definition, memory-store, vector-store, planning-request, plan).
- The 5 Spring AI reference adapter shells under
  `agent-service.service.integration.springai/`.
- ADRs 0120–0128 (the rc43–rc48 strategic + contract-shape wave).
- `docs/CLAUDE-deferred.md` (27 deferred rules with explicit triggers).
- `docs/governance/recurring-defect-families.yaml` (15 families; closure
  status per family).
- Root `ARCHITECTURE.md` and the 5 active L1 module `ARCHITECTURE.md`
  documents.
- `docs/quickstart.md` §4.5 customer-extension preview.

I deliberately did NOT audit: ADR 0001–0119 (the pre-rc43 corpus is
already triple-reviewed); gate rules / enforcers / self-tests (those are
the platform's own consistency machinery, not the agentic surface); the
deployment-plane and edge-routing portions (covered by ADR-0089 and rc13
sufficiently).

## 2. Verdict

**Improve before closing.** The L0 surface at rc50 is approximately
85 % complete. Four contract-shape gaps remain at the **developer-
ergonomics tier** that the rc43–rc48 wave (primitive tier) left
unmodelled. These four gaps will, at W2 implementation time, force
Audience B (external Spring developers) to import Spring AI types
directly, defeating Rule R-A "Business/Platform Decoupling"
non-vacuously and re-creating the very gap that
F-l0-agentic-primitive-gap was meant to seal. Closure is a small
follow-on wave (estimated 2–3 senior-engineer days following exactly the
same contract-shape-at-L0 pattern that rc43–rc48 already established).

## 3. What rc48–rc50 got right

- **Spring AI canonical (ADR-0125 Path a)** is strategically correct.
  Thin-decorator boundary preserves SPI purity, tenant scoping
  orthogonality, hook ownership, and portability via composition
  (e.g. `Retriever` wraps platform `VectorStore`, not Spring AI's).
- **SkillKind unification (ADR-0122/0127)** is elegant: one `Skill` SPI,
  one `SkillRegistry`, one capacity arbiter, discriminate sandbox
  routing by enum kind (TOOL / BUILTIN / AGENT_AS_TOOL / MCP_SERVER /
  UNTRUSTED_TOOL / UNTRUSTED_CODE).
- **Memory M1–M6 taxonomy (ADR-0123)** mirrors the cognitive-architecture
  literature (short-term, episodic, semantic, graph, knowledge,
  retrieved) and supports CQRS split via `MemoryReader` + `MemoryWriter`.
- **DAG-shaped Plan (ADR-0126)** is more capable than naive sequential:
  steps, dependencies, branches, loops, budgets, and strategy enum
  cover REACT and Tree-of-Thought naturally.
- **Agent-as-Tool composition (ADR-0122 + ADR-0128)** correctly models
  agent-to-agent composition as `SkillKind.AGENT_AS_TOOL` — no separate
  MultiAgent SPI needed.
- **Design-only shell discipline** is preserved: every Spring AI adapter
  throws `UnsupportedOperationException`, compile-time boundary is
  proven, runtime binding is properly scoped to W2/W3.

## 4. P0 findings (block RC closure)

### P0-1 — `ModelGateway` has no streaming surface

`ModelGateway.invoke(ModelInvocation) → ModelResponse` is sync-complete-
envelope only. Spring AI ships `ChatModel.stream(Prompt) → Flux<ChatResponse>`
as a first-class contract. Without a platform streaming surface, at W2 the
SPI must either grow a second method (breaking the v1.0 contract that just
shipped) or Audience B will bypass `ModelGateway` for streaming. Rule 15
(Streamed Handoff Mode Conformance) is deferred to W2 but its SPI binding
target does not exist.

**Closure shape**: add `default Flux<ModelResponseChunk> stream(ModelInvocation)`
to `ModelGateway` returning `UnsupportedOperationException` at L0; or use
`java.util.stream.Stream<ModelResponseChunk>` to keep SPI purity. Sealed
`ModelResponseChunk` discriminates `ContentDelta` / `ToolCallDelta` /
`Complete`. ADR-0129 captures the decision.

### P0-2 — No `PromptTemplate` SPI

`AgentDefinition.systemPrompt` is a `String` literal; user messages are
flat strings in `ModelInvocation.messages`. The most-used Spring AI
Audience B feature is `PromptTemplate.from("Hello {name}").create(...)`.
Without a platform `PromptTemplate` SPI, every customer reaches directly
for `org.springframework.ai.chat.prompt.PromptTemplate`, bypassing the
decoration boundary. Rule R-A becomes vacuously satisfied again.

**Closure shape**: add `PromptTemplate` SPI in
`agent-middleware.prompt.spi` with `RenderedPrompt render(String tenantId,
Map<String, Object> variables)`; sealed `PromptTemplateSource` (InlineString
\| ClasspathResource) carrying a `PlaceholderSyntax` enum. Reference
adapter `SpringAiPromptTemplateAdapter` in the existing
`service.integration.springai/` package, design-only at L0. ADR-0131.

### P0-3 — No `ChatAdvisor` (interceptor) SPI

Spring AI's killer ergonomic feature is
`ChatClient.builder().defaultAdvisors(...)`. The platform's stated answer
("use Plan.steps with memory-scope") couples retrieval semantics to the
planner, which is wrong for the simple chat-with-memory case.
`HookDispatcher` is gate-private machinery, not an Audience B surface.

**Closure shape**: add `ChatAdvisor` SPI in
`agent-middleware.advisor.spi` with `AdvisedResponse aroundCall(AdvisedRequest,
AdvisorChain)`; bind into `ModelGateway.invoke` decoration via
`HookDispatcher` at W2 (Telemetry Vertical co-arrives per ADR-0061 §7).
ADR-0132.

### P0-4 — No `StructuredOutputConverter<T>` SPI

`ModelResponse.content` is a raw `String`; `SkillDefinition.outputSchema`
is a JSON-schema string. There is no type-parameterised extraction path.
At W2, every customer extracting typed beans will either parse JSON
manually or import `org.springframework.ai.converter.BeanOutputConverter`
directly. Same Rule R-A vacuity.

**Closure shape**: add `StructuredOutputConverter<T>` SPI in
`agent-middleware.model.spi` (sibling to `ModelResponse`) with
`T convert(ModelResponse)` and `String getFormatInstructions()`.
Jackson-backed reference adapter in `service.integration.springai/`,
design-only at L0. ADR-0130.

## 5. P1 findings (should land before next formal release)

### P1-1 — No `ConversationMemory` (windowed FIFO + token-budget) variant

`MemoryStore<K, V>` with `MemoryQuery.scan(...)` is more general but not
ergonomic for the "last N user/assistant messages bounded by token
budget" pattern that every chat agent needs. Add `ConversationMemory
extends MemoryStore<String, ConversationTurn>` with `addMessages(...)`,
`getMessagesUpToBudget(int)`, and `summariseAndCompact(int keepLastN)`.
Default category `M2_EPISODIC`. ADR-0133.

### P1-2 — Tool-call iteration loop under-specified

`model-invocation.v1.yaml` declares request/response shape but not the
LLM ↔ Tool ↔ LLM iteration semantics. Two execution modes are implicit
(agent-driven where the LLM iterates with growing message history vs.
planner-driven where a `Plan` dispatches steps) — neither is documented.
Supplement the contract with a `tool_call_loop:` section declaring both
modes, message-accumulation rules for `ToolResultMessage` re-injection,
and termination conditions. ADR-0134.

## 6. P2 follow-ups (documentation-only)

### P2-1 — `RetrievalOptions.cacheStrategy` forward-looking shape

The W3 RAG vertical will introduce embedding caches. Today `RetrievalOptions`
has no `cacheStrategy` field; the absence is intentional but no ADR
records that intention. Capture in ADR-0135 the declared W3 landing
shape: a 5th nullable record field carrying one of `{"none", "lru",
"redis", "annoy", "hnsw"}`. The L0 record stays unchanged to avoid a
constructor-signature change that ripples through existing
SpiCarrierImmutabilityTest.

### P2-2 — `AgentSession` deliberate-non-SPI capture

The platform has no `AgentSession` SPI distinct from `Run` —
deliberately. Session continuity is the projection
`(tenantId, conversationId)` over the `Run` sequence + M2_EPISODIC
`ConversationMemory`. Capture this design intent in ADR-0135 so future
contributors do not propose a redundant `AgentSession` SPI.

## 7. Over-engineering signals (informational; not blockers)

| # | Signal | Recommendation |
|---|---|---|
| **OE-1** | 6 active meta-governance rules (G-1, G-8, G-9, G-10, G-11, G-13) + 5 gate-level meta-rules (Rules 96, 99, 112, 114). ~14 % of active engineering-rule budget. | Once Rule G-13 (single-source rendering coherence) lands in W3+, accelerate the W3..W10 retirement schedule from ADR-0119 (subsumes G-2.b, G-2.d, G-2.1, G-8.a/c/e, G-9.c). Target ~35 % reduction in active gate rules by end of W3. |
| **OE-2** | 143 gate rules / 176 enforcers / 258 self-tests for what is structurally L0. Sustainable but at the upper bound. | Re-baseline at every formal release; prune any rule whose constraint is fully subsumed by Rule G-13 rendering. |
| **OE-3** | 27 deferred rules in `CLAUDE-deferred.md`. At the W2 LLM-gateway trigger, ≥ 8 rules unfreeze simultaneously — release-day bottleneck. | Add a proactive readiness checklist per deferred rule; mark its enforcer rows as `staged: true` in `enforcers.yaml` so they pre-validate. |
| **OE-4** | `F-bulk-scrub-orphan-syntax` family has **no W-level assignment**. Structural fix is AST-aware tooling. | Small W2 investment: add `gate/lib/refactor/{java_rename.py, python_rename.py, POLICY.md}` wrapping JavaParser + libCST. |

## 8. Things I deliberately did NOT flag

- **No `AgentSession` SPI distinct from `Run`** — defensible design choice;
  captured in P2-2 as documentation-only via ADR-0135.
- **No `MultiAgent` / `Orchestrator` SPI for agent-to-agent composition** —
  correctly modelled as `SkillKind.AGENT_AS_TOOL`; avoids over-modelling.
- **No `AgentLifecycle` SPI** — correctly delegated to Spring DI + per-Skill
  `init/suspend/teardown`.
- **All 5 Spring AI adapter shells throw `UnsupportedOperationException`** —
  this is correct L0 design-only discipline, not a gap.
- **Spring AI canonical (ADR-0125 Path a)** — strategically correct;
  decoration boundary holds.

## 9. Recommended remediation program (rc51 wave)

The improvement program is a single coherent wave following the rc43–rc48
contract-shape pattern. Wave breakdown:

- **Wave E1** — Model Surface Completeness (P0-1, P0-4): ADR-0129 + ADR-0130.
- **Wave E2** — Prompt Layer (P0-2): ADR-0131.
- **Wave E3** — Advisor Interceptor Layer (P0-3): ADR-0132.
- **Wave E4** — Memory & Tool-Loop Semantics (P1-1, P1-2): ADR-0133 + ADR-0134.
- **Wave E5** — Documentation & Forward-Looking Capture (P2-1, P2-2): ADR-0135.
- **Wave G1** — Governance Pruning (OE-1, OE-2, OE-3): W3..W10 retirement
  schedule per ADR-0119. Documentation-only at rc51 (depends on Rule G-13
  W3+ landing).
- **Wave G2** — AST-aware refactor tooling (OE-4): `gate/lib/refactor/`.

Sequencing: E1–E5 can land in a single wave; G1 is incremental across
W3–W10; G2 is a W2 investment that runs in parallel with the first W2
product wave.

## 10. Verification (post-rc51 closure)

- `bash gate/check_architecture_sync.sh` exits 0.
- `bash gate/test_architecture_sync_gate.sh` reports all self-tests passed.
- `./mvnw -T 1C clean verify` succeeds; new SPI carrier-immutability tests
  green.
- `F-l0-agentic-primitive-gap` ledger entry advances from 1 to 3 occurrences;
  cleanup_status re-confirmed `closed` at rc51.
- `docs/contracts/contract-catalog.md` Active SPI total advances from 33
  to 38; structural carriers table extends with 6 new rows.
- `docs/governance/architecture-status.yaml#baseline_metrics.adr_count`
  advances from 113 to 120.
- `docs/quickstart.md` §4.6 compiles end-to-end with the new ergonomics-tier
  examples.

## 11. Cross-references

- Strategic foundation: ADR-0120, ADR-0122, ADR-0125, ADR-0127, ADR-0128
  (preserved unchanged at rc51).
- rc48 architecture review:
  `docs/logs/reviews/2026-05-25-l0-rc48-agentic-contract-surface-architecture-review.en.md`.
- rc49 corrective release:
  `docs/logs/releases/2026-05-25-l0-rc49-agentic-contract-surface-corrective.en.md`.
- rc50 supplemental release:
  `docs/logs/releases/2026-05-25-l0-rc50-codegraph-nodegraph-supplement.en.md`.
- rc51 closure release (follow-on):
  `docs/logs/releases/2026-05-25-l0-rc51-agentic-completeness.en.md`.
- `F-l0-agentic-primitive-gap` family ledger:
  `docs/governance/recurring-defect-families.yaml`.

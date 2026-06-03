---
level: L1
view: logical
module: agent-middleware
status: extracted-spi
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0073 (Engine Hooks + Runtime Middleware SPI); Layer-0 principle P-M (Heterogeneous Engine Contract); Rule R-M.c (Runtime-Owned Middleware via Engine Hooks, formerly Rule 45)"
---

# agent-middleware — L1 architecture (SPI extracted)

> Owner: Middleware team | Wave: W2 | Maturity: SPI-only (consumer impls W2)
> Created: 2026-05-17 (six-module materialization PR); SPI extraction T2.B1 landed 2026-05-17.

## Status

**Cross-cutting middleware SPI extracted per ADR-0073.** The five SPI types
(`HookPoint`, `RuntimeMiddleware`, `HookContext`, `HookOutcome`,
`HookDispatcher`) live under `agent-middleware/src/main/java/com/huawei/ascend/middleware/`
(SPI in the `.spi` sub-package; `HookDispatcher` at the package root as the
runtime dispatcher implementation).

The W2 Telemetry Vertical populates the consumer impls (TokenCounterHook,
PiiRedactionHook, CostAttributionHook, LlmSpanEmitterHook) — those land alongside
the W2 observability work, not in this module today.

## 0.4 Layered 4+1 view map (W1 — ADR-0068)

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | runtime-owned cross-cutting concerns |
| §2 Hook surface | logical | 10 canonical HookPoint values per `docs/contracts/engine-hooks.v1.yaml` (BEFORE/AFTER LLM, TOOL, MEMORY; BEFORE_SUSPENSION/RESUME; ON_ERROR; ON_YIELD added rc22 per ADR-0100) |
| §3 Dispatch order | process | declaration order; fail-fast inside the chain |

## 1. Role

`agent-middleware` is the **runtime-owned middleware module**. It
implements Rule R-M.c (formerly Rule 45) / P-M: cross-cutting policies (model gateway, tool
authz, memory governance, tenant policy, quota, observability, sandbox
routing, checkpoint, failure handling) are expressed as
`RuntimeMiddleware` listeners attached at canonical `HookPoint` events.

## 2. Hook surface

Authority: `docs/contracts/engine-hooks.v1.yaml` (gate Rule 57 enforces
yaml↔enum consistency). Nine canonical hook points:

| HookPoint | Fired by | Typical consumers |
|---|---|---|
| `BEFORE_LLM_INVOCATION` | engine | token-budget, pii-redaction |
| `AFTER_LLM_INVOCATION` | engine | cost-attribution, span emit |
| `BEFORE_TOOL_INVOCATION` | engine | tool-authz, action-guard |
| `AFTER_TOOL_INVOCATION` | engine | observability |
| `BEFORE_MEMORY_READ` | engine | tenant-scoped read filter |
| `AFTER_MEMORY_WRITE` | engine | privacy redaction |
| `BEFORE_SUSPENSION` | orchestrator | checkpoint enrichment |
| `BEFORE_RESUME` | orchestrator | run-state validation |
| `ON_ERROR` | engine + orchestrator | best-effort failure logging |

## 3. Dispatch semantics (W2.x scope)

- Hook ordering = middleware registration order.
- Default failure propagation = fail-fast inside the dispatcher chain
  (a non-`Proceed` outcome stops subsequent middlewares for the same
  `HookPoint`).
- Run-state consumption of outcomes (`Fail` → `Run.FAILED`,
  `ShortCircuit` → engine bypass) is DEFERRED to W2 Telemetry Vertical
  per Rule R-M sub-clause .c.b in the `deferred_sub_clauses` block of
  `docs/governance/rules/rule-R-M.md`.
- `on_error` is best-effort across the chain.

## 4. Forbidden imports (SPI purity per Rule R-D, formerly Rule 32)

Every `com.huawei.ascend.middleware..spi..` package imports only from
`java.*` and same-package SPI siblings. Enforced by
`SpiPurityGeneralizedArchTest` (E48). Cross-SPI dependency is not an
allowed design escape hatch; adapter layers translate between packages.
Constructive impls under `com.huawei.ascend.middleware.*` may use any
agent-* dep listed in `module-metadata.yaml#allowed_dependencies` (today:
empty — the W2 Telemetry Vertical may widen this).

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. `docs/contracts/engine-hooks.v1.yaml` — canonical hook surface.
3. ADR-0073 — module authority.
4. ADR-0103 — naming resolution + capability-services distribution (READ FIRST if you're here because someone said "agent-middleware should be Memory/Skills/Sandbox/Knowledge").
5. `docs/dfx/agent-middleware.yaml` — Design-for-X declarations.

---

## What this module is NOT (rc22 / ADR-0103)

The term "Agent Middleware" carries two distinct meanings in the wider agent-platform community:

1. **In-process runtime hooks** — THIS module's scope. Cross-cutting policy injection (model gateway, tool authz, tenant policy, observability, ...) via canonical `HookPoint` events.
2. **Cloudified Agentic Capability Services** — Memory Systems, Skill Registry, Sandbox Execution, Knowledge Index. This is what some external proposals (e.g., 2026-05-21 polymorphic-deployment proposal §2) want the term to mean.

The user has pinned the six L1 reactor modules as the grounding architecture and explicitly directed: do NOT create a seventh module for Capability Services. Per ADR-0103, the capability-services concepts distribute across the existing six:

| Capability concept | Home in the six modules | Status |
|---|---|---|
| **Memory** | `spring-ai-ascend-graphmemory-starter` (SPI consumer) + `agent-service` (`GraphMemoryRepository` SPI surface per ADR-0082); tenant-scoped read/write filters land via THIS module's existing hooks `BEFORE_MEMORY_READ` / `AFTER_MEMORY_WRITE` | shipped (SPI) + W2 (filters) |
| **Skills** | `agent-runtime` (skill registry + `ResilienceContract.resolve(tenant, skill)`) + `agent-service` (capacity governance via `SkillCapacityRegistry`); authz lands via THIS module's `BEFORE_TOOL_INVOCATION` hook | shipped (W1) |
| **Sandbox** | `agent-runtime` (`SandboxExecutor` SPI) + `docs/governance/sandbox-policies.yaml` SSOT per Rule R-L; runtime refusal of over-wide grants deferred per Rule R-L.b | policy shipped, runtime W2 |
| **Knowledge** | DEFERRED to W3+ — no active module owns Knowledge capability service today | deferred |

The substantive insight (capability services are first-class concerns) is accepted. The structural solution (a new module called `agent-middleware`) is rejected. See ADR-0103 for full rationale.

---

## 5. Development View (Rule G-1.1.a — rc22 / ADR-0099)

Target directory tree (current namespace; rc22.5 migrates to `com.huawei.ascend.*` per ADR-0104):

```text
agent-middleware/
└── src/main/java/
    └── com/huawei/ascend/middleware/
        ├── spi/                            # 5 SPI types (Rule R-D purity)
        │   ├── HookPoint.java              # enum mirroring engine-hooks.v1.yaml (10 entries incl. ON_YIELD rc22)
        │   ├── RuntimeMiddleware.java      # listener interface
        │   ├── HookContext.java
        │   └── HookOutcome.java            # sealed: Proceed | Fail | ShortCircuit
        └── HookDispatcher.java             # runtime dispatcher impl (root of package, not under .spi)
(W2 consumer impls — TokenCounterHook, PiiRedactionHook, CostAttributionHook, LlmSpanEmitterHook — land alongside W2 Telemetry Vertical under `src/main/resources/` when shipped; not present today.)
```

Mode-A (Platform-Centric per ADR-0101): `agent-middleware` lives on the platform.
Mode-B (Business-Centric per ADR-0101): `agent-middleware` lives on the platform (capability-services in-process hooks ride along with the service+engine wherever they land).

## *SPI Interface Appendix* (Rule G-1.1.b — rc22 / ADR-0099)

`agent-middleware` produces 9 SPI packages (cross-validates against `module-metadata.yaml#spi_packages`, `docs/contracts/contract-catalog.md`, `docs/dfx/agent-middleware.yaml`):

| Type FQN | SPI package | Purpose |
|---|---|---|
| `com.huawei.ascend.middleware.spi.HookPoint` | `middleware.spi` | Enum of canonical hook points; mirrors `engine-hooks.v1.yaml#hooks:` list (10 entries as of rc22: BEFORE/AFTER LLM, TOOL, MEMORY; BEFORE_SUSPENSION/RESUME; ON_ERROR; **ON_YIELD** added rc22 per ADR-0100 coexistence) |
| `com.huawei.ascend.middleware.spi.RuntimeMiddleware` | `middleware.spi` | Listener interface; called by `HookDispatcher.fire(point, context)` |
| `com.huawei.ascend.middleware.spi.HookContext` | `middleware.spi` | Per-fire context carrier |
| `com.huawei.ascend.middleware.spi.HookOutcome` | `middleware.spi` | Sealed return type: `Proceed` \| `Fail` \| `ShortCircuit` |
| `com.huawei.ascend.middleware.model.spi.ModelGateway` | `middleware.model.spi` | rc43 — tenant-scoped LLM invocation boundary (ADR-0121); bound to `BEFORE_LLM` / `AFTER_LLM` hooks. rc51 — `stream(ModelInvocation)` default added per ADR-0129 (returns `java.util.stream.Stream<ModelResponseChunk>`; throws by default until W2 binds Spring AI `ChatModel.stream(...)`) |
| `com.huawei.ascend.middleware.model.spi.StructuredOutputConverter` | `middleware.model.spi` | rc51 — generic `StructuredOutputConverter<T>` boundary per ADR-0130; reference adapter `SpringAiBeanOutputConverterAdapter` lands in `agent-service.integration.springai` |
| `com.huawei.ascend.middleware.skill.spi.Skill` | `middleware.skill.spi` | rc43 — unified Tool/Skill SPI with `SkillKind` discriminator (ADR-0127) |
| `com.huawei.ascend.middleware.skill.spi.SkillRegistry` | `middleware.skill.spi` | rc43 — tenant-scoped (tenantId, skillKey) registry |
| `com.huawei.ascend.middleware.memory.spi.MemoryStore` | `middleware.memory.spi` | rc43 — unified MemoryStore<K,V> = MemoryReader + MemoryWriter parameterized by MemoryCategory (ADR-0123) |
| `com.huawei.ascend.middleware.memory.spi.MemoryReader` | `middleware.memory.spi` | rc43 — read-only half of MemoryStore (ADR-0123 CQRS split) |
| `com.huawei.ascend.middleware.memory.spi.MemoryWriter` | `middleware.memory.spi` | rc43 — write-only half of MemoryStore (ADR-0123 CQRS split) |
| `com.huawei.ascend.middleware.memory.spi.SemanticMemoryStore` | `middleware.memory.spi` | rc43 — M3 marker (ADR-0123) |
| `com.huawei.ascend.middleware.memory.spi.KnowledgeMemoryStore` | `middleware.memory.spi` | rc43 — M5 marker (ADR-0123) |
| `com.huawei.ascend.middleware.memory.spi.ConversationMemory` | `middleware.memory.spi` | rc52 — windowed FIFO + token-budget pruning variant `extends MemoryStore<String, ConversationWindow>`; default category `M2_EPISODIC` (ADR-0133) |
| `com.huawei.ascend.middleware.vector.spi.VectorStore` | `middleware.vector.spi` | rc43 — tenant-scoped vector storage + similarity search (ADR-0124) |
| `com.huawei.ascend.middleware.retrieval.spi.Retriever` | `middleware.retrieval.spi` | rc43 — composition layer over VectorStore(s) (ADR-0124) |
| `com.huawei.ascend.middleware.embedding.spi.EmbeddingModel` | `middleware.embedding.spi` | rc43 — text embedding boundary (ADR-0124); `modelVersion()` feeds `MemoryMetadata.embeddingModelVersion` |
| `com.huawei.ascend.middleware.prompt.spi.PromptTemplate` | `middleware.prompt.spi` | rc51 — tenant-scoped prompt-rendering boundary with sealed `PromptTemplateSource` (InlineString / ClasspathResource) per ADR-0131; reference adapter `SpringAiPromptTemplateAdapter` |
| `com.huawei.ascend.middleware.advisor.spi.ChatAdvisor` | `middleware.advisor.spi` | rc53 — interceptor SPI around model invocation per ADR-0132; chain composes via `AdvisorChain.next(AdvisedRequest)`; typed same-package request/response carriers avoid model-SPI imports |
| `com.huawei.ascend.middleware.advisor.spi.AdvisorChain` | `middleware.advisor.spi` | rc51 — chain abstraction passed to `ChatAdvisor.aroundCall(...)` per ADR-0132 |
| `com.huawei.ascend.middleware.advisor.spi.StreamingChatAdvisor` | `middleware.advisor.spi` | rc53 — streaming sibling of `ChatAdvisor`; composes through `StreamingAdvisorChain.proceed(AdvisedRequest)` and `AdvisedStreamChunk` with `advisor-model-hook-order/v1` ordering (ADR-0132) |
| `com.huawei.ascend.middleware.advisor.spi.StreamingAdvisorChain` | `middleware.advisor.spi` | rc52 — continuation abstraction passed to `StreamingChatAdvisor.aroundStream(...)` per ADR-0132 |

(`HookDispatcher` is implementation at the package root, not in `.spi`; not counted as SPI surface. Reference Spring AI adapters for the rc43 SPIs live in `agent-service` under `service.integration.springai` per ADR-0125; rc51 adds `SpringAiBeanOutputConverterAdapter` and `SpringAiPromptTemplateAdapter` to the same package.)

## *L2 Constraint Linkage* (Rule G-1.1.c — rc22 / ADR-0099)

Vacuously green at rc22. The W2 outcome-consumption work (when `HookOutcome.Fail` actually transitions Run to FAILED) MAY warrant an L2 design; if so it MUST include Boundary Contracts.

## Deployment loci (rc22 / ADR-0101)

`deployment_loci: [platform_centric]` — middleware always on the platform (rides with whichever modules host the cross-cutting hooks).

## *Cross-reference to ADR-0100 ON_YIELD hook* (rc22)

The new `HookPoint.ON_YIELD` (added to `engine-hooks.v1.yaml` in rc22 per ADR-0100 Yield/SuspendSignal coexistence) is a cooperative-scheduling hint. Engine fires `ON_YIELD` when it asks to be rescheduled WITHOUT a state-machine transition. Distinct from `SuspendSignal` (checked exception) which remains canonical for state-machine suspension.

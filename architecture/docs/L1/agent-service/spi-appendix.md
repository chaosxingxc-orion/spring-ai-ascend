---
level: L1
view: development
module: agent-service
status: active
authority: "ADR-0143 (rc55 — canonical 4+1 source moved here) + ADR-0099 (rc22 — Rule G-1.1.b SPI Appendix 4-way parity) + Rule R-D (SPI + DFX + TCK co-design + catalog integrity)"
---

# agent-service — SPI Interface Appendix

> Authoring source: `ARCHITECTURE.md` §SPI Interface Appendix + rc53 review file §19, ported in rc55 W5 with corrections:
>
> - **M4** (`F-cross-authority-agreement`): the SPI table uses the canonical `TaskStateStore` name (matches `module-metadata.yaml#spi_packages` + shipped Java `service.task.spi.TaskStateStore`); the legacy `TaskRepository` prose in ARCHITECTURE.md §11 is wrong and corrected during rc55 W2.
> - **M10** (`F-spi-package-bloat-with-carriers`): the rc55 cited surface was MIS-IDENTIFIED in the original audit — `service.runtime.memory.spi` has 1 interface + 0 carriers (clean) per the rc55 W0 sibling sweep. The actual offenders are 12 OTHER `*.spi.*` packages across agent-middleware + agent-runtime + agent-service per the sibling-sweep report; the systemic Java refactor is OUT OF SCOPE for rc55 and DEFERRED to a follow-up impl-mode wave. §3 of this appendix documents the systemic gap.
> - **Rule G-1.1.b** (4-way parity): every active SPI FQN listed below appears in `agent-service/module-metadata.yaml#spi_packages` AND `docs/contracts/contract-catalog.md` §2 Active SPI interfaces AND `docs/dfx/agent-service.yaml#spi_packages` AND exists as a `public interface` `.java` file on disk.
> - **v1.2 absorption (ADR-0155, 2026-05-28)**: §4 added with 5 new SPI interface entries (rows 10–14) and the `InjectionMode` enum carrier; 4-way parity restored in the same PR.

## 1. Active Java SPI interfaces (9)

| # | Interface FQN | SPI package | Status | Cross-module consumer | Authority |
|---|---|---|---|---|---|
| 1 | `com.huawei.ascend.service.runtime.runs.spi.RunRepository` | `service.runtime.runs.spi` | shipped — W0 in-memory ref impl (`InMemoryRunRegistry`); W2 durable Postgres-backed | Layer 4 Control (via ADR-0142 single-owner pinning — never bypasses) | ADR-0088 + ADR-0118 (abstract `updateIfNotTerminal` CAS) + Rule R-C.2.b |
| 2 | `com.huawei.ascend.service.runtime.memory.spi.GraphMemoryRepository` | `service.runtime.memory.spi` | shipped — interface only; `spring-ai-ascend-graphmemory-starter` reference adapter W1 | Layer 5b Translation & Tool-Intercept | ADR-0034 (memory taxonomy) |
| 3 | `com.huawei.ascend.service.runtime.resilience.spi.ResilienceContract` | `service.runtime.resilience.spi` | shipped — W0 Resilience4j-backed impl `DefaultSkillResilienceContract`; per-skill capacity via `YamlResilienceContract` | Layer 4 Control + Layer 5a Engine Dispatch | ADR-0070 + ADR-0080 + ADR-0081 + Rule R-K |
| 4 | `com.huawei.ascend.service.runtime.resilience.spi.SkillCapacityRegistry` | `service.runtime.resilience.spi` | shipped — W0 YAML-backed impl `YamlSkillCapacityRegistry`; `ResilienceAutoConfiguration` exposes as `@ConditionalOnMissingBean` | Layer 4 Control (per Rule R-K admission) | ADR-0070 + ADR-0080 + ADR-0081 |
| 5 | `com.huawei.ascend.service.engine.spi.StatelessEngine` | `service.engine.spi` | implemented_unverified (rc23 per ADR-0100) — pure-function compute SPI + `InMemoryStatelessEngine` reference impl exists; runtime orchestrator wiring deferred | Layer 5a Engine Dispatch (via EngineRegistry.resolve) | ADR-0100 (rc22 5-component decomp) |
| 6 | `com.huawei.ascend.service.session.spi.ContextProjector` | `service.session.spi` | implemented_unverified (rc23 per ADR-0100) — Session-context projection SPI + `InMemoryContextProjector` reference impl exists; durable projection deferred | Layer 5b Translation & Tool-Intercept | ADR-0100 |
| 7 | `com.huawei.ascend.service.task.spi.TaskStateStore` | `service.task.spi` | implemented_unverified (rc23 per ADR-0100) — TaskControlState persistence SPI + tenant-scoped `InMemoryTaskStateStore` reference impl exists; JDBC/RLS deferred | Layer 2 Session & Task Manager | ADR-0100 (canonical name; the legacy "TaskRepository" prose is corrected by rc55 W2 per M4) |
| 8 | `com.huawei.ascend.service.agent.spi.Agent` | `service.agent.spi` | rc43 `design_only` — first-class entity binding model + skills + memory + planner (HTTP-edge customer registration surface) | (deferred — W3 SDK GA) | ADR-0128 |
| 9 | `com.huawei.ascend.service.agent.spi.AgentRegistry` | `service.agent.spi` | rc43 `design_only` — tenant-scoped (tenantId, agentId) index | (deferred — W3 SDK GA) | ADR-0128 |

**4-way parity verification** (Rule G-1.1.b — gate enforcer E167):

| Surface | Where it lives | rc55 audit status |
|---|---|---|
| `agent-service/module-metadata.yaml#spi_packages` | 7 package entries (one per `.spi.*` sub-package) | ✓ rc55 cross-checked; 7 packages declared |
| `docs/contracts/contract-catalog.md` §2 Active SPI interfaces | 9 row entries (one per interface) | ✓ rc55 cross-checked; rows 30, 35, 36, 37, 43, 44, 45, 61, 62 |
| `docs/dfx/agent-service.yaml#spi_packages` | 7 package entries (order-insensitive set match with module-metadata per Rule R-D.e) | ✓ rc55 cross-checked; set-equal to module-metadata |
| On-disk `.java` files | `agent-service/src/main/java/com/huawei/ascend/service/**/spi/*.java` | ✓ rc55 cross-checked; every FQN above resolves to a `public interface` declaration |

## 2. SPI-adjacent structural carriers (records, enums, sealed types — NOT counted in §1)

Per Rule R-D.d (`*.spi.*` packages contain extension interfaces only;
carriers belong in the parent package), the following structural
carriers live in `.spi` packages BUT are NOT extension interfaces and
are NOT included in the 9-interface §1 count. They are listed
separately for clarity.

| Carrier type | Home package | Purpose |
|---|---|---|
| `ResiliencePolicy` | `service.runtime.resilience.spi` | Per-operation policy carrier returned by `ResilienceContract.resolve(...)` |
| `SkillResolution` | `service.runtime.resilience.spi` | Sealed accept/reject decision envelope |
| `SuspendReason` | `service.runtime.resilience.spi` | Sealed reason taxonomy for suspension + rate-limit decisions (W2 will extend with additional variants per ADR-0019) |
| `AgentInvokeRequest` | `service.engine.spi` | Immutable service-to-engine invocation carrier *(schema_shipped per `docs/contracts/agent-invoke-request.v1.yaml` — Java carrier records exist and are test-verified; orchestrator runtime wiring is W2-deferred per ADR-0100; aligned with catalog row 162 per AUD-2026-05-27 AUD-PARITY-7)* |
| `StateDelta` | `service.engine.spi` | Immutable engine result carrier with typed run-transition hint |
| `AgentDefinition`, `AgentInvocation`, `AgentResponse`, `AdvisorBinding`, `OutputContentPolicy`, `SafetyPolicy` | `service.agent.spi` | rc43-introduced Agent-SPI carrier records (per ADR-0128) — `design_only` until W3 SDK GA |
| `Session` aggregate | `service.session` (parent, NOT under `.spi`) | Session record used by the reference projector |
| `Task` aggregate | `service.task` (parent, NOT under `.spi`) | Task record used by the reference task-state store |
| `Run` aggregate (Run + RunStatus + RunStateMachine + RunMode) | `service.runtime.runs` (parent, NOT under `.spi`) | Run aggregate per ADR-0142 single-owner pinning to Layer 2 |
| `IdempotencyRecord` | `service.platform.idempotency` (parent, NOT under `.spi`; canonical per ADR-0057 §2) | Idempotency contract-spine entity per ADR-0057. NB: a dead duplicate exists at `service.runtime.idempotency.IdempotencyRecord` (zero importers, no production wiring) — slated for deletion in the audit-2026-05-27 impl-mode follow-up wave per AUD-2026-05-27 AUD-IDEM-8 (family `F-vocabulary-identity-collision`). |
| `IdempotencyStore` (interface) | `service.platform.idempotency` (parent, NOT under `.spi`) | HTTP-edge contract for idempotent run-create dedup, with 2 production impls (`InMemoryIdempotencyStore` dev, `JdbcIdempotencyStore` real postures). Intentionally NOT under `.spi.` per Rule R-D.d carve-out — governed by ADR-0057 directly rather than by SPI 4-way parity. Therefore not counted in the §1 9-interface set; reviewers seeking the agent-service extension surface for idempotency dedup should treat ADR-0057 as the authority. Per AUD-2026-05-27 AUD-PARITY-4 family `F-spi-package-bloat-with-carriers`. |
| `EvolutionExport` enum | `service.runtime.evolution` (parent, NOT under `.spi`) | rc55 ADR-0145 — discriminator enum for the future sealed `RunEvent` hierarchy *(Rule R-M.e gate is currently design-armed but vacuously true until the Java sealed type lands in a follow-up impl-mode wave)* |

## 3. F-spi-package-bloat-with-carriers — systemic gap deferred (M10 correction)

The rc55 W0 sibling sweep documented that 12 OTHER `*.spi.*` packages
across the repo currently violate Rule R-D.d by containing more
structural carriers than extension interfaces:

| SPI Package | Interfaces | Carriers | Ratio |
|---|---:|---:|---:|
| `engine.planner.spi` | 1 | 11 | 11:1 |
| `middleware.advisor.spi` | 4 | 10 | 2.5:1 |
| `middleware.memory.spi` | 6 | 11 | 1.8:1 |
| `middleware.model.spi` | 2 | 6 | 3:1 |
| `middleware.prompt.spi` | 1 | 2 | 2:1 |
| `middleware.retrieval.spi` | 1 | 2 | 2:1 |
| `middleware.skill.spi` | 2 | 7 | 3.5:1 |
| `middleware.spi` | 1 | 3 | 3:1 |
| `middleware.vector.spi` | 1 | 3 | 3:1 |
| `service.agent.spi` | 2 | 9 | 4.5:1 |
| `service.engine.spi` | 1 | 2 | 2:1 |
| `service.runtime.resilience.spi` | 2 | 3 | 1.5:1 |

**rc55 disposition** (per the sibling-sweep report at
`docs/logs/reviews/2026-05-26-agent-service-l1-sibling-sweep.en.md`):

- The bulk Java refactor (move carriers out of `.spi` per Rule R-D.d)
  is **OUT OF SCOPE for rc55** — rc55 is a design-mode wave; bulk
  carrier-promotion across 12 packages is an impl-mode wave.
- The systemic gap is **DOCUMENTED HERE** so future reviewers don't
  re-discover it.
- A follow-up impl-mode rc wave will execute the carrier promotion +
  ship a gate-rule for Wave 5+ that enforces `(records + sealed +
  enums) ≤ interfaces` per `.spi` package, with grandfather list for
  the current 12 violators if needed.
- The `service.runtime.memory.spi` ORIGINAL rc55 M10 cited surface
  was MIS-IDENTIFIED in the audit notes — actual file count is 1
  (just `GraphMemoryRepository.java`); the actual offender is
  `agent-middleware.memory.spi` (17 files: 6 interfaces + 11
  carriers). The M10 finding is preserved in the family yaml with
  the correction recorded in the sibling-sweep report.

**Why not fix now?** Per the rc55 plan §7 risks: bulk carrier
promotion touches shipped Java and would require ArchUnit refactoring,
test updates, and downstream consumer impact analysis across 12
packages. Doing this in rc55 would blow the design-mode scope. The
follow-up impl-mode wave is the right place; the family yaml +
sibling-sweep report ensure it can't be forgotten.

## 4. v1.2 SPI additions (ADR-0155 — design_only)

| # | Interface FQN | SPI package | Status | Cross-module consumer | Authority |
|---|---|---|---|---|---|
| 10 | `com.huawei.ascend.service.runtime.executor.spi.ExecutorAdapter` | `service.runtime.executor.spi` | design_only — interface placeholder; concrete records (`ExecutionRequest`/`AgentEvent`) ship W2 | Layer 5a Engine Dispatch (EDE-01 contract) | ADR-0155 §4 |
| 11 | `com.huawei.ascend.service.runtime.intercept.spi.PlatformChatClient` | `service.runtime.intercept.spi` | design_only | Layer 5b TTI-03 (Native + Third-party adapter call path) | ADR-0155 §3 |
| 12 | `com.huawei.ascend.service.runtime.intercept.spi.PlatformToolCallback` | `service.runtime.intercept.spi` | design_only | Layer 5b TTI-04 | ADR-0155 |
| 13 | `com.huawei.ascend.service.runtime.intercept.spi.PlatformMemoryProvider` | `service.runtime.intercept.spi` | design_only | Layer 5b TTI-05 (read-only STM-04 projection) | ADR-0155 §3 |
| 14 | `com.huawei.ascend.service.runtime.intercept.spi.PlatformRetriever` | `service.runtime.intercept.spi` | design_only | Layer 5b TTI-06 | ADR-0155 |

**§2 carrier addition:** `InjectionMode` enum lives under
`com.huawei.ascend.service.runtime.executor.spi` per Rule R-D.d
carve-out (single-package home; declares an adapter's wiring choice).

**4-way parity check for v1.2 additions:**

| Surface | Where it lives | Status |
|---|---|---|
| `agent-service/module-metadata.yaml#spi_packages` | 2 new package entries (`service.runtime.executor.spi` + `service.runtime.intercept.spi`) | added in PR 92 absorption |
| `docs/contracts/contract-catalog.md` §2 Active SPI interfaces | 5 new rows | added in PR 92 absorption |
| `docs/dfx/agent-service.yaml#spi_packages` | 2 new package entries (set-equal with module-metadata per Rule R-D.e) | added in PR 92 absorption |
| On-disk `.java` files | 5 `public interface` + 1 enum + 2 `package-info` declarations | added in PR 92 absorption |

## 5. Cross-module SPI consumption

agent-service CONSUMES SPIs from the modules declared in
`module-metadata.yaml#allowed_dependencies`:

| SPI consumed | Source module | Source package | Purpose |
|---|---|---|---|
| `EnginePort`, `Orchestrator`, `RunContext`, `ExecutionContext`, `Checkpointer`, `SuspendSignal`, `TraceContext`, `ExecutorDefinition`, `RunMode` | `agent-bus` | `bus.spi.engine` | Neutral orchestration/engine SPI (re-homed to agent-bus per ADR-0158 — transport-agnostic EnginePort boundary) |
| `ExecutorAdapter`, `GraphExecutor`, `AgentLoopExecutor`, `EngineHookSurface`, `EngineMatchingException` | `agent-runtime` | `engine.spi` | Engine adapter SPI (per ADR-0079 extraction) |
| `EngineRegistry`, `EngineEnvelope` | `agent-runtime` | `engine.runtime` | Engine registry + envelope (relocated to engine.runtime per ADR-0090) |
| `S2cCallbackEnvelope`, `S2cCallbackResponse`, `S2cCallbackTransport` | `agent-bus` | `bus.spi.s2c` | S2C transport SPI (relocated to agent-bus per ADR-0088) |
| `IngressGateway`, `IngressEnvelope` | `agent-bus` | `bus.spi.ingress` | Ingress gateway SPI (per ADR-0089; design_only at W1) |
| `HookPoint`, `HookContext`, `HookOutcome`, `RuntimeMiddleware`, `HookDispatcher` | `agent-middleware` | `middleware.spi` | Hook chain SPI (cross-cutting middleware; per ADR-0073) |
| `Skill`, `SkillRegistry`, `SkillContext`, `SkillKind` | `agent-middleware` | `middleware.skill.spi` | Skill SPI (per ADR-0127) |
| `MemoryStore`, `ConversationMemory`, `KnowledgeMemoryStore`, `SemanticMemoryStore`, `MemoryReader`, `MemoryWriter` | `agent-middleware` | `middleware.memory.spi` | Memory SPI (per ADR-0123) |
| `ModelGateway` + carriers | `agent-middleware` | `middleware.model.spi` | Model invocation SPI (per ADR-0121 + ADR-0129 streaming) |
| `Planner` + carriers | `agent-runtime` | `engine.planner.spi` | Planner SPI (per ADR-0126) |
| `ChatAdvisor`, `AdvisorChain`, `StreamingChatAdvisor` | `agent-middleware` | `middleware.advisor.spi` | Tool-shaping interceptor SPI (per ADR-0132); consumed in Layer 5b |
| `PromptTemplate` + `RenderedPrompt` | `agent-middleware` | `middleware.prompt.spi` | Prompt rendering SPI (per ADR-0131); consumed in Layer 5b |
| `StructuredOutputConverter<T>` | `agent-middleware` | `middleware.model.spi` | Typed-bean extraction SPI (per ADR-0130); consumed in Layer 5b |
| `VectorStore`, `Retriever`, `EmbeddingModel` | `agent-middleware` | `middleware.vector.spi`, `middleware.retrieval.spi`, `middleware.embedding.spi` | Vector / retrieval / embedding SPIs (per ADR-0124) |

## 6. Future SPIs (declared in design; NOT yet on disk at rc55)

| SPI | Module | Package | Wave | Authority |
|---|---|---|---|---|
| `RunEvent` (sealed interface + 10 record variants) | agent-service | `service.runtime.evolution` (parent — NOT under `.spi` per ADR-0145 §decision) | Follow-up impl-mode wave (rc55+1 or later) | ADR-0145 + `docs/contracts/run-event.v1.yaml` (design_only) |
| `DualTrackRouter` SPI | agent-service | `service.orchestrator/` | W2 | ADR-0112 + ADR-0139 narrowed semantics |
| `SandboxExecutor.refuseOverWideGrant(...)` runtime check | agent-middleware (consumed) | (cross-module) | W2 | Rule R-L.b (deferred per `rule-R-L.md#deferred_sub_clauses`) |
| Layer 3 queue Producer/Consumer SPIs | agent-service | `service.queue/` | W4 (or W2 per scheduling) | ADR-0141 + Rule R-E binding |

## 7. Cross-references

- 4-way parity gate: enforcer E167 (`gate/lib/check_l1_spi_appendix.sh`)
  per Rule G-1.1.b. rc55 W5 cross-walked this appendix against the
  other 3 surfaces.
- module-metadata.yaml: `agent-service/module-metadata.yaml#spi_packages`.
- DFX: `docs/dfx/agent-service.yaml#spi_packages`.
- Contract catalog: `docs/contracts/contract-catalog.md` §2 Active SPI interfaces.
- Source: `agent-service/src/main/java/com/huawei/ascend/service/**/spi/*.java`.
<<<<<<<< HEAD:docs/architecture/l0/l1/agent-service/05-contracts/spi-appendix.md
- Development View: [`development.md`](../development.md) §1 + §2 (tree + layer matrix).
- Logical View: [`logical.md`](../logical.md) §6 (vocabulary glossary — distinct mechanisms not aliases).
- Module-root grounding: [`agent-service/ARCHITECTURE.md`](../../../../../../agent-service/ARCHITECTURE.md) §SPI Interface Appendix (legacy table retained for shipped-state context; this canonical appendix takes precedence per ADR-0143).
========
- Development View: [`development.md`](development.md) §1 + §2 (tree + layer matrix).
- Logical View: [`logical.md`](logical.md) §6 (vocabulary glossary — distinct mechanisms not aliases).
- Module-root grounding: [`ARCHITECTURE.md`](ARCHITECTURE.md) §SPI Interface Appendix (legacy table retained for shipped-state context; this canonical appendix takes precedence per ADR-0143).
>>>>>>>> origin/main:architecture/docs/L1/agent-service/spi-appendix.md

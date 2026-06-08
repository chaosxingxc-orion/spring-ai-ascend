# Contract Catalog

> Single source of truth for all public contracts in the spring-ai-ascend platform.
> Version: 0.1.0-SNAPSHOT | Last refreshed: 2026-05-28 (PR 92 absorption — added 14 design_only YAML schemas + 5 design_only SPI interfaces per ADR-0155)

## Rhetorical stance

This catalog is the **runtime promise** surface. A contract here is what the system COMMITS to at runtime — wire shape, route behavior, SPI signature, header semantics — distinct from:

- **`architecture/docs/L0/ARCHITECTURE.md` §4 #1..#65** — declarative architectural constraints (what the platform commits to STRUCTURALLY).
- **`CLAUDE.md` rules** — enforceable engineering rules (how each commitment is enforced).
- **`architecture/docs/L1/<module>{.md,/}`** — L1 module design (how a module realises its slice of the contracts).
- **`docs/governance/architecture-status.yaml#capabilities`** — per-capability shipped/deferred ledger (what's currently active).

Each contract in this catalog has at least one **authority ADR** (the decision that introduced or amended it) and at least one **enforcer** (the gate rule or test that polices its truth). Reading the catalog with this framing prevents conflating "a contract is published" with "a constraint is satisfied" or "a rule is enforced".

---

## 1. HTTP API contracts

Stable W0 routes: `GET /v1/health`, `GET /actuator/health`, `GET /actuator/prometheus` (no auth headers). Shipped W1 routes: `POST /v1/runs` (202 + TaskCursor per Rule R-F Cursor Flow), `GET /v1/runs/{id}`, `POST /v1/runs/{id}/cancel` (200/404/409 for run-owner semantics; 403 only for JWT/header tenant mismatch today) — all require `X-Tenant-Id`; POST routes also require `Idempotency-Key`; W1 adds JWT `tenant_id` claim cross-check against `X-Tenant-Id` (ADR-0040). Implementation: `agent-service/src/main/java/.../web/runs/RunController.java`. Full per-route spec: [http-api-contracts.md](http-api-contracts.md) + `docs/contracts/openapi-v1.yaml`. (rc12 K-ζ updated these rows from prior `Planned W1` per rc11 review P2-1; Rule 104 `openapi_implemented_route_catalog_truth` prevents recurrence.)

**API conventions** (absorbed from `api-conventions.md`): URL major-versioned (`/v1/`); plural nouns; RFC 7807 `application/problem+json` errors with stable `code`; cursor pagination (`?limit=20&cursor=`); `GET`=200, POST-create=201, async=202, DELETE=204; `Idempotency-Key` required on POST/PUT/PATCH in research/prod; `OpenApiContractIT` snapshot-tests spec; SSE streaming reserved W3+.

---

## 2. Java SPI interfaces

**Inclusion rule:** Java `interface` types that represent named public extension points in the current reactor modules; not probes, not data carriers (records / sealed status types / exceptions), not implementations.

SPI impls: thread-safe, no null returns. SPIs that process tenant-owned runtime data MUST carry tenant scope (via explicit `tenantId` argument or `RunContext.tenantId()`). SPI packages import only `java.*` plus same-package sibling carriers; broader historical cross-package SPI residuals are documented in root `architecture/docs/L0/ARCHITECTURE.md §3.7` and must not be used as precedent for new SPI design. japicmp binary-compat from W1.

**Active SPI interfaces (10 total):**

(rc43 baseline: 19 pre-rc43 + 14 rc43 agentic-contract-surface SPI surfaces (Agent + AgentRegistry + ModelGateway + Skill + SkillRegistry + MemoryStore + MemoryReader + MemoryWriter + SemanticMemoryStore + KnowledgeMemoryStore + VectorStore + Retriever + EmbeddingModel + Planner) per ADR-0120 / ADR-0121 / ADR-0122 / ADR-0123 / ADR-0124 / ADR-0125 / ADR-0126 / ADR-0127 / ADR-0128. rc51 + 5 agentic-completeness SPI surfaces (StructuredOutputConverter + PromptTemplate + ChatAdvisor + AdvisorChain + ConversationMemory) per ADR-0129 / ADR-0130 / ADR-0131 / ADR-0132 / ADR-0133. rc51 also adds the `stream(...)` default method to the existing `ModelGateway` per ADR-0129 and supplements `model-invocation.v1.yaml` with the tool-call iteration loop per ADR-0134. ADR-0135 documents the deliberate decision not to add a separate `AgentSession` SPI.)

(rc52 corrective: +2 streaming advisor sibling SPI surfaces (`StreamingChatAdvisor`, `StreamingAdvisorChain`) per ADR-0132. Agent-middleware SPI packages now use same-package carrier types for advisor, conversation-memory, and retrieval contracts so the strict purity rule is no cross-SPI dependencies.)

| Interface | Module | Package | Status |
|---|---|---|---|
| `Checkpointer` | `agent-bus` | `com.huawei.ascend.bus.spi.engine` | shipped — W0 in-memory impl (`InMemoryCheckpointer`, in `agent-service`); relocated to the neutral engine contract per ADR-0158 |
| `Orchestrator` | `agent-bus` | `com.huawei.ascend.bus.spi.engine` | shipped — W0 reference impl (`SyncOrchestrator`, in `agent-service`); relocated to the neutral engine contract per ADR-0158 |
| `EnginePort` | `agent-bus` | `com.huawei.ascend.bus.spi.engine` | shipped — neutral Service/Engine boundary contract; in-process realization `InProcessEnginePort` (agent-runtime) driven by SyncOrchestrator; networked realizations `RpcEnginePort` (Form 1, internal RPC) + `A2aEnginePort` (federation) are design_only EnginePort adapters in `com.huawei.ascend.runtime.orchestration` per ADR-0158 |
| `DefinitionResolver` | `agent-bus` | `com.huawei.ascend.bus.spi.engine` | shipped — bidirectional bridge between the wire-form `DefinitionRef` and the runnable `ExecutorDefinition`; `resolve` is engine-facing, `referenceFor` is service-facing; reference impl `CompositeDefinitionResolver` (agent-service) per ADR-0158 |
| `S2cCallbackTransport` | `agent-bus` | `com.huawei.ascend.bus.spi.s2c` | shipped — W2.x; `InMemoryS2cCallbackTransport` reference (ADR-0074); relocated to agent-bus per ADR-0088 |
| `AgentRuntimeHandler` | `agent-runtime` | `com.huawei.ascend.runtime.engine.spi` | shipped — the single framework-neutral runtime SPI: runs one agent and surfaces its output (openJiuwen first); per the agent-runtime pure rebuild (Doc 2). Base class `AbstractAgentRuntimeHandler` + result carrier `AgentExecutionResult` ship alongside in the same package |
| `StreamAdapter` | `agent-runtime` | `com.huawei.ascend.runtime.engine.spi` | shipped — adapts a framework's native result stream into the neutral `AgentExecutionResult` stream |

**SPI count by module (shipped surface; the agent-runtime SPI surface is the framework-neutral `engine.spi` pair `AgentRuntimeHandler` + `StreamAdapter`):**

| Module | SPI interfaces |
|---|---|
| `agent-service` | 0 — serviceization façade skeleton; registration/discovery SPI deferred to a dedicated ADR (ADR-0159) |
| `agent-runtime` | 2 (`AgentRuntimeHandler`, `StreamAdapter`) |
| `agent-bus` | 8 (`IngressGateway`, `S2cCallbackTransport`, `ReflectionEnvelopeRouter`, `FederationGateway`, `Checkpointer`, `Orchestrator`, `EnginePort`, `DefinitionResolver`) |

**Per-SPI tenant scope (canonical post-ADR-0044):**

| SPI | Scope | Tenant carrier | Planned scope evolution |
|---|---|---|---|
| `Checkpointer` | run-scoped | implicit via `runId` uniqueness | unchanged (ADR-0027) |
| `Orchestrator` | tenant-scoped | explicit `tenantId` arg in `run(runId, tenantId, …)` | unchanged |
| `S2cCallbackTransport` | tenant-scoped | tenant resolved out-of-band via `S2cCallbackTransport` registry binding at the wrapping Run boundary, per ADR-0074 §Consequences (the `S2cCallbackEnvelope` Java record carries 8 components `{callbackId, serverRunId, capabilityRef, requestPayload, traceId, idempotencyKey, deadline, requestAttributes}` and does NOT include an in-band `tenantId` field); preferred fix per Rule R-C.c is to add `tenantId` to the Java record — deferred to impl-mode follow-up wave (AUD-2026-05-27 AUD-EVT-6 / family `F-cross-authority-tenant-scope-claim-without-field`) | unchanged — relocated to agent-bus per ADR-0088 |
| `AgentRuntimeHandler` | tenant-scoped | via `AgentExecutionContext` carrying `EngineExecutionScope.tenantId()` | unchanged |

**Structural carriers (records / sealed interfaces / sealed status types / exceptions — not SPI interfaces but part of the contract surface):**

| Type | Module | Notes |
|---|---|---|
| `RunMode` | `agent-bus` (`...bus.spi.engine`) | GRAPH \| AGENT_LOOP discriminator; co-located with orchestration SPI per ADR-0088 |
| `RunContext` | `agent-bus` (`...bus.spi.engine`) | Per-run context interface; exposes `tenantId()`, `runId()` |
| `TraceContext` | `agent-bus` (`...bus.spi.engine`) | Trace-id / span carrier interface |
| `ExecutorDefinition` | `agent-bus` (`...bus.spi.engine`) | Sealed: `GraphDefinition` \| `AgentLoopDefinition` |
| `DefinitionRef` | `agent-bus` (`...bus.spi.engine`) | Serializable capability-name reference; the wire-form of an `ExecutorDefinition` a remote engine resolves to its own definition (engine-port.v1.yaml) per ADR-0158 |
| `SuspendSignal` | `agent-bus` (`...bus.spi.engine`) | Checked-exception interrupt primitive; carries `forClientCallback(...)` variant per ADR-0074 |
| `S2cCallbackEnvelope` / `S2cCallbackResponse` | `agent-bus` (`...bus.spi.s2c`) | Six mandatory request fields per ADR-0074; relocated per ADR-0088 |

**Deferred / Promoted Design Names:**

| Surface | Current status | Authority |
|---|---|---|
| `Skill` / `SkillRegistry` / `SkillContext` | promoted to active SPI in rc43; `SkillResourceMatrix` remains W2 admission-policy design | ADR-0127 / ADR-0122; ADR-0052 |
| `AgentRegistry` | promoted to active SPI in rc43; `RemoteAgentClient` remains post-W4 deferred | ADR-0128; ADR-0016 |
| `RunDispatcher` | deferred W2 dispatcher shape | ADR-0031 |
| `CapabilityRegistry` | deferred W2 capability registry shape | ADR-0021 |

Seven previously listed SDK SPI interfaces were deleted in the 2026-05-12 Occam pass (see `architecture-status.yaml` row `sdk_spi_starters`). They are no longer part of the contract surface.

---

## 3. YAML domain contracts

Schema-first domain contracts (Rule M-2.a, formerly Rule 48). Each YAML file is the single source of truth for the named protocol; Java records/enums mirror the schema and validate fields on construction.

| Contract | Path | Status | Authority |
|---|---|---|---|
| `engine-envelope.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0072 — retired by the pure rebuild; design reference only (no EngineRegistry runtime) |
| `engine-hooks.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0073 — retired by the pure rebuild; design reference only (no HookPoint/HookDispatcher runtime) |
| `engine-port.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0158 (transport-agnostic Service/Engine boundary wire shape; in-process realization = the Java `EnginePort` interface; networked = internal-RPC + A2A) |
| `s2c-callback.v1.yaml` | `docs/contracts/` | `runtime_enforced` | ADR-0074 (Rule R-M.d); java types in `agent-bus.bus.spi.s2c` per ADR-0088 |
| `ingress-envelope.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0089 (Rule R-I.b); runtime binding W3+ with agent-client SDK |
| `plan-projection.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0032 (planner contract minimal); ADR-0052 (`SkillResourceMatrix`); rc4 review P1-3 amendment |
| `agent-invoke-request.v1.yaml` | `docs/contracts/` | `schema_shipped` | ADR-0100 (agent-service decomp); Java carrier records exist and are test-verified; runtime_enforced=false until the first orchestrator path constructs `AgentInvokeRequest` and invokes `StatelessEngine` |
| `reflection-envelope.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0102 (rc22 — online evolution duality); S2C envelope for hot-patch; runtime impl rc26 |
| `a2a-envelope.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0100 (rc25 — A2A protocol contract-only adoption); NO SDK dep per Rejection 3 |
| `backpressure-request.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0100 (rc25 — bus control-track backpressure channel); runtime impl with BackpressureRequestEmitter SPI |
| `federation-envelope.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0101 (rc26 — Mode B Business-Centric federation wire shape); broker choice deferred to separate ADR |
| `evolution-scope.v1.yaml` | `docs/governance/` | `schema_shipped` | ADR-0077 (Rule R-M.e) |
| `model-invocation.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0121 (rc43 — ModelGateway SPI; runtime_enforced when W2 LLM gateway ships) |
| `skill-definition.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0127 (rc43 — unified Skill SPI; extends ADR-0030); ADR-0122 (Tool-Skill resolution Path b) |
| `memory-store.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0123 (rc43 — Memory unified SPI); ADR-0034 (taxonomy); ADR-0051 (ownership boundary) |
| `vector-store.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0124 (rc43 — Vector / Retrieval / Embedding SPIs); ADR-0125 (Spring AI canonical boundary) |
| `planning-request.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0126 (rc43 — Planner SPI; extends ADR-0032); runtime_enforced when W4 planner ships |
| `plan.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0126 (rc43 — Planner output DAG; distinct from `plan-projection.v1.yaml` scheduler INPUT) |
| `agent-definition.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0128 (rc43 — Agent first-class entity SPI); runtime_enforced when W3 SDK GA ships |
| `model-streaming.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0129 (rc51 — Streaming-aware `ModelGateway.stream(...)`; runtime_enforced when W2 LLM gateway wires Spring AI `ChatModel.stream(...)`) |
| `structured-output.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0130 (rc51 — `StructuredOutputConverter<T>` SPI; reference adapter wraps Spring AI `BeanOutputConverter`) |
| `prompt-template.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0131 (rc51 — `PromptTemplate` SPI; reference adapter wraps Spring AI `PromptTemplate`) |
| `chat-advisor.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0132 (rc53 — `ChatAdvisor` + `StreamingChatAdvisor` interceptor SPIs; typed same-package advisor carriers avoid model-SPI dependency; `advisor-model-hook-order/v1` binds ordering relative to `BEFORE_LLM` / `AFTER_LLM`) |
| `run-event.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0145 (rc55 — sealed RunEvent hierarchy specification; closes F-discriminator-without-discriminated-type for EvolutionExport enum; 10 variants cover S1-S5 scenarios; runtime_enforced when the Java sealed interface + 10 record variants land in a follow-up impl-mode wave) |
| `audit-trail.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0156 (v1.0 financial MVP ship-blocker — PC-003 / Persona-F; immutable hash-chained regulator-grade audit-trail schema covering run_admitted / run_status_transition / tool_call_started / tool_call_completed / model_invocation / sandbox_violation / identity_propagation event types; storage backend = Postgres + RLS + append-only trigger; regulatory mapping to JR/T 0223-2021 + 等保 2.0/3.0 + SR 11-7; runtime_enforced when storage wiring + checksum-chain verifier land in a follow-up impl-mode wave) |
| `iam-bridge.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0156 (v1.0 financial MVP ship-blocker — PC-003 / Persona-F + Persona-D; OAuth2/OIDC bridge for agent identity delegation — enforces §4 #56 tenant-claim cross-check at admission, propagates `User-Context-Token` header to downstream business-system calls so the loan officer's identity reaches the core-banking system rather than the service principal; first-class adapters for Keycloak / Okta / Azure AD / Auth0 / domestic OIDC; runtime_enforced when JwtTenantValidator filter + IamBridge SPI + UserContextTokenPropagationFilter land in a follow-up impl-mode wave) |
| `cost-governance.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0156 (v1.0 financial MVP ship-blocker — PC-003 / Persona-A + Persona-B + Persona-D; per-(tenant, agent) token budget schema + SpendLogEntry record (LiteLLM_SpendLogs-shape) + enforcement-mode taxonomy block / degrade / notify; integration points = `BEFORE_LLM` pre-call budget check + `AFTER_LLM` post-call spend record per Rule R-M.c HookPoint; interlocks with Rule R-K Skill Capacity Matrix so capacity + budget reject through the same `SkillResolution.reject(SuspendReason.RateLimited)` path; conservative v1.0 default budget shipped under `docs/governance/skill-capacity.yaml#v1_0_financial_default_budget`; runtime_enforced when `TokenBudgetStore` + `SpendLogStore` SPIs + the two HookPoint wirings + ArchUnit no-orphan-model-call test land in a follow-up impl-mode wave) |
| `access-intent.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (M1 AL-03 normalised request shape; converges A2A + MQ ingress before crossing module boundaries) |
| `control-event.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (IEQ-02 envelope; includes RESUME_ACCEPTED + INTERRUPT_REGISTERED kinds from v1.2 reversal) |
| `work-item.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (IEQ-03 envelope carrying engine-tick / tool-invoke / resume-tick payload refs) |
| `execution-request.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (ExecutorAdapter.execute input carrier) |
| `agent-event.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (ExecutorAdapter.execute output stream; NOT to be confused with sealed RunEvent in run-event.v1.yaml) |
| `governed-messages.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 §3 (replaces v1-draft BuiltPrompt; M6 TTI-02 output of boundary-treated messages) |
| `config-snapshot-ref.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (Run-time immutable config binding; STM-07 carrier) |
| `correlation-record.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (STM-06 cross-Run handle union; LocalChildRun or RemoteAgent) |
| `interrupt-registration.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 §1 (H1 reversal — TTI-09 produces, TCC-06A consumes via ControlEvent.payloadRef) |
| `error-class.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (EDE-09 14-value platform-wide error taxonomy; M4 + M6 consume) |
| `intercept-request.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (TTI-01 unified entry envelope across 5 resource kinds) |
| `tool-result.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (TTI-04 normalised tool result; never carries vendor SDK objects) |
| `checkpoint-record.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (STM-05 recoverable boundary; sideEffect ∈ {NONE, PARTIAL, COMMITTED}) |
| `session-snapshot.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 §3 (PlatformMemoryProvider read projection of STM-04) |

Note: `evolution-scope.v1.yaml` lives under `docs/governance/`, not `docs/contracts/`, because it indexes governance-plane export rules rather than a wire/Java contract.

---

## 4. Configuration contracts

**Absorbed from `configuration-contracts.md`**: All properties under `springai.ascend.*`; `app.posture={dev,research,prod}` read once at boot (dev=permissive, research/prod=fail-closed). Each starter exposes `springai.ascend.<domain>.enabled`. Sidecar adapters (`graphmemory`) default `enabled=false`. The `base-url` / `api-key` knobs on graphmemory are **RESERVED for the W1 Graphiti REST adapter** (ADR-0034) — at W0 they are accepted but not consumed (orphan-config Rule 3 exemption flagged via `@Deprecated(forRemoval=false)` on the property fields per v2.0.0-rc3 cross-constraint audit α-8 / P1-7).

**Absorbed from `contract-evolution-policy.md`**: Config deprecation = N+2 release cycle. HTTP /v1 stays active after /v2 (research: 90 days, prod: 180 days). Breaking-change checklist required before any contract-surface PR merges.

---

## 5. Telemetry contract (absorbed from `telemetry-contracts.md`)

Counter: `springai_ascend_<domain>_<subject>_total`. Timer: `springai_ascend_<domain>_<operation>_seconds`. High-cardinality labels (`tenant_id`, `run_id`, `user_id`) forbidden on Prometheus; use structured JSON logs. Cardinality cap: 1 000 (research) / 10 000 (prod). Key counters: `*_default_impl_not_configured_total{spi, method}`, `filter_errors_total{filter, reason}`, `idempotency_{claimed,replayed,conflict,error}_total`.

---

## 6. SDK versioning (absorbed from `sdk-versioning.md`)

SemVer from 1.0.0: PATCH=fix, MINOR=additive, MAJOR=breaking. Stable surface: starter artifacts, SPI interfaces, Spring Boot property keys. Deprecate with `@Deprecated` in current MINOR; remove in next MAJOR. All deps pinned to exact patch in `spring-ai-ascend-dependencies` BoM. Spring AI 2.0.0-M5; CI forces upgrade after 2026-08-01. Current maturity: SPI=L1, HTTP /v1=L2, Config=L1, Telemetry=L1.

---

## 7. Maven BoM

`com.huawei.ascend:spring-ai-ascend-dependencies:0.1.0-SNAPSHOT` — active reactor modules (8 per `architecture-status.yaml#repository_counts.reactor_modules`):

| Artifact | Plane (P-I) | Status |
|---|---|---|
| `spring-ai-ascend-parent` | none | Parent POM (not a reactor row by itself; declared via `<parent>`) |
| `spring-ai-ascend-dependencies` | none | BoM (dependency management) |
| `agent-service` | compute_control | Enterprise serviceization façade (skeleton) — registration/discovery driving runtime-built Agent instances via agent-runtime; the runtime SDK formerly hosted here was relocated to agent-runtime per ADR-0159 |
| `agent-runtime` | compute_control | Run-owning runtime SDK: framework-neutral engine (`engine.spi.AgentRuntimeHandler` + `StreamAdapter`, openJiuwen adapter) + Run lifecycle + engine dispatch + session + task-centric control + internal event queue + access (A2A) + bootable runtime app (`app.RuntimeApp` / `LocalA2aRuntimeHost`); consumes the neutral `bus.spi.engine` boundary — ADR-0159 |
| `agent-bus` | bus_state | Active cross-plane control surfaces: `bus.spi.ingress.IngressGateway` (ADR-0089) + `bus.spi.s2c.S2cCallbackTransport` (ADR-0074 + ADR-0088) + neutral orchestration/engine SPI `bus.spi.engine` (EnginePort + RunMode + Checkpointer + Orchestrator + RunContext + SuspendSignal + TraceContext + ExecutorDefinition + ExecutionContext; ADR-0158). Workflow primitives W2 per ADR-0050 |

**Module history (rc12 → rc13 reactor count: 9 → 8)**

- Pre-Phase-C era modules `agent-platform` + `agent-runtime` were merged into `agent-service` per **ADR-0078** (consolidation, 2026-05-18).
- **ADR-0079** (2026-05-18) introduced a transient shared kernel module `agent-runtime-core` to host SPI + kernel types shared by `agent-service` and `agent-runtime`, breaking the engine ↔ service back-dep cycle.
- **ADR-0088** (2026-05-20, rc13) **dissolved** `agent-runtime-core` and redistributed its 16 sources to semantic-home modules: `Run`/`RunStatus`/`RunStateMachine`/`RunRepository`/`IdempotencyRecord` back to `agent-service`; `RunMode` + 6 orchestration SPI types into `agent-runtime.engine.orchestration.spi`; 3 S2C transport types into `agent-bus.bus.spi.s2c`. Symmetric with **ADR-0089** which adds `agent-bus.bus.spi.ingress.IngressGateway` as the C2S cross-plane surface — Bus & State Hub plane now owns cross-plane traffic in both directions.
- **ADR-0158** **re-homed** the neutral orchestration/engine SPI from `agent-runtime.engine.orchestration.spi` to `agent-bus.bus.spi.engine` (transport-agnostic EnginePort boundary: `EnginePort` + `RunMode` + `Orchestrator` + `RunContext` + `ExecutionContext` + `SuspendSignal` + `Checkpointer` + `TraceContext` + `ExecutorDefinition`). The Bus & State Hub plane owns the neutral execution contract; `agent-runtime` consumes the `bus.spi.engine` carriers.
- The agent-runtime pure rebuild retired `agent-middleware` + the unreached engine design-island and returned the reactor to 4 modules (`agent-runtime`, `agent-service`, `agent-bus`, BoM).
- Pre-2026-05-12 starters (`-memory`, `-skills`, `-knowledge`, `-governance`, `-persistence`, `-resilience`, `-mem0`, `-docling`, `-langchain4j-profile`) were deleted in the 2026-05-12 Occam pass.

---

*See also*: `docs/telemetry/policy.md` · `docs/cross-cutting/posture-model.md`

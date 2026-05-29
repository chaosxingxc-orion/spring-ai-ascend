# Agent Service EngineeringFrame Dossier

Date: 2026-05-29
Status: review draft
Audience: architecture owners, engineering leads, AI-agent workflow owners
Authority context: ADR-0157 EngineeringFrame ontology, ADR-0138 Agent Service five-layer L1 ratification, ADR-0140 Engine Adapter split, ADR-0141 Internal Event Queue design-only, ADR-0142 Run aggregate single owner, ADR-0144 Layer-to-package matrix, ADR-0155 Agent Service v1.2 internal module design.

## 1. Root-Cause Statement

The current Agent Service inventory still mixes structure and value: six ADR-0138 layer-level "features" in `architecture/features/features.dsl` are actually durable structural carriers, while real product features should remain demand/value threads that traverse those carriers. The concrete evidence is in `architecture/features/features.dsl`, where `FEAT-AGENT-SERVICE-ACCESS-LAYER`, `FEAT-AGENT-SERVICE-TASK-CENTRIC-CONTROL`, `FEAT-AGENT-SERVICE-SESSION-TASK-MANAGER`, `FEAT-AGENT-SERVICE-INTERNAL-EVENT-QUEUE`, `FEAT-AGENT-SERVICE-ENGINE-DISPATCH-EXECUTION`, and `FEAT-AGENT-SERVICE-TRANSLATION-TOOL-INTERCEPT` are tagged as `SAA Feature` even though their descriptions and L1 docs describe internal layers, not customer value threads.

Strongest interpretation: Agent Service needs a first-class structural map that AI agents can read before the feature corpus: `SAA Module -> SAA EngineeringFrame -> SAA FunctionPoint`, while `SAA Feature` remains on the value axis and connects to this map through `requires FunctionPoint` and `traverses EngineeringFrame`.

## 2. Evidence Already Present

Generated facts establish that `agent-service` is a domain module in the `compute_control` plane with allowed dependencies on `agent-execution-engine`, `agent-bus`, and `agent-middleware` (`build-module/agent-service`). This makes it a control-plane service, not a generic application module.

The canonical L1 logical view already decomposes Agent Service into six durable structural areas:

1. Access Layer.
2. Session and Task Manager.
3. Internal Event Queue.
4. Task-Centric Control Layer.
5. Engine Dispatch and Execution.
6. Translation and Tool-Intercept.

The development view maps those areas to concrete package homes:

- Access: `service.dispatcher`, `service.platform.web`, `service.platform.idempotency`, `service.platform.tenant`, `service.platform.auth`, `service.platform.observability`.
- State: `service.runtime.runs`, `service.task`, `service.session`, `service.runtime.idempotency`.
- Queue: `service.queue` design-only.
- Control: `service.orchestrator`, `service.runtime.orchestration`, `service.runtime.posture`.
- Engine dispatch: `service.engine.adapter`, `service.engine.spi`, consumed `agent-execution-engine.engine.spi.*`.
- Translation/intercept: `service.integration.springai`, `service.session.spi.ContextProjector`, `service.runtime.intercept.spi`.

The current `architecture/features/engineering-frames.dsl` already introduces the ontology and non-Agent-Service frames, but it has not yet materialized the six Agent Service frames. ADR-0157 explicitly says those six layer features should be re-tagged into EngineeringFrames.

## 3. Proposed Agent Service EngineeringFrames

The first Agent Service frame set should not invent a new decomposition. It should lift the existing L1 structure into the ADR-0157 structural axis.

| Frame ID | Element Variable | Display Name | Status | Source Layer |
|---|---|---|---|---|
| `EF-ACCESS-ADMISSION` | `efAccessAdmission` | Access Admission Frame | shipped | Access Layer |
| `EF-SESSION-TASK-STATE` | `efSessionTaskState` | Session Task State Frame | shipped | Session and Task Manager |
| `EF-INTERNAL-EVENT-QUEUE` | `efInternalEventQueue` | Internal Event Queue Frame | design_only | Internal Event Queue |
| `EF-TASK-CONTROL` | `efTaskControl` | Task Control Frame | shipped | Task-Centric Control Layer |
| `EF-ENGINE-DISPATCH` | `efAgentServiceEngineDispatch` | Agent Service Engine Dispatch Frame | shipped / partially design_only | Engine Dispatch and Execution |
| `EF-TRANSLATION-INTERCEPT` | `efTranslationIntercept` | Translation Intercept Frame | shipped / partially design_only | Translation and Tool-Intercept |

These frames are claim-agnostic. They should not carry `saa.productClaim`. Product claims bind to Feature elements, which then traverse frames and require function points.

## 4. Frame Dossiers

### 4.1 EF-ACCESS-ADMISSION

Definition: The inbound admission and protocol-normalization frame for Agent Service. It accepts external or peer requests, binds tenant/auth/trace/idempotency context, and emits normalized service intents without owning runtime state.

Owns:

- Protocol ingress convergence for HTTP, A2A, MQ, SSE, polling, resume, cancel, and callback ingress.
- Tenant, auth, trace, and idempotency binding before any state write.
- Error envelope shaping and protocol-level response projection.
- Boundary governance: clients may enter through this frame but must not directly reach engines, repositories, middleware, or bus topics.

Does not own:

- Run/Task/Session state ownership.
- Runtime orchestration decisions.
- Engine dispatch or executor lifecycle.
- Model/tool translation.

Existing code facts:

- `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller`
- `code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfilter`
- `code-symbol/com-huawei-ascend-service-platform-tenant-jwttenantclaimcrosscheck`
- `code-symbol/com-huawei-ascend-service-platform-observability-traceextractfilter`

Existing contract facts:

- `contract-op/createrun`
- `contract-op/getrun`
- `contract-op/cancelrun`
- `contract-yaml/ingress-envelope` (design_only)
- `docs/contracts/access-intent.v1.yaml` (design_only, ADR-0155, listed in `contract-catalog.md`)

Existing tests:

- `test/com-huawei-ascend-service-platform-web-runs-runhttpcontractit`
- `test/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfiltertest`
- `test/com-huawei-ascend-service-platform-idempotency-idempotencystoretest`
- `test/com-huawei-ascend-service-platform-tenant-jwttenantclaimcrosschecktest`
- `test/com-huawei-ascend-service-platform-observability-traceextractfilterit`

Anchored shipped FunctionPoints:

- `FP-CREATE-RUN`
- `FP-CANCEL-RUN`
- `FP-GET-RUN-STATUS`
- `FP-IDEMPOTENCY-CLAIM`
- `FP-TENANT-CROSS-CHECK`

Anchored design-only FunctionPoints:

- `FP-A2A-MESSAGE-SEND`
- `FP-A2A-TASKS-CANCEL`
- `FP-A2A-TASKS-RESUBSCRIBE`
- `FP-MQ-INBOUND`

Track B rule:

- Demand may add new ingress operations only if they normalize into an existing admission intent and do not bypass tenant/auth/idempotency/trace binding.
- Demand must escalate to ADR if it requires a new ingress authority model, a direct engine path, or a new protocol that cannot normalize into AccessIntent.

OSS comparison:

- AutoGen separates Studio/AgentChat/Core/Extensions; external entry surfaces are not the same as the event-driven core.
- Dify separates Backend API from agent backend execution.
- OpenHands separates frontend/API interaction from backend runtime execution.

### 4.2 EF-SESSION-TASK-STATE

Definition: The state ownership frame for Run, Task, Session, idempotency record, correlation record, response snapshot, and tenant-scoped persistence. It owns the durable truth that Access and Control read or mutate through sanctioned APIs.

Owns:

- Run aggregate and RunStatus state machine.
- Atomic `RunRepository.updateIfNotTerminal(...)` CAS transition.
- Task control state and TaskStateStore.
- Session context state and ContextProjector inputs.
- Correlation records for child run, remote agent, callback, trace, and tenant linkage.
- Response snapshot writeback after terminal state.
- Tenant-first persistence and lifecycle audit material.

Does not own:

- Protocol parsing or external error mapping.
- Orchestration decisions.
- Executor implementation.
- Prompt/tool/model translation.

Existing code facts:

- `code-symbol/com-huawei-ascend-service-runtime-runs-spi-runrepository`
- `code-symbol/com-huawei-ascend-service-runtime-runs-runstatemachine`
- `code-symbol/com-huawei-ascend-service-runtime-orchestration-inmemory-inmemoryrunregistry`
- `code-symbol/com-huawei-ascend-service-task-inmemorytaskstatestore`
- `code-symbol/com-huawei-ascend-service-session-inmemorycontextprojector`
- `code-symbol/com-huawei-ascend-service-session-spi-contextprojector`

Existing and near-term contracts:

- `contract-yaml/run-event` (design_only)
- `docs/contracts/correlation-record.v1.yaml` (design_only, ADR-0155)
- `docs/contracts/config-snapshot-ref.v1.yaml` (design_only, ADR-0155)
- `docs/contracts/checkpoint-record.v1.yaml` (design_only, ADR-0155)
- `docs/contracts/session-snapshot.v1.yaml` (design_only, ADR-0155)

Existing tests:

- `test/com-huawei-ascend-service-runtime-architecture-runrepositoryatomiccontracttest`
- `test/com-huawei-ascend-service-runtime-architecture-runrepositorysaveguardtest`
- `test/com-huawei-ascend-service-runtime-runs-runstatemachinetest`
- `test/com-huawei-ascend-service-runtime-runs-runstatemachinelibrarytest`
- `test/com-huawei-ascend-service-task-inmemorytaskstatestoretest`
- `test/com-huawei-ascend-service-session-inmemorycontextprojectortest`

Anchored shipped FunctionPoints:

- `FP-RUN-STATE-TRANSITION`

Candidate FunctionPoints to add:

- `FP-TASK-STATE-STORE`
- `FP-SESSION-CONTEXT-PROJECTION`
- `FP-CORRELATION-RECORD`
- `FP-RESPONSE-SNAPSHOT-WRITEBACK`
- `FP-CONFIG-SNAPSHOT-REFERENCE`
- `FP-CHECKPOINT-RECORD`

Track B rule:

- Demand may add state projections, query shapes, or additional record fields only when they preserve tenantId-first ownership and CAS semantics.
- Demand must escalate to ADR if it introduces a second Run writer, cross-tenant state access, a new aggregate owner, or a state machine branch that invalidates existing DFA guarantees.

OSS comparison:

- LangGraph makes checkpointing a separate library family, showing that state/checkpoint ownership is a stable structural concern.
- Temporal and Conductor patterns reinforce durable event/state history as a separate concern from request ingress.
- AutoGen Core uses event-driven stateful workflows separate from high-level AgentChat surface.

### 4.3 EF-INTERNAL-EVENT-QUEUE

Definition: The internal eventing and channel-binding frame for Agent Service. It is design-only today but already has a published boundary contract in L1 docs and ADR-0155 contracts.

Owns:

- RunEvent envelope and routeable event material.
- Producer/consumer, lease, ack, retry, dead-letter, dedup, and heartbeat relationships.
- Three-track channel binding: control, data, rhythm.
- SSE/polling projection views for Access.
- Resume sweep, timeout, heartbeat, and long-running rhythm.
- Remote and child completion routing.
- Configuration drift and audit events.

Does not own:

- External protocol surface.
- State mutation truth.
- Orchestrator decision logic.
- Cross-service bus implementation, which remains in `agent-bus`.

Existing contracts:

- `contract-yaml/run-event` (design_only)
- `docs/contracts/control-event.v1.yaml` (design_only, ADR-0155)
- `docs/contracts/work-item.v1.yaml` (design_only, ADR-0155)
- `docs/contracts/agent-event.v1.yaml` (design_only, ADR-0155)

Existing code facts:

- No code home yet. `service.queue/` is explicitly design-only in L1.

Existing tests:

- No direct production test fact yet. Future tests should be added with the first queue implementation.

Candidate FunctionPoints to add:

- `FP-RUN-EVENT-ROUTING`
- `FP-CONTROL-EVENT-ROUTING`
- `FP-WORK-ITEM-DISPATCH`
- `FP-RESUME-SWEEP`
- `FP-DEAD-LETTER`
- `FP-SSE-POLLING-PROJECTION`
- `FP-BOUNDED-BUFFER-REJECTION`

Track B rule:

- Demand may consume projected event streams or add event kinds only if they fit existing channel intent and event contracts.
- Demand must escalate to ADR if it requires a fourth channel, cross-channel ordering guarantees, a new durability tier that changes the queue contract, or bypasses `agent-bus` channel isolation.

OSS comparison:

- AutoGen Core is explicitly event-driven.
- Temporal separates signals, timers, and workflow history.
- Conductor separates task queues, worker polling, timeout, and dead-letter behavior.

### 4.4 EF-TASK-CONTROL

Definition: The orchestration decision frame. It translates state, engine outputs, middleware outcomes, suspend signals, resume signals, child completions, and cancel requests into sanctioned transition intents.

Owns:

- Orchestrator control loop.
- Fast/Slow Path routing decision as runtime execution semantics, not access mode.
- Suspend/resume semantics.
- RuntimeMiddleware governance via HookPoint dispatch.
- Rollback/retry/attempt classification.
- Cancel/complete race classification.
- Client-hosted skill dispatch via S2C instead of direct engine-to-client access.
- Sub-agent and third-party join control.
- Same-remote-invocation recovery.
- Per-run virtual-actor serialization and RESUME_ACCEPTED handling.

Does not own:

- State storage itself.
- Engine adapter implementation.
- Prompt/model/tool translation.
- Ingress protocol parsing.

Existing code facts:

- `code-symbol/com-huawei-ascend-service-runtime-orchestration-inmemory-syncorchestrator`
- `code-symbol/com-huawei-ascend-service-runtime-orchestration-inmemory-inmemoryrunregistry` (consumed through state frame)
- `code-symbol/com-huawei-ascend-service-runtime-s2c-inmemorys2ccallbacktransport`

Existing contract facts:

- `contract-yaml/s2c-callback`
- `contract-yaml/engine-hooks`
- `contract-yaml/run-event` (design_only)
- `docs/contracts/control-event.v1.yaml` (design_only, ADR-0155)
- `docs/contracts/interrupt-registration.v1.yaml` (design_only, ADR-0155)

Existing tests:

- `test/com-huawei-ascend-engine-runtime-runtimemiddlewareinterceptshooksit`
- `test/com-huawei-ascend-service-runtime-s2c-s2ccallbackroundtripit`
- `test/com-huawei-ascend-service-runtime-s2c-s2ccallbackenvelopevalidationtest`
- `test/com-huawei-ascend-service-runtime-runs-runstatemachinetest`

Anchored shipped FunctionPoints:

- `FP-SUSPEND-RESUME`
- `FP-CHILD-RUN-SPAWN`

Candidate FunctionPoints to add:

- `FP-S2C-CALLBACK-HANDLING` (Agent Service side, distinct from `agent-bus` transport)
- `FP-CANCEL-RACE-ARBITRATION`
- `FP-RESUME-ACCEPTED-HANDLER`
- `FP-RUNTIME-MIDDLEWARE-GOVERNANCE`
- `FP-REMOTE-JOIN-CONTROL`
- `FP-PER-RUN-ACTOR-SERIALIZATION`

Track B rule:

- Demand may add new control decisions if they still use state-frame CAS and emit contract-bound events.
- Demand must escalate to ADR if it introduces direct state writes, direct middleware calls from executors, direct client calls from engines, or a new execution mode that bypasses the orchestration decision loop.

OSS comparison:

- LangGraph runner loop, interrupt/resume, and checkpoint resume map strongly here.
- OpenAI Agents runner and tool approval fit the same control-loop category.
- Temporal workflow execution loop and signal handling are analogous.

### 4.5 EF-ENGINE-DISPATCH

Definition: The Agent Service host-side execution adapter frame. It consumes the engine boundary from `agent-execution-engine`, invokes registered adapters, handles executor lifecycle forms, and guarantees that no raw vendor execution behavior crosses upward.

Owns:

- Agent Service host-side adapter invocation and lifecycle.
- StatelessEngine SPI and reference implementation.
- ExecutorAdapter three forms: native, third-party bridge, remote.
- InjectionMode declaration and startup validation.
- AgentEvent stream from executor output.
- Structured ErrorClass emission.
- Capability/version governance for adapters.

Does not own:

- Canonical EngineRegistry implementation in `agent-execution-engine`.
- Runtime control decisions after adapter output.
- Translation/intercept resource calls, which flow through Translation Intercept.
- Product-level Agent definition ownership unless promoted later by ADR.

Existing code facts:

- `code-symbol/com-huawei-ascend-service-engine-spi-statelessengine`
- `code-symbol/com-huawei-ascend-service-engine-adapter-inmemorystatelessengine`
- `code-symbol/com-huawei-ascend-service-engine-spi-agentinvokerequest`
- `code-symbol/com-huawei-ascend-service-engine-spi-statedelta`
- `code-symbol/com-huawei-ascend-service-runtime-orchestration-inmemory-sequentialgraphexecutor`
- `code-symbol/com-huawei-ascend-service-runtime-orchestration-inmemory-iterativeagentloopexecutor`

Existing and near-term contracts:

- `contract-yaml/engine-envelope`
- `contract-yaml/agent-invoke-request`
- `docs/contracts/execution-request.v1.yaml` (design_only, ADR-0155)
- `docs/contracts/agent-event.v1.yaml` (design_only, ADR-0155)
- `docs/contracts/error-class.v1.yaml` (design_only, ADR-0155)

Existing tests:

- `test/com-huawei-ascend-engine-runtime-engineenvelopevalidationtest`
- `test/com-huawei-ascend-engine-runtime-engineregistryresolvetest`
- `test/com-huawei-ascend-service-platform-engine-engineregistrybootvalidationit`
- `test/com-huawei-ascend-service-engine-adapter-inmemorystatelessenginetest`
- `test/com-huawei-ascend-service-engine-spi-agentinvokerequesttest`
- `test/com-huawei-ascend-service-engine-spi-statedeltatest`

Candidate FunctionPoints to add:

- `FP-EXECUTOR-ADAPTER-INVOKE`
- `FP-EXECUTION-REQUEST`
- `FP-AGENT-EVENT-STREAM`
- `FP-ERROR-CLASS-EMISSION`
- `FP-ADAPTER-CAPABILITY-GOVERNANCE`

Track B rule:

- Demand may add a new adapter only if it registers into the existing engine/adaptor model and resource calls route through Translation Intercept.
- Demand must escalate to ADR if it requires bypassing EngineRegistry strict matching, adding a new adapter form beyond the approved InjectionMode set, or moving canonical engine ownership out of `agent-execution-engine`.

OSS comparison:

- LangGraph separates core graph runtime from SDK/prebuilt layers.
- Spring AI Alibaba separates graph-core, agent-framework, studio, sandbox, and starters.
- AgentScope/OpenAI-style runner abstractions point to adapter lifecycle as a stable frame, not a feature.

### 4.6 EF-TRANSLATION-INTERCEPT

Definition: The boundary-treatment frame for model, tool, memory, retrieval, prompt, structured output, and Spring AI adapter integration. It governs resource calls without becoming the orchestrator or the engine registry.

Owns:

- Context projection and prompt/material construction where applicable.
- ADR-0155 governed messages boundary treatment.
- Context compaction and projection versioning.
- Model profile normalization.
- Structured output and result interpretation.
- ChatAdvisor and tool shaping, composed with RuntimeMiddleware rather than replacing it.
- Client-hosted skill payload shaping.
- Remote Agent payload normalization.
- Tool/memory/retrieval invocation profile.
- Platform intercept SPI surface: `PlatformChatClient`, `PlatformToolCallback`, `PlatformMemoryProvider`, `PlatformRetriever`.

Does not own:

- RuntimeMiddleware HookPoint dispatch itself.
- Orchestrator state transitions.
- Engine adapter selection.
- Session facts, which remain state-frame owned.

Existing code facts:

- `code-symbol/com-huawei-ascend-service-integration-springai-springaichatmodelgateway`
- `code-symbol/com-huawei-ascend-service-integration-springai-springaitoolcallbackskilladapter`
- `code-symbol/com-huawei-ascend-service-session-spi-contextprojector`
- `code-symbol/com-huawei-ascend-service-session-inmemorycontextprojector`
- `code-symbol/com-huawei-ascend-service-agent-spi-agentregistry`

Existing and near-term contracts:

- `docs/contracts/governed-messages.v1.yaml` (design_only, ADR-0155)
- `docs/contracts/intercept-request.v1.yaml` (design_only, ADR-0155)
- `docs/contracts/tool-result.v1.yaml` (design_only, ADR-0155)
- `docs/contracts/session-snapshot.v1.yaml` (design_only, ADR-0155)
- `contract-yaml/model-invocation` (design_only in generated facts)
- `contract-yaml/memory-store` (design_only in generated facts)

Existing tests:

- `test/com-huawei-ascend-service-session-inmemorycontextprojectortest`
- `test/com-huawei-ascend-service-agent-spi-agentspicarrierimmutabilitytest`
- Architecture tests related to LLM gateway hook discipline are in the generated test inventory under runtime architecture.

Candidate FunctionPoints to add:

- `FP-GOVERNED-MESSAGES`
- `FP-PLATFORM-CHAT-CLIENT`
- `FP-PLATFORM-TOOL-CALLBACK`
- `FP-PLATFORM-MEMORY-PROVIDER`
- `FP-PLATFORM-RETRIEVER`
- `FP-TOOL-RESULT-NORMALIZATION`
- `FP-REMOTE-AGENT-PAYLOAD-NORMALIZATION`

Track B rule:

- Demand may add provider adapters, tool translators, or message governance policies if they remain behind this frame and preserve contract-bound resource calls.
- Demand must escalate to ADR if it lets adapters instantiate provider SDKs directly, treats remote agent internal calls as locally interceptable, or moves policy enforcement into the engine execution frame.

OSS comparison:

- Semantic Kernel separates connectors, memory, functions, filters, prompt_template, and services.
- Spring AI uses PromptTemplate, ChatClient, advisors, and structured output primitives as composable boundaries.
- LangChain/LangChain4j tools, memory, retrievers, and structured output are resource-bound adapters, not orchestration state owners.

## 5. Demand Landing Checks

### Audit trail demand

Expected route:

Product Feature: financial-grade audit trail.

Frame traversal:

1. `EF-ACCESS-ADMISSION`: capture tenant, actor, trace, request, idempotency context.
2. `EF-SESSION-TASK-STATE`: persist state transition and tenant-bound audit source material.
3. `EF-INTERNAL-EVENT-QUEUE`: emit audit-worthy RunEvent or lifecycle projection.
4. `EF-TASK-CONTROL`: classify cancel/complete/suspend/resume decisions.
5. `EF-ENGINE-DISPATCH`: classify adapter error and execution event material.
6. `EF-TRANSLATION-INTERCEPT`: audit model/tool/resource-bound calls.

If the demand requires only new audit events and storage contracts, it lands on existing frames. If it requires a separate global audit plane that rewrites state ownership or bypasses frame-level event emission, it must stop and enter ADR.

### A2A ingress demand

Expected route:

1. `EF-ACCESS-ADMISSION`: normalize message/send, tasks/cancel, tasks/resubscribe into AccessIntent.
2. `EF-SESSION-TASK-STATE`: bind Run/Task/Session and correlation handle.
3. `EF-INTERNAL-EVENT-QUEUE`: route control/data/rhythm events.
4. `EF-TASK-CONTROL`: decide resume/cancel/child/remote flow.

If A2A remains protocol ingress plus normalized service intent, it lands on existing frames. If A2A demands direct peer access to engine adapters or agent-bus topics, it must stop and enter ADR.

### Cost governance demand

Expected route:

1. `EF-TRANSLATION-INTERCEPT`: normalize model profile and governed messages.
2. `EF-TASK-CONTROL`: enforce quota/rate decisions through RuntimeMiddleware and SuspendReason.
3. `EF-SESSION-TASK-STATE`: persist spend/cost decision material if required.
4. `EF-INTERNAL-EVENT-QUEUE`: emit spend/audit events.

If the demand is pre-call/post-call governance at the model boundary, it lands on existing frames. If it requires replacing RuntimeMiddleware or moving budget checks into executor implementations, it must stop and enter ADR.

## 6. Required DSL and Gate Work

Recommended sequence:

1. Add the six Agent Service `SAA EngineeringFrame` elements to `architecture/features/engineering-frames.dsl`.
2. Add `genModule_agent_service -> ef*` `contains` relationships.
3. Add `ef* -> fp*` `anchors` relationships for all existing Agent Service FunctionPoints.
4. Reinterpret value Features so their `contains FunctionPoint` edges migrate to `requires FunctionPoint`.
5. Add `Feature -> EngineeringFrame` `traverses` edges for each value feature.
6. Re-tag the six ADR-0138 layer Feature elements out of `SAA Feature`, or remove/replace them after catalogs can render frames.
7. Update feature catalog rendering so Agent Service docs show a frame section before feature sections.
8. Update baselines and gate expectations only after the workspace projection is clean.

Initial anchoring suggestion:

| Frame | Existing anchors |
|---|---|
| `EF-ACCESS-ADMISSION` | `FP-CREATE-RUN`, `FP-CANCEL-RUN`, `FP-GET-RUN-STATUS`, `FP-IDEMPOTENCY-CLAIM`, `FP-TENANT-CROSS-CHECK`, `FP-A2A-MESSAGE-SEND`, `FP-A2A-TASKS-CANCEL`, `FP-A2A-TASKS-RESUBSCRIBE`, `FP-MQ-INBOUND` |
| `EF-SESSION-TASK-STATE` | `FP-RUN-STATE-TRANSITION` |
| `EF-INTERNAL-EVENT-QUEUE` | none yet; design-only frame may have zero shipped anchors |
| `EF-TASK-CONTROL` | `FP-SUSPEND-RESUME`, `FP-CHILD-RUN-SPAWN` |
| `EF-ENGINE-DISPATCH` | no direct Agent Service FP yet; add host-side executor FPs or traverse external `EF-ENGINE-REGISTRY` with care |
| `EF-TRANSLATION-INTERCEPT` | no direct FP yet; add governed-message and platform-intercept FPs |

## 7. Open Issues

1. `FP-ENGINE-DISPATCH` is currently owned and implemented by `agent-execution-engine`, while Agent Service has host-side engine adapter responsibilities. Do not double-anchor the same FunctionPoint to both modules unless the relationship vocabulary explicitly supports cross-module frame traversal.
2. `FP-S2C-CALLBACK` is currently anchored in `agent-bus` through the S2C transport frame. Agent Service still needs a separate control-side callback handling FunctionPoint if we want to model validation, resume, failure, and timeout behavior inside Agent Service.
3. The generated fact extractor does not yet appear to emit EngineeringFrame facts. ADR-0157 mentions this as forward-looking. Until then, frame claims are authored DSL plus generated code/contract/test facts, not generated frame facts.
4. Several ADR-0155 contracts are listed in `contract-catalog.md` but were not present in the generated `contract-surfaces.json` snapshot used in this review. Before blocking gates rely on them, rerun fact extraction or inspect extractor coverage.
5. The six layer Features should not simply disappear until generated catalogs and gates can render EngineeringFrames. A staged migration is safer than a hard swap.

## 8. Recommendation

Adopt the six-frame Agent Service map from ADR-0157 as the canonical structural axis, but treat it as an implementation dossier rather than a naming exercise.

The immediate engineering goal should be:

`agent-service -> EngineeringFrame -> FunctionPoint -> Contract -> CodeFact/TestFact -> Verification`

Once this path is visible in the workspace, AI agents can first read structure, then understand how product features traverse it. Demand response can then be accepted into existing frames by default, with ADR escalation when demand requires a new frame or changes frame ownership.

---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-service
affects_level: [L1, L2]
affects_view: [scenarios, logical, process, development, physical]
status: proposed
language: en-US
relates_to:
  - docs/L1/agent-service/README.md
  - docs/L1/agent-service/scenarios.md
  - docs/L1/agent-service/logical.md
  - docs/L1/agent-service/process.md
  - docs/L1/agent-service/physical.md
  - docs/L1/agent-service/development.md
  - docs/L1/agent-service/spi-appendix.md
  - docs/adr/0136-vocabulary-reconciliation-pr71-task-vs-run.yaml
  - docs/adr/0137-suspendsignal-canonical-interruptsignal-glossary.yaml
  - docs/adr/0138-agent-service-five-layer-l1-ratification.yaml
  - docs/adr/0140-agent-service-layer5-split.yaml
  - docs/adr/0141-internal-event-queue-design-only.yaml
  - docs/adr/0142-run-aggregate-single-owner.yaml
  - docs/adr/0143-review-log-demotion-l1-canonical-move.yaml
  - docs/adr/0144-agent-service-layer-package-matrix.yaml
  - docs/adr/0145-run-event-sealed-hierarchy.yaml
---

# Agent Service L1 — Open-Source Agent / Workflow Architecture Comparison Review

> Date: 2026-05-26  
> Scope: `agent-service` canonical L1 4+1 views and follow-up L2 backlog.  
> Source: the comparison analysis in `docs/L1/agent-service/oss-agent-service-comparison.md`.  
> Positioning: this file is an additional architecture review record under `docs/logs/reviews/`. It does not replace the canonical L1 views under `docs/L1/agent-service/`. If this review conflicts with the canonical L1 views, `docs/L1/agent-service/` remains authoritative.

## 1. Executive conclusion

No single Java open-source project was found to be fully equivalent to the current Agent Service L1 five-layer responsibility model. The more accurate reading is that Agent Service is a composite **Agent-native control service**: it brings protocol ingress, Run/Task/Session state, internal events, task-centric runtime governance, engine adapters, and Spring AI / LangGraph / AutoGen / CrewAI / OpenAI Agents / Semantic Kernel style tool/runtime translation into one ADR- and Rule-governed service boundary.

The current L1 five-layer split is sound. Three rc55 corrections are especially important and should remain stable:

1. **Layer 2 owns the Run aggregate exclusively**: this matches the fact-source pattern in A2A TaskStore, Temporal workflow history, and Conductor task persistence. State persistence needs a single owner; Layer 4 may only express transition intent through `RunRepository.updateIfNotTerminal(...)`.
2. **Layer 3 is explicitly marked `design_only`**: no `service.queue/` code home exists yet, but declaring the three-track bus binding prevents queue responsibilities from leaking into the Orchestrator or Runtime Adapter.
3. **Layer 5 is split into 5a / 5b**: execution dispatch and prompt / tool / model translation evolve at different cadences. ADR-0140 prevents ChatAdvisor, RuntimeMiddleware, and ExecutorAdapter from being conflated.

This review does not recommend reducing Agent Service to a wrapper around any one OSS framework. The next architectural priority should be turning Layer 2 / Layer 3 / Layer 4 state transition, event queue, and Hook / Suspend semantics into minimal verifiable code and contract tests before adding more engine adapters.

## 2. Comparison set and evaluation frame

This review places OSS projects into the Agent Service 4+1 coordinate system instead of deriving architecture conclusions from project names alone.

| Type | Representative projects | Relationship to Agent Service | Judgment |
|---|---|---|---|
| Protocol-first service | A2A Java SDK, Spring AI A2A | A2A Task / AgentCard / executor ingress | Useful for Layer 1 and Task protocol reference, but not a full control plane |
| Runtime-wrapper service | AgentScope Runtime Java | Wraps one agent framework as a runtime service | Closest service shape, but lacks full Run/Task/Queue/governance |
| Workflow-orchestration service | Temporal Java SDK, Conductor | Durable task, worker, history, retry, signal | Useful for Layer 3 / Layer 4 long-running control, but not Agent semantics core |
| Java agent platform / library | Spring AI Alibaba, LangGraph4j, LangChain4j, Spring AI, Solon AI | Graph, tool, MCP, sandbox, ChatClient, session facets | Useful as local references for Layer 4 / 5a / 5b |
| Non-Java agent runtime | LangGraph, AutoGen, CrewAI, OpenAI Agents Python, Semantic Kernel | Graph state, actor queue, crew orchestration, runner, plugin kernel | Extract patterns, but do not let them redefine the Java L1 boundary |

Conclusion: most OSS projects cover only one Agent Service layer or one 4+1 view. The closest match to our needs is the composite pattern, not a single repository.

### 2.1 Project-by-project evidence expansion

| Project | Closest Agent Service layer | Why it cannot be copied directly | Architecture opinion to retain |
|---|---|---|---|
| A2A Java SDK | Layer 1 + Layer 2 protocol Task boundary | It defines protocol-visible Task / Message / TaskState / AgentExecutor only; it does not own internal Run execution state, Session projection, or the three-track queue | Reuse TaskState final / interrupted predicates and AgentCard / push notification ideas; do not replace RunRepository with A2A TaskStore |
| Spring AI A2A | Layer 1 thin adapter + Layer 5b ChatClient bridge | It quickly bridges requests into ChatClient and does not govern tenantId-first persistence, cancel race, RuntimeMiddleware, or S2C callback | Keep A2A starter auto-configuration / controller / executor handler separated; controllers must not own state |
| Spring AI Alibaba | Layer 4 / 5a / 5b Java platform capability set | Graph, agent, admin, MCP, sandbox, and A2A capabilities are spread across modules rather than one Agent Service control plane | Use it as a Java/Spring platform reference for graph, MCP, sandbox, and admin, while preserving this project's five-layer boundary |
| AgentScope Runtime Java | Layer 1 + 5a runtime-wrapper service | It is a single-agent runtime service where Runner wraps AgentHandler; it lacks full Run/Task/Queue/task-centric governance | Reuse protocol handler → unified runner thin handoff and externalized session/state/memory/sandbox services |
| Temporal Java SDK | Layer 3 + Layer 4 durable execution substrate | Temporal does not model Agent protocol, tool governance, A2A/S2C, or Session context; workflow history cannot replace Run/Task/Session aggregates | Reuse history/replay, signal, timer, and worker task queue constraints for long-running recoverability |
| Conductor | Layer 1 / 2 / 3 / 4 workflow/task orchestration server | It centers on workflow task / worker semantics; Agent/LLM is a worker extension, not SuspendSignal / HookPoint / EngineRegistry governance | Reuse worker poll/update, retry/timeout, human task, dead-letter, and task query API patterns |
| LangGraph4j / LangGraph | Layer 4 + 5a graph runtime | They are graph execution kernels / hosted graph APIs, not enterprise ingress, tenant/RLS, or Run aggregate ownership models | Reuse interrupt/resume/checkpoint/state history; keep Checkpointer SPI small and separate from Session/Memory sovereignty |
| LangChain4j / Spring AI / Semantic Kernel | Layer 5b model/tool/function invocation kernel | They govern model-call boundaries and do not know Run CAS, tenant, cancel, audit, or Layer 4 middleware | Reuse Advisor / ToolExecutor / plugin filter / structured output patterns, while keeping RuntimeMiddleware in Layer 4 |
| AutoGen | Layer 3 actor/message runtime + Layer 4 intervention | Its asyncio queue and subscription model are strong, but it is not a durable service queue and lacks tenant-first aggregates | Reuse message envelope, topic/subscription, cancellation token, and intervention/drop fields for RunEvent envelope design |
| CrewAI / OpenAI Agents Python | Layer 4 / 5a multi-agent orchestration and runner | They are application-level Crew/Flow or SDK runner models, not service-level Run/Task/Queue authorities | Reuse handoff, guardrail, human feedback, and next-step loop patterns without letting SDK runners own service state |

### 2.2 Important details missing from the first summary

The first generated review compressed too much of the source analysis. Four detail categories needed restoration:

1. **Project-specific non-equivalence was underspecified**: saying a project is a reference can be misread as saying it is a substitute. This revision states each OSS project's missing boundary: protocol, runtime, workflow, graph, tool, or plugin kernel.
2. **Layer 3 worker-contract detail was too thin**: the source analysis stressed that a queue is not only a channel; it also needs Producer / Consumer / lease / ack / retry / dead-letter / heartbeat semantics. This revision promotes that to P0 backlog and binds it to Rule R-E.
3. **Layer 5 capability-discovery rationale was incomplete**: the source analysis cited LangChain4j capabilities, A2A AgentCard, and AgentScope metadata. This revision states that capability discovery may enhance `EngineRegistry.resolve(envelope)` but must not weaken strict matching.
4. **Session / Memory concurrency risk was not expanded**: the source used LangChain4j `@MemoryId` as evidence that concurrency discipline cannot be left to callers. This revision adds it as a P1 L2 backlog item aligned with tenantId-first and RLS.

## 3. Strict accept / modify / reject classification

### 3.1 Accept

| Review finding | Basis | Accepted form |
|---|---|---|
| Access Layer must stay a thin protocol adapter | AgentScope Runtime, Spring AI A2A, A2A Java SDK | Layer 1 only translates protocols and binds tenant / idempotency / trace; it does not own the Run state machine or drive Runtime directly |
| The Run / Task / Session aggregate split is correct | A2A TaskState, Temporal execution, AgentScope session/state, LangChain4j memoryId risk | Keep Task as control state, Run as execution state, and Session as context state |
| Layer 3 needs to remain an independent layer | Temporal worker/history, Conductor queue/worker, AutoGen envelope/queue | Preserve the L1 layer and three-track channel binding even while code home remains design_only |
| Layer 4 is the platform differentiator | LangGraph4j interrupt/resume, Temporal signal, Conductor human task, OpenAI Agents interruption | Keep SuspendSignal, RuntimeMiddleware, Fast/Slow, S2C callback, and cancel race governance in Layer 4 |
| Layer 5a / 5b split is sound | Spring AI ChatClient/Advisor, LangChain4j ToolExecutor, AgentScope Runner, LangGraph4j CompiledGraph | 5a handles execution dispatch; 5b handles prompt/tool/model translation; RuntimeMiddleware does not move into 5b |
| Moving canonical 4+1 views to `docs/L1/agent-service/` was the correct governance action | ADR-0143 + Rule G-1 | `docs/logs/reviews/` remains an interaction/review record area; canonical L1 authority stays under `docs/L1/agent-service/` |

### 3.2 Modify before acceptance

| Review finding | Risk | Modified landing point |
|---|---|---|
| Add `isFinal()` / `isInterrupted()` / `isActive()` to `Task.A2aState` | Direct enum changes touch implementation and tests; a review log must not claim this is implemented | Add to Layer 2 Task lifecycle L2 backlog, then implement in an impl-mode wave |
| Add a CAS-style transition primitive to `TaskStateStore` | A direct RunRepository clone may ignore Task-vs-Run semantics | Define `transitionIf(...)` or an equivalent primitive in an L2 contract first |
| Add Layer 3 Publisher / Consumer / Lease / DeadLetter skeletons | Layer 3 is design_only; the review must not imply code exists | Add to Layer 3 L2 backlog; any implementation must preserve control/data/rhythm vs durability-tier orthogonality |
| Add tests for RunEvent-to-channel routing | `run-event.v1.yaml` is design_only and the Java sealed type has not landed yet | Add contract/gate tests in the ADR-0145 implementation wave; do not claim runtime enforcement here |
| Add engine capability discovery | Must not weaken Rule R-M.a/.b strict matching | Treat it as an enhancement to `EngineRegistry.resolve(envelope)`: strict type matching first, capability-aware matching second |
| Define RuntimeMiddleware and ChatAdvisor ordering | They live in different layers and must not be merged into one interceptor | Add an L2 contract for HookPoint, AdvisorChain, short-circuit, and exception propagation ordering |
| Keep Checkpointer SPI minimal | A large SPI could absorb session / memory / workflow history ownership | State in L2 that Checkpointer carries compute snapshots only, not Session or Memory sovereignty |
| Add a protocol-adapter thinness rule | A2A / MQ / HTTP starters may be tempted to own state in controllers | Add Access Layer L2 contract language: controllers translate protocols and call Layer 2/4 services only |

### 3.3 Reject

| Candidate direction | Rejection reason |
|---|---|
| Rewrite Agent Service as a direct wrapper around one OSS framework | Violates the current L1 service-control-plane positioning; most OSS projects cover only one layer or view |
| Replace Run / Task / Session with A2A TaskStore | A2A Task is protocol control state; it cannot carry internal execution state, tenantId-first persistence, cancel race, or Session projection |
| Replace Agent Service Layer 2/3/4 with Temporal / Conductor workflow history | Durable workflow is a useful long-running substrate, not the Agent-native Run/Task/SuspendSignal/HookPoint model |
| Let Layer 5 engine adapters redefine the Layer 2/4 state model | Violates Rule R-M.a/.b and ADR-0142; engines are adapted by Agent Service, not owners of the Run aggregate |
| Treat ChatAdvisor as an alias for RuntimeMiddleware | ADR-0140 assigns RuntimeMiddleware to Layer 4 and ChatAdvisor to Layer 5b; they compose but are not equivalent |
| Model Layer 3 as one generic MQ or one persistence mode | Violates Rule R-E; channel intent and durability tier are orthogonal dimensions |

## 4. Layer-by-layer review

### 4.1 Layer 1 — Access Layer

The OSS comparison shows that thinner ingress layers keep the core state machine cleaner. A2A Java SDK and Spring AI A2A are good references for A2A endpoints, AgentCard, and executor bridging. AgentScope Runtime's protocol handler to Runner pattern is also a useful thin-adapter precedent.

**Review finding**: the current Layer 1 design is sound. When A2A / MQ / HTTP extensions land, controllers should not own TaskStore, RunRepository, or executor lifecycle. They should translate protocols, bind tenant / idempotency / trace, and call Layer 2/4 services.

### 4.2 Layer 2 — Session & Task Manager

A2A Java TaskState predicates, AgentScope externalized session/state, Conductor task persistence, and Temporal durable identity all support the current Run / Task / Session split. LangChain4j's warning around concurrent calls for the same `@MemoryId` further shows that Session / Memory concurrency must be governed by the platform.

**Review finding**: Layer 2 is the part of the current design that should remain most stable. The next improvements should strengthen Task lifecycle transition primitives and define explicit concurrency rules for Session projection / Memory mutation.

### 4.3 Layer 3 — Internal Event Queue

Temporal, Conductor, and AutoGen all show that an internal queue is not merely “use an MQ later.” Layer 3 needs event envelopes, channel routing, lease, ack, retry, dead-letter, idempotency key, and durability-tier semantics.

**Review finding**: keeping Layer 3 independent is correct, but it must continue to be marked `design_only` until `service.queue/` or an equivalent code home lands. A minimal future skeleton may include `RunEventPublisher`, `RunEventConsumer`, `QueueLease`, and dead-letter contracts, but it must not break Rule R-E physical isolation for control/data/rhythm.

### 4.4 Layer 4 — Task-Centric Control Layer

LangGraph4j / LangGraph interrupt-resume, Temporal signal, Conductor human task, and OpenAI Agents interruption / next-step loop all support making SuspendSignal and runtime governance first-class. Layer 4 is not an implementation of one agent loop; it decides when to start, suspend, resume, cancel, short-circuit, upgrade to Slow-Path, and trigger middleware.

**Review finding**: Layer 4 is the core differentiator between Agent Service and ordinary Agent SDKs. The next L2 work should define RuntimeMiddleware ordering / short-circuit / exception propagation and map S2C callback, child-run, and tool-await suspensions consistently across SuspendReason / RunEvent / channel routing.

### 4.5 Layer 5a / 5b — Engine Adapter Layer

Spring AI, LangChain4j, LangGraph4j, AgentScope Runtime, OpenAI Agents, and Semantic Kernel show that engine types will vary: graph engines, actor runtimes, crew orchestration, turn-based runners, and kernel function pipelines may all become adapted targets.

**Review finding**: ADR-0140's 5a / 5b split is correct and necessary. `EngineRegistry.resolve(envelope)` may evolve from pure `engine_type` strict matching toward capability-aware matching, but strict matching remains the Rule R-M.a/.b floor.

## 5. 4+1 view evaluation

The current 4+1 design is sound:

| View | Current value | OSS comparison |
|---|---|---|
| Scenarios | S1-S5 cover Fast/Slow, A2A, S2C, and cancel race | Similar to Temporal / Conductor using scenarios to drive failure paths |
| Logical | Five layers + Run/Task/Session ER + state machine + RunEvent | Maps to A2A TaskState, Temporal state machine, Conductor task model |
| Process | P1-P6 sequence diagrams, especially cancel winner/loser | Maps to workflow-engine modeling of race / retry / signal |
| Physical | Five planes, RLS, three-track bus, sandbox boundary | Similar to Conductor / Temporal deployment and persistence separation, with tenant/RLS added |
| Development | Package tree, Layer↔Package matrix, L2 boundary contracts | Stronger governance view than most OSS projects and appropriate for long-term multi-author development |

A future OSS pattern appendix or README link to this review would be useful so later L2 designs keep the OSS reference frame. That appendix must remain reference material only; it must not replace ADRs, Rules, contract catalog entries, or canonical L1 views.

## 6. Follow-up improvement backlog

The table below is not an immediate implementation list. It converts OSS evidence into candidate L2 / impl-mode entry points. Each item still needs a contract, tests, and gate coverage before it can change the current canonical L1 behavior. “An OSS project does this” is evidence, not authority.

| ID | Improvement | Source practice | Suggested landing point | Priority | Constraint |
|---|---|---|---|---|---|
| I-01 | Add state predicates to `Task.A2aState` | A2A Java `TaskState` | `service.task` | P0 | Do not change RunStatus DFA |
| I-02 | Add CAS-style transition primitive to `TaskStateStore` | A2A TaskStore + RunRepository | `service.task.spi` | P0 | L2 contract first, implementation later |
| I-03 | Add Layer 3 Publisher / Consumer / Lease / DeadLetter skeleton | Conductor queue, Temporal task queue | future `service.queue/` | P0 | Keep `design_only` until code lands |
| I-04 | Test RunEvent channel routing | Temporal event history, Conductor task events | `run-event.v1.yaml` + tests | P0 | Must map to control/data/rhythm |
| I-05 | Add engine capability discovery | LangChain4j capabilities, A2A AgentCard, AgentScope metadata | `service.engine.spi` | P1 | Do not weaken strict matching |
| I-06 | Define RuntimeMiddleware ordering / short-circuit / exception contract | Spring AI Advisor, LangChain4j ToolExecutor | Layer 4 L2 design | P1 | RuntimeMiddleware remains Layer 4 only |
| I-07 | Keep Checkpointer SPI minimal | LangGraph4j checkpoint saver | orchestration SPI | P1 | Do not own Session / Memory sovereignty |
| I-08 | Define protocol-adapter thinness rule | AgentScope Runtime Runner | Access Layer L2 | P1 | Controllers must not own Run state |
| I-09 | Define Session / memory mutation concurrency discipline | LangChain4j `@MemoryId` risk | Session / Memory L2 | P1 | Must align with tenantId-first and RLS |
| I-10 | Align human/S2C callback with A2A input_required | A2A Java, Conductor human task, Temporal signal | S2C / Task state | P2 | Express through canonical SuspendSignal path |
| I-11 | Add admin/debug process query API | Spring AI Alibaba workflow debug, Conductor WorkflowResource | future observability/admin | P2 | Must not bypass Layer 2/4 |
| I-12 | Align sandbox/tool governance schema with physical enforceability | Spring AI Alibaba sandbox, AgentScope SandboxService | Middleware / Sandbox | P2 | Must satisfy Rule R-L |

## 7. Recommended L2 deep-dive order

1. **Layer 2 Task lifecycle L2**: add `Task.A2aState` predicates and `TaskStateStore` transition contract.
2. **Layer 3 Internal Event Queue L2**: add Producer / Consumer / Lease / retry / dead-letter / channel routing.
3. **Layer 4 RuntimeMiddleware + SuspendSignal L2**: define HookPoint ordering, short-circuit, failure propagation, and resume semantics.
4. **Layer 5 Engine Adapter L2**: define EngineCapability, adapter lifecycle, streaming, tool-call, checkpoint, and structured-output capabilities.
5. **Layer 1 A2A Access L2**: refine Thin controller, AgentCard, Task Cursor, cancel/query/streaming endpoints.

## 8. Final judgment

Agent Service's module split and 4+1 design are sound and more complete than any single OSS project in the comparison set. The key OSS lesson is not to replace the current design, but to provide validation coordinates for each layer:

- A2A Java validates Task protocol state and final/interrupted predicates;
- Spring AI A2A / AgentScope Runtime validate thin protocol adapters;
- Temporal / Conductor validate events, queues, workers, history, retry, and resume for long-running work;
- LangGraph4j / LangGraph validate interrupt/resume/checkpoint as first-class runtime capabilities;
- Spring AI / LangChain4j validate separating prompt/tool/model translation from runtime control;
- Spring AI Alibaba / Solon AI show Java agent, MCP, A2A, sandbox, and admin capabilities converging toward platform modules;
- AutoGen / CrewAI / OpenAI Agents / Semantic Kernel show actor runtimes, multi-agent orchestration, agent runners, and plugin kernels as adapted targets rather than replacement boundaries.

Future work should keep the canonical L1 views as the spine and use OSS patterns to strengthen L2 contracts and enforcement gates. The priority is making state transition, event queue, and Hook / Suspend semantics verifiable, not expanding the number of engine adapters.

---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-service
affects_level: L1
affects_view: [scenarios, logical, process, development, physical]
status: proposed
language: en-US
relates_to:
  - docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md
  - docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.en.md
  - docs/logs/reviews/2026-05-26-agent-service-l1-oss-comparison-review.cn.md
  - docs/logs/reviews/2026-05-26-agent-service-l1-oss-comparison-source.cn.md
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
  - docs/adr/0139-fast-slow-path-narrowed-semantics.yaml
  - docs/adr/0140-agent-service-layer5-split.yaml
  - docs/adr/0141-internal-event-queue-design-only.yaml
  - docs/adr/0142-run-aggregate-single-owner.yaml
  - docs/adr/0143-review-log-demotion-l1-canonical-move.yaml
  - docs/adr/0144-agent-service-layer-package-matrix.yaml
  - docs/adr/0145-run-event-sealed-hierarchy.yaml
---

# Agent Service L1 — Module Capability Description and Feature List

> Date: 2026-05-26  
> Scope: `agent-service` service responsibilities, module capabilities, business-scenario coverage, and feature list.  
> Positioning: This is an architecture review opinion under `docs/logs/reviews/`. It consolidates the historical feature inventory, canonical 4+1 views, and OSS comparison conclusions into an L1 module capability list that is readable by both humans and AI agents. It does not replace the canonical L1 views under `docs/L1/agent-service/`; if they disagree, the canonical L1 views win.

## 1. Executive conclusion

Agent Service is an **Agent-native control service**, not a thin wrapper around an Agent SDK, workflow engine, ChatClient, A2A starter, or runtime wrapper. Its core responsibility is to safely translate external requests into tenant-bound Run / Task / Session state changes while preserving server sovereignty over heterogeneous engine dispatch, tool invocation, model invocation, suspension / resume / cancel, S2C callbacks, A2A peer collaboration, and long-running task governance.

Under the latest L1 architecture, the feature list must answer three questions:

1. **Can the system execute the business scenarios end-to-end?** S1 standard synchronous intake, S2 long-horizon ReAct/tool loop, S3 A2A peer collaboration, S4 S2C callback, and S5 cancel during execution must all close across access, state, events, control, execution, and translation.
2. **Which full module owns each capability?** The list is grouped by module full name, not by layer number or implementation priority. Cooperating capabilities must remain orthogonal; two modules must not own the same source of truth.
3. **Does the capability set meet the industry baseline?** A2A Java, Spring AI A2A, AgentScope Runtime, Temporal, Conductor, LangGraph, LangChain4j, Spring AI, AutoGen, CrewAI, OpenAI Agents, and Semantic Kernel are pattern references only; this project's authority still comes from ADRs, Rules, contracts, and the canonical L1 views.

This document therefore avoids extra status labels or priority tiers. A good L1 feature list should reduce design entropy: it states what each module must do, which business paths each capability covers, what the inputs and outputs are, which modules collaborate, how exceptions close, and which mature OSS patterns justify the capability.

## 2. Decomposition principles

| Principle | Meaning | Prevents |
|---|---|---|
| Scenario anchored | Every feature traces to S1-S5 or a cross-scenario invariant. | Component-only lists that cannot prove the system works. |
| Full module ownership | Use full names such as Access Layer and Session & Task Manager. | Replacing real ownership with abstract layer labels. |
| Single source of truth | Run execution state, Task control state, Session context state, and RunEvent audit/event streams have distinct owners. | Generic StateStore designs, Run/Task confusion, or Runtime-owned state. |
| Data flow plus control flow | Cover both request-to-execution flow and state/event/payload/callback/audit flow. | Happy-path-only designs without races, resume, retry, or dead-letter paths. |
| Orthogonal collaboration | Modules cooperate through explicit inputs, outputs, and contracts. | Duplicating a policy across multiple modules or merging unrelated concerns. |
| Industry baseline calibration | Use OSS systems to identify mature agent/workflow service expectations. | Shipping only an SDK runner without long-horizon, queue, interrupt, worker, or human-callback capabilities. |

## 3. Business scenarios to module collaboration matrix

| Scenario | Normal business closure | Exception closure | Primary collaborating modules |
|---|---|---|---|
| S1 Standard synchronous intake | Access Layer receives `POST /v1/runs`; Session & Task Manager creates Run / Task; Task-Centric Control Layer dispatches; Engine Dispatch & Execution runs the workload; Translation & Tool-Intercept shapes context, prompt, and model invocation. | Cross-tenant requests collapse to boundary errors; idempotency hits return cached responses or conflicts; invalid engine envelopes become `engine_mismatch` failures. | Access Layer, Session & Task Manager, Task-Centric Control Layer, Engine Dispatch & Execution, Translation & Tool-Intercept. |
| S2 Long-horizon ReAct / tool loop | Access Layer returns a Task Cursor; Session & Task Manager maintains Run / Task / Session; Internal Event Queue binds control / data / rhythm; Task-Centric Control Layer drives middleware around HookPoints; Engine Dispatch & Execution runs the agent loop; Translation & Tool-Intercept interprets tool/model calls. | Tool timeout, middleware short-circuit / fail, resume locus changes, and checkpoint recovery failures must all be expressed through SuspendSignal, RunRepository CAS, RunEvent, and channel routing. | All six modules participate; Internal Event Queue provides the event and rhythm boundary for long-running work. |
| S3 A2A peer collaboration | Parent Run suspends on this instance; Access Layer / IngressGateway handles peer ingress / egress; Task-Centric Control Layer spawns and waits for child Runs; peer terminal status returns and resumes the parent Run. | Peer unreachable, peer error envelope, cross-tenant peer call, and child terminal failure must preserve parentRunId / traceId / tenantId correlation and let the parent Run decide resume, failure, or retry. | Access Layer, Session & Task Manager, Internal Event Queue, Task-Centric Control Layer, Engine Dispatch & Execution. |
| S4 S2C client callback | Engine Dispatch & Execution throws `SuspendSignal.forClientCallback(...)`; Task-Centric Control Layer publishes S2cCallbackEnvelope; Internal Event Queue sends the request on control and receives the response on data; client resume lets the executor continue. | Client timeout, schema-invalid response, capacity exhaustion, and resume re-auth failure must produce explicit events, state transitions, and error envelopes. | Session & Task Manager, Internal Event Queue, Task-Centric Control Layer, Engine Dispatch & Execution, Access Layer. |
| S5 Cancel during execution | Access Layer handles cancel and re-auth; Session & Task Manager performs atomic CAS through `RunRepository.updateIfNotTerminal(...)`; Task-Centric Control Layer classifies winner / loser; Internal Event Queue emits audit events. | Same-terminal cancel returns idempotent success; different-terminal cancel returns illegal transition; cancel-vs-complete race loser re-reads post-CAS state and returns a deterministic response. | Access Layer, Session & Task Manager, Task-Centric Control Layer, Internal Event Queue. |

## 4. Data-flow and control-flow closure

### 4.1 Data flow

1. **Ingress data**: external requests carry tenant, identity, idempotency key, trace, protocol payload, and engine envelope; Access Layer normalizes them.
2. **State data**: Session & Task Manager splits ingress data into Run execution state, Task control state, Session context state, and IdempotencyRecord; all tenant-bound persistence obeys RLS.
3. **Event data**: RunCreated, RunStateTransition, SuspendRequested, ResumeRequested, S2C, ChildRun, CancelRequested, and TerminalTransition events arise at state and control boundaries and route by intent to control / data / rhythm.
4. **Execution data**: Task-Centric Control Layer passes RunContext and EngineEnvelope to Engine Dispatch & Execution; the execution module returns only result, yield, SuspendSignal, or HookPoint event, never service-level state ownership.
5. **Model and tool data**: Translation & Tool-Intercept converts Session projection into InjectedContext, PromptTemplate, model invocation, tool request, and structured output.
6. **Return data**: tool result, S2C response, child-run completion, and terminal result return to Task-Centric Control Layer, then enter the source of truth through Session & Task Manager CAS / projection / audit paths.

### 4.2 Control flow

1. Access Layer only translates protocols, binds auth context, handles idempotency, and shapes error envelopes; it does not directly drive runtime or call middleware.
2. Session & Task Manager is the source of truth for Run / Task / Session; Run status writes only happen through `RunRepository.updateIfNotTerminal(...)` or the create-only save path.
3. Internal Event Queue is currently a design boundary; its control meaning is binding cancel, resume, suspend, S2C, payload, heartbeat, and other intents to the three physical channels rather than selecting one concrete MQ too early.
4. Task-Centric Control Layer decides start, suspend, resume, cancel, middleware short-circuit, Fast / Slow Path routing, S2C callback, and child-run join; persistence still returns to Session & Task Manager.
5. Engine Dispatch & Execution strictly matches engines through `EngineRegistry.resolve(envelope)` and does not let engine adapters determine the Run / Task / Session state model.
6. Translation & Tool-Intercept shapes model and tool invocation without treating ChatAdvisor as RuntimeMiddleware.

## 5. Feature list grouped by module

### 5.1 Access Layer

| Feature ID | Category | Scenarios | Capability | Inputs / Outputs | Collaborators | Exception coverage | OSS reference |
|---|---|---|---|---|---|---|---|
| AS-L1-F01 | Protocol ingress and cursor | S1, S2, S5 | Receive HTTP / future gRPC / future A2A / future MQ ingress and translate external requests into internal Run / Task create, query, cancel, or resume requests; return Task Cursor immediately for long-running work. | Inputs: request payload, tenant, identity, idempotency key, trace. Outputs: RunResponse, TaskCursor, error envelope. | Session & Task Manager, Task-Centric Control Layer. | Schema invalid, unsupported protocol, long-running connection misuse. | A2A Java Task API, Conductor task query / update, LangGraph hosted run/thread API. |
| AS-L1-F02 | Tenant / auth / trace / idempotency binding | S1-S5 | Bind JWT tenant claim cross-check, TenantContextFilter, IdempotencyHeaderFilter, TraceExtractFilter, and request body hash before any request reaches the state source of truth. | Inputs: headers, JWT, body hash. Outputs: tenant-bound request context, idempotency decision, trace id. | Session & Task Manager. | Cross-tenant, idempotency hit, idempotency conflict, idempotency body drift. | Spring Security filter chain, A2A request context, Conductor idempotent task update. |
| AS-L1-F03 | Agent / capability publication boundary | S3, S4 | Publish agent identity, protocol capabilities, streaming / callback / tool support, and peer collaboration capabilities without letting capability publication weaken internal strict engine matching. | Inputs: agent metadata, engine capability summary. Outputs: AgentCard / capability response. | Engine Dispatch & Execution, Translation & Tool-Intercept. | Capability mismatch, unsupported peer feature, stale capability advertisement. | A2A AgentCard, AgentScope metadata, OpenAI Agents handoff metadata. |
| AS-L1-F04 | Cancel / query / resume ingress | S2, S4, S5 | Provide protocol entry points for Run query, cancel, and resume, converting all control requests into tenant-bound service calls. | Inputs: runId, tenant, resume payload, cancel actor. Outputs: Run status, resume accepted / rejected, cancel result. | Session & Task Manager, Task-Centric Control Layer, Internal Event Queue. | Cross-tenant cancel, same-terminal idempotent cancel, different-terminal illegal transition, resume schema invalid. | Temporal signal/query, Conductor workflow/task resource, A2A input_required resume. |

### 5.2 Session & Task Manager

| Feature ID | Category | Scenarios | Capability | Inputs / Outputs | Collaborators | Exception coverage | OSS reference |
|---|---|---|---|---|---|---|---|
| AS-L1-F05 | Run execution-state source of truth | S1-S5 | Own Run aggregate, RunStatus DFA, RunStateMachine validation, and atomic CAS through `RunRepository.updateIfNotTerminal(...)`. | Inputs: create request, transition intent. Outputs: Run record, transition result, audit material. | Access Layer, Task-Centric Control Layer, Internal Event Queue. | Illegal transition, cancel-vs-complete race, terminal no-op, `engine_mismatch` terminal failure. | Temporal workflow history single-source principle, Conductor durable task state. |
| AS-L1-F06 | Task control-state source of truth | S1-S5 | Maintain protocol-visible Task control state expressing done-or-not, why-stopped, input_required, completed, and failed semantics. | Inputs: Run state projection, protocol state change. Outputs: Task state, whyStopped, cursor status. | Access Layer, Run / Session subdomains, Task-Centric Control Layer. | Task / Run state drift, input_required confused with RunStatus. | A2A TaskState, A2A TaskStore final / interrupted predicates. |
| AS-L1-F07 | Session context-state source of truth | S1-S4 | Maintain conversation messages, variables, Session projection, and ContextProjector input for context shared across Runs. | Inputs: conversation update, Run-to-Session projection, memory reference. Outputs: Session snapshot, InjectedContext source. | Translation & Tool-Intercept, Task-Centric Control Layer. | Concurrent memory mutation, stale context projection, cross-tenant session read. | LangChain4j `@MemoryId` risk, AgentScope externalized session/state. |
| AS-L1-F08 | Tenant-first persistence and lifecycle audit | S1-S5 | Ensure all Run / Task / Session / idempotency / lifecycle audit records carry tenantId and obey RLS in durable backends; project state changes into lifecycle audit and RunEvent material. | Inputs: tenant-bound aggregate changes. Outputs: RLS-bound records, audit rows, event source material. | Internal Event Queue, physical deployment plane, Access Layer. | RLS bypass, tenant inference, audit loss, event without tenant. | Multi-tenant workflow services, Temporal namespace isolation, Conductor task visibility. |

### 5.3 Internal Event Queue

> The canonical L1 views position Internal Event Queue as a design boundary whose code directory has not landed yet. This section describes the module's required capability boundary without implying existing runtime code.

| Feature ID | Category | Scenarios | Capability | Inputs / Outputs | Collaborators | Exception coverage | OSS reference |
|---|---|---|---|---|---|---|---|
| AS-L1-F09 | RunEvent envelope and channel routing | S1-S5 | Define envelope and routing for RunCreated, RunStateTransition, SuspendRequested, ResumeRequested, S2C, ChildRun, CancelRequested, and TerminalTransition events. | Inputs: state / control boundary events. Outputs: routeable RunEvent envelope. | Session & Task Manager, Task-Centric Control Layer, agent-bus. | Event missing tenant, wrong channel, payload over inline cap. | AutoGen message envelope, Temporal event history, Conductor task event. |
| AS-L1-F10 | Producer / Consumer / Lease / Ack / Retry / DeadLetter | S2-S5 | Split publication and consumption, defining lease, ack, retry, dead-letter, heartbeat, and dedup relationships. | Inputs: RunEvent, control signal, delivery receipt. Outputs: ack, retry decision, dead-letter record. | Task-Centric Control Layer, Session & Task Manager, agent-bus. | At-least-once duplicate, consumer crash, poison message, lease expiry. | Conductor worker poll/update/dead-letter, Temporal worker task queue. |
| AS-L1-F11 | Three-track physical channel binding | S2-S5 | Bind cancel / resume / suspend / S2C requests to control; payload / transition / S2C response / child completion to data; heartbeat / tick to rhythm. | Inputs: event intent, payload size, timer signal. Outputs: control / data / rhythm channel operation. | Task-Centric Control Layer, physical deployment plane, agent-bus. | Control starvation, data congestion, timer loss, durability tier confusion. | Temporal signal/timer separation, Conductor queue visibility, high-priority control channels. |
| AS-L1-F12 | Long-horizon rhythm and observability | S2, S3, S4 | Provide the boundary for timeout, deadline, resume sweep, heartbeat, queue lag, retry count, and dead-letter count. | Inputs: rhythm tick, lease state, queue metrics. Outputs: timeout signal, observability metric, retry/dead-letter signal. | Task-Centric Control Layer, Session & Task Manager, physical deployment plane. | Timeout not fired, missed resume sweep, queue lag invisible. | Temporal timers, Conductor task timeout / retry metrics. |

### 5.4 Task-Centric Control Layer

| Feature ID | Category | Scenarios | Capability | Inputs / Outputs | Collaborators | Exception coverage | OSS reference |
|---|---|---|---|---|---|---|---|
| AS-L1-F13 | Orchestrator control loop | S1-S4 | Decide start, continue, suspend, resume, fail, or terminate based on Run / Task / Session, EngineEnvelope, routing predicates, and middleware results. | Inputs: RunContext, EngineEnvelope, HookOutcome, SuspendSignal, executor result. Outputs: dispatch decision, transition intent, resume intent. | Session & Task Manager, Engine Dispatch & Execution, Internal Event Queue. | Engine mismatch, executor failure, unexpected SuspendSignal, missing resume context. | LangGraph runner loop, OpenAI Agents runner, Temporal workflow execution loop. |
| AS-L1-F14 | RuntimeMiddleware governance | S2, S4 | Execute policy, quota, memory governance, sandbox routing, observability, and failure handling at HookPoint.before_tool / after_tool / before_llm / before_resume boundaries. | Inputs: HookPoint event, RunContext, tool/model metadata. Outputs: Proceed / ShortCircuit / Fail, audit signal. | Engine Dispatch & Execution, Translation & Tool-Intercept, agent-middleware. | Middleware fail, short-circuit without audit, over-wide sandbox grant, quota exhausted. | Spring AI Advisor composability, LangChain4j ToolExecutor filter, Semantic Kernel plugin filter. |
| AS-L1-F15 | Suspend / resume semantics | S2, S3, S4 | Catch checked suspension for child-run, S2C callback, and tool-await patterns, transition Run to SUSPENDED, and resume when the condition is satisfied. | Inputs: SuspendSignal, resume payload, child terminal status, S2C response. Outputs: suspend event, resume event, transition intent. | Session & Task Manager, Internal Event Queue, Engine Dispatch & Execution, Access Layer. | Client timeout, schema invalid, peer failure, resume re-auth failure, checkpoint missing. | LangGraph interrupt/resume, Temporal signal, Conductor human task, OpenAI Agents interruption. |
| AS-L1-F16 | Cancel race classification | S5 | Classify cancel winner / loser, same-terminal, different-terminal, and active-to-cancelled paths deterministically while aligning response code and audit event. | Inputs: cancel actor, pre-CAS Run, post-CAS Run. Outputs: 200 / 409 / 404, CancelRequestedEvent, terminal transition if won. | Access Layer, Session & Task Manager, Internal Event Queue. | Cancel-vs-complete race, duplicate cancel, cross-tenant cancel, terminal conflict. | Workflow engine CAS / optimistic transition, Conductor task terminal update. |
| AS-L1-F17 | Fast / Slow Path and long-running governance | S1, S2, S4 | Select execution path using wall-clock, external input, S2C, A2A, and deployment locus; Fast-Path may omit intermediate checkpoints but not tenant, RLS, or metadata persistence. | Inputs: run metadata, routing policy, execution estimate. Outputs: FastPath / SlowPath decision, mid-execution upgrade. | Session & Task Manager, Engine Dispatch & Execution, Internal Event Queue. | Long-running misclassification, Fast-Path overrun, checkpoint requirement drift. | Temporal durable execution, LangGraph checkpoint, OpenAI Agents long-running run-step loop. |

### 5.5 Engine Dispatch & Execution

| Feature ID | Category | Scenarios | Capability | Inputs / Outputs | Collaborators | Exception coverage | OSS reference |
|---|---|---|---|---|---|---|---|
| AS-L1-F18 | EngineRegistry strict matching | S1-S4 | Every execution goes through `EngineRegistry.resolve(envelope)` and selects exactly one ExecutorAdapter by `engine_type`; no registry bypass or Java subtype dispatch is allowed. | Inputs: EngineEnvelope. Outputs: ExecutorAdapter or EngineMatchingException. | Task-Centric Control Layer. | engine_type mismatch, missing adapter, capability mismatch. | Spring AI model registry, LangGraph compiled graph registry, OpenAI Agents runner selection. |
| AS-L1-F19 | ExecutorAdapter lifecycle | S1-S4 | Normalize execute / resume / stream / suspend boundaries for graph executor, agent loop, future actor runtime, crew orchestration, and kernel process. | Inputs: RunContext, resume payload, engine-specific config. Outputs: result, stream chunk, SuspendSignal, failure. | Task-Centric Control Layer, Translation & Tool-Intercept, agent-execution-engine. | Executor crash, unsupported resume, stream interruption, external runtime unavailable. | LangGraph4j graph runtime, AgentScope Runtime Runner, CrewAI Flow, AutoGen runtime. |
| AS-L1-F20 | EngineHookSurface | S2, S4 | When executor reaches tool / model / resume / checkpoint boundaries, it emits HookPoint events into Task-Centric Control Layer instead of directly invoking RuntimeMiddleware. | Inputs: engine-internal hook boundary. Outputs: HookPoint event. | Task-Centric Control Layer, Translation & Tool-Intercept. | Direct middleware call, missing HookPoint, hook result ignored. | LangChain4j tool invocation callback, Semantic Kernel function filter. |
| AS-L1-F21 | Compute snapshot / checkpoint handoff | S2, S3, S4 | Hand compute snapshots to the control layer without swallowing Session, Memory, or workflow-history sovereignty. | Inputs: parentNodeKey, resumePayload, executor snapshot. Outputs: snapshot reference, resume-compatible payload. | Task-Centric Control Layer, Session & Task Manager. | Snapshot incompatible, memory/session ownership blur, resume payload loss. | LangGraph checkpoint saver, Temporal history as a boundary reference. |

### 5.6 Translation & Tool-Intercept

| Feature ID | Category | Scenarios | Capability | Inputs / Outputs | Collaborators | Exception coverage | OSS reference |
|---|---|---|---|---|---|---|---|
| AS-L1-F22 | Context projection and prompt construction | S1-S4 | Convert Session context, variables, and memory projection into InjectedContext, then render model input through PromptTemplate. | Inputs: Session projection, template variables, agent definition. Outputs: InjectedContext, RenderedPrompt. | Session & Task Manager, Engine Dispatch & Execution. | Stale context, missing variable, cross-tenant memory read, prompt schema drift. | Spring AI PromptTemplate, LangChain4j memory / prompt injection, AgentScope state externalization. |
| AS-L1-F23 | Structured output and result interpretation | S1, S2 | Convert model or engine output into typed domain object, tool result, or Run terminal result. | Inputs: model output, schema, tool result. Outputs: typed result, conversion error, terminal payload. | Engine Dispatch & Execution, Task-Centric Control Layer. | Schema invalid, partial output, tool result mismatch. | Spring AI StructuredOutputConverter, LangChain4j structured output, Semantic Kernel function result. |
| AS-L1-F24 | ChatAdvisor / tool shaping | S2, S4 | Decorate requests, shape tool calls, and interpret responses at the model-call boundary; compose with RuntimeMiddleware without replacing it. | Inputs: ChatClient request, tool definition, model response. Outputs: shaped request, interpreted response, tool-call descriptor. | Task-Centric Control Layer, Engine Dispatch & Execution, agent-middleware. | Advisor short-circuit conflicts with runtime policy, tool-call escape, model-call exception. | Spring AI ChatAdvisor, LangChain4j ToolExecutor, Semantic Kernel plugin filter. |
| AS-L1-F25 | Model / tool / invocation profile | S1-S4 | Absorb differences across Spring AI, LangChain4j, Semantic Kernel, and OpenAI Agents invocation kernels into a governable service-local profile. | Inputs: model config, tool schema, invocation options. Outputs: normalized invocation profile. | Engine Dispatch & Execution, Task-Centric Control Layer. | Unsupported model option, tool schema drift, profile bypasses governance. | Spring AI ChatClient, LangChain4j AiServices, OpenAI Agents tool model, Semantic Kernel plugin model. |

## 6. Orthogonality check

| Boundary | Correct split | Incorrect split |
|---|---|---|
| Run vs Task vs Session | Run owns execution state; Task owns protocol/control state; Session owns context state. | One StateStore swallowing Run, Task, and Session. |
| Task-Centric Control Layer vs Session & Task Manager | Task-Centric Control Layer proposes transition intent; Session & Task Manager executes CAS and persists. | Orchestrator or ExecutorAdapter directly writes Run status. |
| Internal Event Queue vs agent-bus | Internal Event Queue defines service-local event intent and routing; agent-bus provides physical channels. | Treating Layer 3 as a concrete MQ or treating three-track channels as durability modes. |
| RuntimeMiddleware vs ChatAdvisor | RuntimeMiddleware handles Run-aware HookPoints; ChatAdvisor handles model-call boundaries. | Calling both tool interceptors and placing them in the same module. |
| Engine adapter vs service state model | Engine adapter is selected and executed through EngineRegistry; Agent Service owns the state model. | Letting LangGraph, OpenAI Agents, CrewAI, or another SDK runner define Run / Task / Session. |
| Checkpoint vs Session / Memory | Checkpoint is a compute snapshot; Session / Memory are context and knowledge sources of truth. | Using checkpoint as a replacement for Session projection or Memory mutation discipline. |

## 7. Multi-angle reflection

### 7.1 Business completeness

S1-S5 cover request creation, synchronous completion, long-running execution, tool invocation, peer collaboration, client capability callback, cancel, resume, and terminal classification. If AS-L1-F01..F25 hold, the system has an end-to-end path from request ingress to state termination: the external boundary can return cursors, the state source of truth can prevent races, the control layer can handle suspend / resume, the execution layer can adapt heterogeneous engines, and the translation layer can govern model and tool invocation.

### 7.2 Exception completeness

Exception coverage does not depend on one generic fallback. It is distributed across module boundaries: Access Layer handles cross-tenant / idempotency / schema failures; Session & Task Manager handles CAS / illegal transition; Internal Event Queue handles retry / dead-letter / lease; Task-Centric Control Layer handles middleware failure / SuspendSignal / timeout / race; Engine Dispatch & Execution handles adapter mismatch / executor failure; Translation & Tool-Intercept handles structured output / tool-call / prompt drift.

### 7.3 Data-flow completeness

All cross-module data must carry tenantId, traceId, runId, or taskId, and must not lose identity when entering persistence or event channels. RunEvent, S2cCallbackEnvelope, IngressEnvelope, and ToolInvocationRequest must avoid anonymous events, tenant inference, and unbounded payload growth.

### 7.4 Control-flow completeness

Control flow follows one sovereignty chain: ingress normalizes requests, Session & Task Manager creates the source of truth, Task-Centric Control Layer proposes control decisions, Engine Dispatch & Execution executes, Translation & Tool-Intercept shapes invocation, and results return to Task-Centric Control Layer and Session & Task Manager. Shortcuts such as ingress directly invoking Runtime, engine directly writing status, or ChatAdvisor directly executing runtime policy break the architecture.

### 7.5 Industry baseline

Compared with the OSS capability baseline, this list covers A2A Task / AgentCard / input_required, Temporal durable history / signal / timer, Conductor worker poll / retry / dead-letter / human task, LangGraph interrupt / resume / checkpoint, Spring AI / LangChain4j / Semantic Kernel model-tool-function invocation kernels, and AgentScope / AutoGen / CrewAI / OpenAI Agents runtime / actor / handoff / runner patterns. Agent Service does not copy those projects; it consolidates mature patterns into a Java service control plane.

## 8. Outdated or rejected legacy expressions

| Legacy expression | Current judgment | Replacement expression |
|---|---|---|
| “Task as scheduling core = rename Run” | Reject | Task is control state; Run is execution state; both coexist. |
| “InterruptSignal” as Java mechanism name | Reject | Use `SuspendSignal`; Interrupt is only a glossary synonym. |
| “Internal Event Queue = one queue + three storage modes” | Reject | Three physical channels plus per-channel durability tier. |
| “Fast-Path does not require persistence” | Reject | Fast-Path does not require checkpoint; Run / Task metadata and RLS persistence still exist. |
| “RuntimeMiddleware and ChatAdvisor are both Shadow Tool Interceptor” | Reject | RuntimeMiddleware belongs to Task-Centric Control Layer; ChatAdvisor belongs to Translation & Tool-Intercept; they compose but are not equivalent. |
| “Engine adapter decides service state model” | Reject | Engines are adapted; Agent Service owns the Run / Task / Session state model. |
| “A2A TaskStore can replace internal RunRepository” | Reject | A2A TaskStore covers protocol control state only; it cannot carry Run execution state, tenant/RLS, or cancel race semantics. |
| “Workflow history can replace Run / Task / Session” | Reject | Temporal / Conductor durable execution is useful reference material, but it cannot replace Agent-native aggregate boundaries. |

## 9. Final judgment

Agent Service module capabilities should be organized around business-scenario closure, full module ownership, and stable anchors such as Run / Task / Session, RunEvent, three-track channels, RuntimeMiddleware, EngineRegistry, and ChatAdvisor / ContextProjector. AS-L1-F01..F25 is neither a priority-ranked implementation queue nor a new status taxonomy; it is an L1 module capability map that explains how Agent Service supports normal paths, exception paths, data flow, control flow, and the industry-average capability baseline once these module capabilities hold.
---
level: L1
view: development
module: agent-service
status: proposed
authority: "Absorbed from docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.{cn,en}.md §6.5. Anchors back to canonical Engine Dispatch & Execution (Layer 5a per ADR-0140) in ../logical.md §1 + Engine Contract per Rule R-M sub-clauses .a..e."
---

# Engine Dispatch & Execution — Feature Inventory (AS-L1-F33..F39)

> Module: Engine Dispatch & Execution (Layer 5a per ADR-0140).
> Sovereign for: EngineRegistry strict matching (Rule R-M.a/.b), ExecutorAdapter lifecycle, third-party Agent adapter, sub-agent executor boundary, EngineHookSurface emission (HookPoint events go to Layer 4 — Layer 5a does NOT invoke RuntimeMiddleware directly), stream/partial-result handoff, adapter capability + version governance.
> Does NOT own: model/tool translation (lives in Layer 5b — Translation & Tool-Intercept), runtime governance (lives in Layer 4 via HookPoint), Run aggregate state.

| Feature ID | Category | Covered clusters | Capability | Inputs / Outputs | Collaborators | Exception coverage | OSS reference |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AS-L1-F33 | EngineRegistry strict matching | AS-SC01-AS-SC05, AS-SC19 | Every execution goes through `EngineRegistry.resolve(envelope)` and selects exactly one ExecutorAdapter by `engine_type` and capability; no registry bypass or Java subtype dispatch is allowed. | Inputs: EngineEnvelope, capability requirement. Outputs: ExecutorAdapter or EngineMatchingException. | Task-Centric Control Layer, Translation & Tool-Intercept. | engine_type mismatch, missing adapter, capability mismatch. | Spring AI model registry, LangGraph compiled graph registry. |
| AS-L1-F34 | ExecutorAdapter lifecycle | AS-SC09-AS-SC12 | Normalize execute / resume / stream / suspend / checkpoint handoff boundaries for graph executor, agent loop, future actor runtime, crew orchestration, and kernel process. | Inputs: RunContext, resume payload, engine config snapshot. Outputs: result, stream chunk, SuspendSignal, snapshot ref, failure. | Task-Centric Control Layer, Translation & Tool-Intercept, agent-execution-engine. | Executor crash, unsupported resume, stream interruption, incompatible snapshot. | LangGraph4j graph runtime, AgentScope Runtime Runner, OpenAI Agents runner. |
| AS-L1-F35 | Third-party Agent adapter | AS-SC15, AS-SC16, AS-SC20 | Invoke third-party Agents through adapter profiles, record remote invocation handles, and support resuming the same remote invocation. | Inputs: remote agent profile, parent Run context, adapter credentials reference. Outputs: remoteTaskId / remoteThreadId, remote status, remote result. | Task-Centric Control Layer, Session & Task Manager, Access Layer. | Remote auth failure, remote unreachable, lost remote state, adapter schema drift. | A2A peer agent, AgentScope Runtime, OpenAI Agents handoff. |
| AS-L1-F36 | Sub-agent executor boundary | AS-SC17, AS-SC18 | Represent local sub-agents, peer agents, and third-party agents as governable execution targets without letting sub-agent objects escape the service state model. | Inputs: child execution request, agent definition, policy envelope. Outputs: child execution result, SuspendSignal, failure. | Task-Centric Control Layer, Session & Task Manager. | Child loop runaway, policy mismatch, parent cancellation ignored. | CrewAI agent/task process, AutoGen routed agents. |
| AS-L1-F37 | EngineHookSurface | AS-SC13, AS-SC14, AS-SC22 | When executor reaches tool / model / resume / checkpoint / remote-call boundaries, it emits HookPoint events into Task-Centric Control Layer instead of directly invoking RuntimeMiddleware. | Inputs: engine-internal hook boundary. Outputs: HookPoint event. | Task-Centric Control Layer, Translation & Tool-Intercept. | Direct middleware call, missing HookPoint, hook result ignored. | LangChain4j tool callback, Semantic Kernel function filter. |
| AS-L1-F38 | Stream / partial-result handoff | AS-SC03, AS-SC09 | Hand token, step, tool progress, and partial result material to Access Layer / Internal Event Queue for projection instead of letting engines own client streams directly. | Inputs: engine stream chunk, progress event. Outputs: stream projection material, RunEvent material. | Internal Event Queue, Access Layer, Translation & Tool-Intercept. | Stream disconnect, partial-output schema mismatch, slow consumer. | OpenAI Agents stream events, LangGraph streaming. |
| AS-L1-F39 | Adapter capability and version governance | AS-SC19, AS-SC20, AS-SC24 | Manage supported run modes, streaming, tool, checkpoint, S2C, delegation, resume schema, and version capabilities for engine / third-party adapters. | Inputs: adapter metadata, runtime probe, capability contract. Outputs: capability registry entry, version compatibility signal. | Access Layer, Task-Centric Control Layer, Session & Task Manager. | Stale adapter capability, resume schema drift, unsupported run mode. | A2A AgentCard, AgentScope metadata, LangChain4j capabilities. |

## Cross-references

- **Canonical Layer 5a definition**: [`../logical.md`](../logical.md) §1 (5a/5b split per ADR-0140; 5a owns Engine Dispatch & Execution).
- **Engine Contract**: Rule R-M sub-clauses .a (EngineRegistry.resolve), .b (strict matching), .c (HookPoint canonical events), .d (S2C envelope through `bus.spi.s2c`).
- **Scenario anchors**: [`../scenarios.md`](../scenarios.md) AS-SC15-AS-SC18 (third-party Agent + sub-agent), AS-SC19-AS-SC24 (adapter configuration).
- **Process sequences**: [`../process.md`](../process.md) P2 (dispatch decision), P4 (A2A peer collaboration), P5 (S2C callback).
- **Engine envelope contract**: `docs/contracts/engine-envelope.v1.yaml` (Rule R-M.a).
- **SPI 4-way parity**: [`../spi-appendix.md`](../spi-appendix.md) — `EngineRegistry`, `ExecutorAdapter`, `S2cCallbackTransport` (owned by `agent-bus` per ADR-0088).

## Originating source

This file absorbs §6.5 of [`docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.en.md`](../../../logs/reviews/2026-05-26-agent-service-module-capability-feature-list.en.md).

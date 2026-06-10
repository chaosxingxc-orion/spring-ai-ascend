---
level: L1
view: process
status: draft
authority: "ADR-0152 (Uniform L1 per-view mechanism + L0 mounting)"
---

# `agent-runtime` — Process View

## Concurrency model

The A2A protocol surface is served by a single shared `A2aAgentExecutor`
(`implements org.a2aproject.sdk.server.agentexecution.AgentExecutor`),
registered as one Spring bean. Because the bean is a singleton, all
per-task mutable state — the streaming first-artifact flag and the
artifact id — is created **local to each `execute(RequestContext,
AgentEmitter)` invocation** and never hoisted to a field, so concurrent
tasks cannot interfere.

Inbound JSON-RPC is decoded by `A2aJsonRpcController`; streaming calls are
bridged from the SDK's `Flow.Publisher<StreamingEventKind>` to a Reactor
`Flux<ServerSentEvent<String>>`. The SDK event bus (`MainEventBusProcessor`)
drains the per-task `EventQueue` on a background thread and pushes each
`TaskStatusUpdateEvent` / `TaskArtifactUpdateEvent` to the client over SSE.
The agent engine runs on the calling thread inside `execute()`; the
executor owns no thread pool.

## Async/sync boundaries

`execute()` is synchronous from the runtime's perspective: it iterates the
engine result `Stream<AgentExecutionResult>` and emits each result through
`AgentEmitter` in order. The async boundary is owned entirely by the a2a
SDK — emitter calls enqueue events that the SDK background bus delivers to
the SSE subscriber. Blocking (`message/send`) and streaming
(`message/stream`) calls share the same `execute()` path; the SDK's
`ResultAggregator` folds the emitted lifecycle events into one final `Task`
for the blocking case.

## Execution flow

Canonical A2A task lifecycle for one agent turn. Each step reuses an
existing `AgentEmitter` method — the runtime defines no mirror types and
performs no business-state projection. Authoritative run state stays in the
runtime; A2A carries only the protocol task lifecycle.

| Step | Emitter call | A2A state / event | Source signal |
|---|---|---|---|
| 1. Accept | `submit()` | `TASK_STATE_SUBMITTED` | request received |
| 2. Start work | `startWork()` | `TASK_STATE_WORKING` | before the engine runs |
| 3. Stream output | `addArtifact(parts, artifactId, "agent-response", null, append, false)` | `TaskArtifactUpdateEvent` | `AgentExecutionResult.OUTPUT` |
| 4a. Complete | `complete(message)` / `complete()` | `TASK_STATE_COMPLETED` | `COMPLETED` |
| 4b. Fail | `fail(message)` | `TASK_STATE_FAILED` | `FAILED` or thrown exception |
| 4c. Need input | `sendMessage(prompt)` then `requiresInput()` | `TASK_STATE_INPUT_REQUIRED` | `INTERRUPTED` |
| 4d. Cancel | `cancel()` | `TASK_STATE_CANCELED` | `cancel(...)` callback |

Step 3 streams every `OUTPUT` chunk into **one growing artifact**: the
first chunk opens it (`append=false`) and later chunks append to the same
`artifactId` (`append=true`), so the client reconstructs a single artifact
rather than many fragments. `lastChunk` stays `false`; the terminal status
event in step 4 closes the stream.

Reference: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/A2aAgentExecutor.java`.
The sequence aligns with AgentScope-Runtime-Java's `GraphAgentExecutor`
(submitted → working → streamed artifacts → terminal). LangGraph4j ships no
A2A server surface, so it is not a lifecycle reference.

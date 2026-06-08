---
level: L1
view: scenarios
module: agent-service
status: active
authority: "ADR-0143 (rc55 — canonical 4+1 source moved here) + ADR-0138 (rc53 — 5-layer L1 ratification) + ADR-0139 (rc53 — Fast/Slow Path narrowed semantics) + ADR-0141 (rc55 — Internal Event Queue design_only) + ADR-0145 (rc55 — sealed RunEvent hierarchy)"
---

# agent-service — Scenarios View

> **STATUS — agent-runtime pure rebuild (ADR-0159):** `agent-service` is now a serviceization-facade **skeleton**; its former runtime is consolidated into **`agent-runtime`**. Wherever this document references `EngineRegistry` / `ExecutorAdapter` / `EngineEnvelope` / `EngineMatchingException` / `EngineHookSurface` / `HookPoint` / `RuntimeMiddleware` / `HookDispatcher` / `resolve(envelope)`, that engine/hook design is **RETIRED / design_only / historical** — no such Java type exists. The current shipped dispatch lives in `agent-runtime`: `EngineDispatcher` -> `AgentRuntimeHandler` (routed by `agentId` via `AgentRuntimeHandlerRegistry`; unknown `agentId` -> terminal `AGENT_ID_INVALID`; single `control` write authority; egress gated by `control`), verified by `EngineDispatcherTest` / `EngineClosedLoopIntegrationTest`. See Rule R-M and `architecture/docs/L1/agent-runtime/`.

> Authoring source: rc53 review file §14 (`docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md`), ported in rc55 W3 with the following corrections from the rc55 audit (W0 sibling sweep + W1 ADR slate):
>
> - **R2** (`F-design-only-mechanism-shown-as-shipped`): every reference to "Internal Event Queue", `service.queue/`, `DualTrackRouter`, `SlowTrackJudge`, `agent-invoke-request.v1.yaml`, `a2a-envelope.v1.yaml` carries an explicit `(design_only — ADR-NNNN)` annotation in the same paragraph or table cell.
> - **M7** (same family): DualTrackRouter is explicitly marked `(design_only — W2, ADR-0112)` on every path-discriminator mention.
> - **O3** (cancel-race resolution): S5 now names both winner AND loser path; the LOSER sequence diagram lives in `process.md` §P6.
> - **R8** + **ADR-0145**: each scenario annotates which `RunEvent` sealed-hierarchy variants are emitted at which step. `RunEvent` is `status: design_only` per `docs/contracts/run-event.v1.yaml`; the Java sealed type lands in a follow-up impl-mode wave.

## 0. Scenario taxonomy

The five scenarios below cover the full agent-service execution surface
end-to-end. Every shipped HTTP route, every documented suspension path,
every RunEvent emission point traces to one or more of S1-S5. The matrix
below is the L1↔code grounding requirement per Rule G-1.1.a applied at
the scenarios layer.

| ID | Title | Layer-traversal | Path discriminator | Cited route(s) |
|---|---|---|---|---|
| S1 | Standard Synchronous Intake | 1→2→3→4→5a | Fast-Path eligible | `POST /v1/runs` (Fast-Path branch) |
| S2 | Long-Horizon ReAct With Tool Calls | 1→2→3→4↔5a (loop) | Slow-Path required | `POST /v1/runs` (Slow-Path branch) |
| S3 | A2A Peer Collaboration | 1→2→4→5a + outbound A2A | parent suspends; child Run on peer | `POST /v1/runs` parent + IngressEnvelope peer-side |
| S4 | S2C Client Callback | 5a→4→3→client→3→4→5a | Server suspends, client resolves | `POST /v1/runs/{id}/resume` (W2-shipped) |
| S5 | Cancel During Execution | 1→2→4 | Re-auth + atomic CAS | `POST /v1/runs/{id}/cancel` |

Layer numbering follows ADR-0138 / ADR-0140 / ADR-0141 / ADR-0144:
1 Access · 2 Session & Task Manager · 3 Internal Event Queue (design_only
per ADR-0141) · 4 Task-Centric Control · 5a Engine Dispatch & Execution
(per ADR-0140) · 5b Translation & Tool-Intercept (per ADR-0140).

---

## 0.1 Expanded scenario inventory (AS-SC01..AS-SC24) — anchored to S1-S5

> The 24 clusters below anchor to canonical S1-S5 (above) and do NOT add new canonical authority. They are an enterprise-scenario decomposition for downstream design grounding — absorbed from PR #79 / `docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.{cn,en}.md` §3 per the post-merge audit Wave 3 plan. Each row's **Canonical anchor** column ties back to one of S1..S5; the **Covered clusters** column in [`features/*.md`](features/) feature rows references these AS-SC IDs.

| Scenario cluster ID | Canonical anchor | Business scenario | Normal closure | Exception closure |
| --- | --- | --- | --- | --- |
| AS-SC01 | S1 | Short synchronous request | Client calls `POST /v1/runs`; Access Layer binds tenant / idempotency / trace; Run completes quickly and returns a result. | Schema invalid, idempotency conflict, engine mismatch, cross-tenant collapse. |
| AS-SC02 | S1 / S2 | Non-SSE long-task polling | Access Layer returns a Task Cursor; client polls query endpoints for Run / Task status. | Client polling disconnect does not cancel the Run; query re-auth is required; terminal status remains idempotently retrievable. |
| AS-SC03 | S1 / S2 | SSE / streaming access | Client requests streaming state or token / step events; Access Layer owns only the stream boundary, while Run state remains in Session & Task Manager. | After SSE disconnect, client recovers through cursor / event offset / runId; disconnect must not cancel the Run. |
| AS-SC04 | S1 / S2 / physical | Direct-access boundary | Client may connect directly to Agent Service Access Layer; it must not connect directly to engine adapters, RunRepository, middleware, or agent-bus. Mode B business-side deployment keeps the same service boundary. | Direct-to-engine, direct-to-queue, or missing tenant binding is rejected or unreachable. |
| AS-SC05 | S1 / S2 | Multi-protocol ingress convergence | HTTP, future gRPC, future A2A, and future MQ ingress converge into the same Run / Task / Session create and control semantics. | Protocol field differences must not create different state machines; unsupported protocol returns a boundary error. |
| AS-SC06 | S1-S5 | Ingress idempotency and duplicate submit | Same tenant + idempotency key + request hash returns the same create result or an explainable conflict. | Body drift, duplicate submit, and late retry must not create duplicate Runs. |
| AS-SC07 | S1 / S2 | Context recovery after session disconnect | Client re-enters with sessionId / runId / taskId; Session & Task Manager restores Session projection plus visible Run / Task state. | Missing session, tenant mismatch, projection lag, and stale cursor have deterministic responses. |
| AS-SC08 | S2 | Context compaction after overflow | Translation & Tool-Intercept creates a controlled context window from Session projection; Session & Task Manager preserves the boundary between original context state and compacted projection. | Compression loss, prompt overflow, memory mutation race, and cross-tenant memory read are blocked or explicitly failed. |
| AS-SC09 | S2 | Long-task continuation | Task Cursor returns first; Run continues under control / data / rhythm events and ticks; client can query or subscribe to progress. | Timeout, heartbeat loss, queue lag, and executor crash enter SuspendSignal / RunEvent / retry / dead-letter closure. |
| AS-SC10 | S2 | Mid-task execution-locus switch | Run upgrades from Fast-Path to Slow-Path or moves across Mode A / Mode B, instance, or worker; checkpoint / parentNodeKey / RunEvent provide recovery anchors. | Deployment locus changes, incompatible snapshots, and lost resume payloads cannot bypass Layer 2 CAS. |
| AS-SC11 | S2 | Rollback to prior state and retry after failure | Run uses attemptId, parentNodeKey, checkpoint reference, and RunEvent history to express retry boundaries; retry is a controlled attempt of the same Run or an explicit child Run. | Non-idempotent tool side effect, terminal Run, missing checkpoint, and exhausted retry budget become deterministic failure or human-intervention states. |
| AS-SC12 | S2 / S5 | Cancel and completion race | When cancel, complete, fail, and expire race, only `RunRepository.updateIfNotTerminal(...)` decides the winner. | The loser re-reads post-CAS state; same-terminal is idempotent success; different-terminal returns illegal transition. |
| AS-SC13 | S4 | Client-hosted skill invocation | When engine needs local files, UI confirmation, browser capability, or private tools, it throws `SuspendSignal.forClientCallback(...)`; S2C envelope asks the client to execute and then resume. | Client timeout, callbackId mismatch, invalid response schema, and resume re-auth failure prevent the engine from continuing privately. |
| AS-SC14 | S4 | Client skill authorization and capability declaration | Access Layer receives / publishes client capability; Task-Centric Control Layer applies policy / quota / sandbox / audit before invocation; Translation & Tool-Intercept only shapes the tool call. | Claimed but unavailable client capability, over-permission, and unverifiable results become suspend failure or controlled retry. |
| AS-SC15 | S3 / S4 | Third-party Agent invocation | Task-Centric Control Layer spawns a child Run or outbound invocation; Access Layer / IngressGateway handles peer / third-party protocol; Engine Dispatch & Execution executes only through adapters. | Peer unreachable, remote auth failure, remote error envelope, and child terminal failure preserve parentRunId / traceId / tenantId. |
| AS-SC16 | S3 / S4 | Same third-party Agent recovery after interruption | Third-party Agent dispatch records remoteAgentId, remoteThreadId / remoteTaskId / callbackId, adapter profile, and parentRunId; next entry attempts to resume the original remote invocation first. | Missing remote handle, remote terminal state, adapter version drift, and lost remote state must explicitly become retry, failure, or human handling; silently creating a new Agent is not allowed. |
| AS-SC17 | S3 | Agent delegates sub-agent | Parent Run creates child Run; child inherits tenant / trace / policy envelope and returns results to the parent when terminal. | Child timeout, child cancel, child failed, and parent cancelled must decide cascade / detach / fail / resume. |
| AS-SC18 | S3 | Multi-Agent / peer collaboration aggregation | Multiple child Runs or peer Runs return in parallel or sequence; Task-Centric Control Layer performs join / aggregation / conflict classification. | Partial failure, late result, duplicate child completion, and invalid aggregation schema remain auditable. |
| AS-SC19 | S1-S4 | Model configuration ownership | Model provider, model id, temperature, streaming, structured output, and cost / quota profile are expressed as governable service-local profiles; execution uses a resolved snapshot. | Request body overriding governance config, unsupported option, model profile drift, and quota exceeded are controlled. |
| AS-SC20 | S3 / S4 | Third-party Agent adapter configuration ownership | Third-party agent adapter, endpoint, auth mode, capability, resume-handle schema, and timeout / retry policy live at the adapter / agent registry boundary. | Missing adapter, capability mismatch, resume schema drift, and wrong credential scope block execution. |
| AS-SC21 | S1 / S4 | Client information and capability configuration ownership | Client identity, client type, SSE support, callback transport, client-hosted skill list, and permission posture are determined by Access Layer plus Agent / Skill registry inputs. | Stale client capability, unavailable callback transport, and permission mismatch fail before invocation. |
| AS-SC22 | S2 / S4 | Tool / sandbox / skill configuration ownership | Tool schema, skill capacity, sandbox policy, tool allowlist, and memory access policy take effect across RuntimeMiddleware and Translation & Tool-Intercept. | Tool escape, over-wide sandbox grant, capacity exhausted, and policy bypass produce audit plus controlled failure. |
| AS-SC23 | S1-S5 | Observability and audit | Every ingress, state transition, suspend/resume, child Run, S2C callback, third-party invocation, and terminal transition produces traceable evidence. | Anonymous event, missing tenantId, lost terminal event, and payload over inline cap are caught by gate or runtime contracts. |
| AS-SC24 | S1-S5 | Configuration snapshot and runtime drift | Run creation records necessary configuration snapshots or references; resume / retry uses the original snapshot unless explicit policy allows upgrade. | Hot config update changing behavior mid-Run, adapter profile drift, and model option drift are detectable. |

---

## 1. S1 — Standard Synchronous Intake (Fast-Path eligible)

| Field | Value |
|---|---|
| **Actor** | Web / App client → REST `POST /v1/runs` (or gRPC equivalent W2+) |
| **Layers traversed** | Access Layer → Session & Task Manager → Task-Centric Control Layer → Engine Dispatch & Execution Layer (5a) |
| **Run.mode** | `GRAPH` (deterministic short chain) OR `AGENT_LOOP` with low estimated step count |
| **Path discriminator** | DualTrackRouter predicate `(design_only — W2, ADR-0112)` via `SlowTrackJudge` — Fast-Path eligible iff: (i) estimated wall-clock ≤ 5 s, (ii) no external input wait + no S2C callback + no A2A collaboration, (iii) no expected resume on a different deployment locus |
| **Persistence shape** | Run + Task metadata records persisted under RLS at create + at terminal transition; **no intermediate compute checkpoint** (Fast-Path narrowed semantics per ADR-0139; "no mandatory checkpoint/snapshot" — metadata persistence remains mandatory under RLS). Idempotency dedup row persisted at Access Layer per ADR-0057. |
| **Contracts touched** | `openapi-v1.yaml`, `engine-envelope.v1.yaml`, `ingress-envelope.v1.yaml` `(design_only — runtime binding W3+)` if async ingress used |
| **RunEvent emissions** (per ADR-0145) | `RunCreatedEvent` at Layer 2 RunRepository.save → `RunStateTransitionEvent(PENDING→RUNNING)` after Layer 2 CAS → `RunStateTransitionEvent(RUNNING→SUCCEEDED)` at terminal → `TerminalTransitionEvent(SUCCEEDED)` |
| **Boundary contract** | Wall-clock ≤ Fast-Path bound; if exceeded mid-execution, the Layer 5a executor throws `SuspendSignal` and Layer 4 transitions the Run to SUSPENDED via Layer 2's `RunRepository.updateIfNotTerminal(...)` CAS — at which point S1 has implicitly upgraded to S2. |
| **Failure modes** | (a) **Cross-tenant request**: 404 not_found at W0 per Rule R-J.b (the W1 widening to 403 `tenant_mismatch` + WARN audit is deferred per ADR-0108). (b) **Idempotency-Key collision**: 409 `idempotency_conflict` (same hash) or 409 `idempotency_body_drift` (different hash) per ADR-0057. (c) **Engine envelope schema violation**: `EngineMatchingException` → Run FAILED with reason `engine_mismatch` per Rule R-M.b → `RunStateTransitionEvent(PENDING→FAILED)` + `TerminalTransitionEvent(FAILED, finalReason=engine_mismatch)`. |
| **Test grounding** | `RunHttpContractIT.createReturnsPending`, `RunStatusTransitionIT` (PENDING→RUNNING→SUCCEEDED happy path) — confirms the persistence + state-transition shape. |

---

## 2. S2 — Long-Horizon ReAct With Tool Calls (Slow-Path)

| Field | Value |
|---|---|
| **Actor** | Web / App client requesting a multi-tool agent run |
| **Layers traversed** | Access → Session & Task Manager → Internal Event Queue `(design_only — ADR-0141)` → Task-Centric Control ↔ Engine Dispatch & Execution Layer (5a) (loop with `HookPoint.before_tool` / `after_tool` middleware events dispatched into Layer 4 per ADR-0140) ↔ Translation & Tool-Intercept Layer (5b) `(design_only for most consumers — ChatAdvisor + PromptTemplate + StructuredOutputConverter contract status per ADR-0125 / 0130-0132)` |
| **Run.mode** | `AGENT_LOOP` |
| **Path discriminator** | DualTrackRouter `(design_only — W2, ADR-0112)` chooses Slow-Path when multi-step OR tool calls are likely OR external input/callback is expected. |
| **Persistence shape** | Run + Task records under RLS; **Checkpointer snapshots intermediate state** at each tool-call boundary (Checkpointer SPI per ADR-0021; in-memory ref impl `InMemoryCheckpointer` shipped W0; durable backend W2). Resume from any checkpoint by `RunStatus.SUSPENDED → RUNNING` via `RunRepository.updateIfNotTerminal(...)` CAS (per ADR-0142 — Layer 4 invokes Layer 2's typed primitive; Layer 4 NEVER writes Run state directly). |
| **Contracts touched** | `engine-envelope.v1.yaml`, `engine-hooks.v1.yaml`, `model-invocation.v1.yaml` `(design_only — tool_call_loop section per ADR-0134)`, `memory-store.v1.yaml` `(design_only — ADR-0123 / 0133)` |
| **RunEvent emissions** | `RunCreatedEvent` → `RunStateTransitionEvent(PENDING→RUNNING)` → `[SuspendRequestedEvent(parentNodeKey=N, suspendReason=AwaitTool) + RunStateTransitionEvent(RUNNING→SUSPENDED)]×K` (K = number of tool-call boundaries) → `[ResumeRequestedEvent(resumeCause=EXTERNAL_TRIGGER) + RunStateTransitionEvent(SUSPENDED→RUNNING)]×K` → `RunStateTransitionEvent(RUNNING→SUCCEEDED)` → `TerminalTransitionEvent(SUCCEEDED)`. All `IN_SCOPE` evolution export. |
| **Boundary contract** | Each tool call is bracketed by `HookPoint.before_tool` + `HookPoint.after_tool` events dispatched through the Layer 4 RuntimeMiddleware chain (per Rule R-M.c — RuntimeMiddleware lives EXCLUSIVELY in Layer 4 per ADR-0140; Layer 5a does NOT directly invoke RuntimeMiddleware). Tool execution governance per Rule R-M.c. Skill capacity arbitration per Rule R-K and `skill-capacity.yaml`. Neither Fast-Path NOR Slow-Path may violate Rule R-G (reactive I/O) / Rule R-H (no Thread.sleep) / Rule R-J.a (RLS on tenant_id tables). |
| **Failure modes** | (a) **Tool execution timeout**: Run remains in RUNNING until tool returns or the middleware throws SuspendSignal → `SuspendRequestedEvent(suspendReason=AwaitTool)`. (b) **Resume on different deployment locus** (Mode B → Mode A per ADR-0101): state recovered from Checkpointer; `tenantId` re-validated per Rule R-J.b (Resume re-auth widening deferred to W2 per Rule R-J.b.d). (c) **Tool middleware short-circuits** (Rule R-M.c `HookOutcome.ShortCircuit`): Run continues without invoking the tool; emission is `HookPoint.after_tool` only (no `before_tool`/`after_tool` pair). |
| **Test grounding** | `NestedDualModeIT` (3-level graph→agent-loop→graph nesting via SuspendSignal — confirms the suspend/resume boundary). |

---

## 3. S3 — A2A Peer Collaboration

| Field | Value |
|---|---|
| **Actor** | Agent A (this instance) calls Agent B (peer instance) for sub-task delegation; Agent A's Run suspends until Agent B returns. |
| **Layers traversed** | Access Layer (A2A Client outbound + A2A Server inbound on peer side; `(design_only — W3+ when SDK lands; a2a-envelope.v1.yaml status design_only per ADR-0100 §rejected-framing #1 — no `a2a-java` SDK runtime dep)`) → Task-Centric Control Layer suspends parent Run via Layer 2 CAS → Engine Dispatch & Execution Layer (5a) dispatches child Run to peer via `IngressEnvelope` over three-track `control` channel per Rule R-E. |
| **Run.mode** | Parent: `GRAPH` or `AGENT_LOOP`; child Run on peer: independent (peer chooses). |
| **Contracts touched** | `a2a-envelope.v1.yaml` `(design_only at W1; no `a2a-java` SDK runtime dep per ADR-0100 §rejected-framing #1)`, `ingress-envelope.v1.yaml` `(design_only — Rule R-I.b; runtime binding W3+)`, `engine-envelope.v1.yaml` (runtime_enforced). |
| **RunEvent emissions** (parent side) | `RunCreatedEvent(parent)` → `RunStateTransitionEvent(PENDING→RUNNING)` → `ChildRunSpawnedEvent(childRunId=X, childRunMode=…, joinPolicy=…)` → `SuspendRequestedEvent(suspendReason=AwaitChildRun)` + `RunStateTransitionEvent(RUNNING→SUSPENDED)` → … (peer executes child) … → `ChildRunCompletedEvent(childRunId=X, childTerminalStatus=SUCCEEDED)` → `ResumeRequestedEvent(resumeCause=CHILD_COMPLETED)` + `RunStateTransitionEvent(SUSPENDED→RUNNING)` → `RunStateTransitionEvent(RUNNING→SUCCEEDED)` + `TerminalTransitionEvent(SUCCEEDED)`. Peer side emits its own independent RunEvent sequence (starts with its own `RunCreatedEvent(parentRunId=A's runId)`). |
| **Boundary contract** | Parent Run's suspension uses `SuspendSignal.forClientCallback(...)` checked variant (rc3 wave per ADR-0074). Peer Run uses its own `RunRepository` instance. Correlation via `parentRunId` + `traceId` per Run record fields. The Layer 2 ↔ Layer 4 single-owner contract (ADR-0142) means Layer 4 NEVER writes Run state directly on either side — always via Layer 2's `updateIfNotTerminal(...)`. |
| **Failure modes** | (a) **Peer unreachable**: `SuspendReason.AwaitChildRun` times out (or `AwaitClientCallback` for the S2C-shaped sub-variant); Run transitions FAILED with `peer_unreachable` reason → `RunStateTransitionEvent(SUSPENDED→FAILED)` + `TerminalTransitionEvent(FAILED, finalReason=peer_unreachable)`. (b) **Peer returns error envelope**: parent Run resumes and decides recovery (retry per ADR-0118 OR FAILED transition via `RunRepository.updateIfNotTerminal(...)` CAS). (c) **Cross-tenant peer call**: rejected at A2A Server side per Rule R-I.1 + IngressGateway authentication (W3+ when SDK lands). |
| **Test grounding** | `NestedDualModeIT` (3-level nesting confirms parent-child correlation); A2A SDK integration tests are W3+ scope. |

---

## 4. S4 — S2C Client Callback (Server Suspends, Asks Client for Capability)

| Field | Value |
|---|---|
| **Actor** | Server-side Run needs a client-side capability (e.g., user confirmation, browser cookie, local-file access); Run suspends with `SuspendSignal.forClientCallback(...)` and the client resolves via `POST /v1/runs/{runId}/resume` (W2-shipped) carrying the resolved capability response. |
| **Layers traversed** | Engine Dispatch & Execution Layer (5a — executor throws `SuspendSignal.forClientCallback`) → Task-Centric Control Layer (Layer 4 catches; calls Layer 2 `RunRepository.updateIfNotTerminal(... SUSPENDED)`; persists Checkpointer snapshot) → Internal Event Queue `(design_only — ADR-0141)` publishes `S2cCallbackEnvelope` on the three-track `control` channel → client → `data` channel carries the response payload (16 KiB inline cap per Rule R-E) → Resume (Layer 4 invokes Layer 2 `updateIfNotTerminal(... RUNNING)`). |
| **Run.mode** | Inherits from parent execution. |
| **Contracts touched** | `s2c-callback.v1.yaml` (runtime_enforced per Rule R-M.d), `engine-envelope.v1.yaml`, `engine-hooks.v1.yaml` (HookPoint.before_suspension + before_resume). |
| **RunEvent emissions** | `RunCreatedEvent` → `RunStateTransitionEvent(PENDING→RUNNING)` → `[Layer 5a throws SuspendSignal.forClientCallback]` → `S2cCallbackRequestedEvent(callbackEnvelope=…, capability=…)` (evolutionExport=`OPT_IN` — client-bound, telemetry-export.v1.yaml gated) + `SuspendRequestedEvent(suspendReason=AwaitClientCallback)` + `RunStateTransitionEvent(RUNNING→SUSPENDED)` → … (client resolves) … → `S2cCallbackCompletedEvent(callbackId=…, outcome=SUCCESS)` (evolutionExport=`OPT_IN`) + `ResumeRequestedEvent(resumeCause=S2C_CALLBACK_RECEIVED)` + `RunStateTransitionEvent(SUSPENDED→RUNNING)` → terminal. |
| **Boundary contract** | `RunStatus.RUNNING → SUSPENDED` transition is atomic CAS via `RunRepository.updateIfNotTerminal(...)` (per ADR-0142 Layer 2 ownership). `SuspendReason = AwaitClientCallback`. On resume, the `SuspendSignal.forClientCallback(...)` is unwound and the executor continues with the resolved capability response injected as `resumePayload`. Callbacks consume the `s2c.client.callback` skill capacity declared in `skill-capacity.yaml` (Rule R-M.d). |
| **Failure modes** | (a) **Client times out**: `SuspendReason.AwaitClientCallback` expires; Run transitions FAILED via CAS → `S2cCallbackCompletedEvent(outcome=TIMEOUT)` + `TerminalTransitionEvent(FAILED, finalReason=s2c_callback_timeout)`. (b) **Skill capacity exhausted** (Rule R-K + `s2c.client.callback` row of `skill-capacity.yaml`): caller suspended with `SuspendReason.RateLimited` *(W2-deferred: scheduler admission per Rule R-K.c — the W0/W1 surface returns `SkillResolution.reject(SuspendReason.RateLimited)` envelope only)*. (c) **Response envelope schema-invalid**: validation against `s2c-callback.v1.yaml#response` fails → `S2cCallbackCompletedEvent(outcome=SCHEMA_INVALID)` + `RunStateTransitionEvent(SUSPENDED→FAILED)` with `s2c_response_invalid` reason. |
| **Test grounding** | `SuspendSignalTest` (SuspendSignal construction + `childRunId` / `s2cCallbackEnvelope` accessor); S2C integration tests are W2-shipped. |

---

## 5. S5 — Cancel During Execution (Cancel Re-auth + Cancel Race)

| Field | Value |
|---|---|
| **Actor** | Client calls `POST /v1/runs/{runId}/cancel`; Run may be in RUNNING, SUSPENDED, PENDING, or already in a terminal state. |
| **Layers traversed** | Access Layer (`RunController.cancel`) → Session & Task Manager (Layer 2 RunRepository load + tenant guard) → `RunStateMachine.validate` invoked atomically inside Layer 2's `updateIfNotTerminal(...)` per ADR-0142 (Layer 4 holds typed reference but does NOT write directly). |
| **Persistence shape** | The cancel transition is **atomic CAS** via `RunRepository.updateIfNotTerminal(this.tenantId, this.runId, RunStatus.CANCELLED)` (abstract method per ADR-0118). The abstract method's implementation MUST be a single SQL update statement with a `WHERE status NOT IN (CANCELLED, SUCCEEDED, FAILED, EXPIRED)` clause (or equivalent atomic primitive — `ConcurrentHashMap.computeIfPresent` for `InMemoryRunRegistry`). |
| **Authorization** | `RunController.cancel` re-validates `(request.tenantId == Run.tenantId)`; cross-tenant collapses to **404 not_found** at W0 per Rule R-J.b. The W1 widening to 403 `tenant_mismatch` + WARN audit MDC `(runId, fromStatus, toStatus, actor, occurredAt)` is deferred per ADR-0108. |
| **Contracts touched** | `openapi-v1.yaml` (cancel route shape, error envelope `{error:{code,message,details}}` per Rule R-F + enforcer E8). |
| **RunEvent emissions** (winner path) | `CancelRequestedEvent(actor=…)` → `RunStateTransitionEvent(<previous>→CANCELLED)` + `TerminalTransitionEvent(CANCELLED)`. Both `IN_SCOPE`. |
| **Outcomes** | (a) **Active → terminal** (RUNNING/SUSPENDED/PENDING → CANCELLED): success, returns 200. (b) **Same-status terminal** (CANCELLED → cancel): idempotent, returns 200; no `RunStateTransitionEvent` emitted (CAS no-op) but `CancelRequestedEvent` IS emitted as an audit signal. (c) **Different-terminal** (SUCCEEDED/FAILED/EXPIRED → cancel): returns 409 `illegal_state_transition`; no state change; `CancelRequestedEvent` emitted as a rejection audit signal. (d) **Concurrent cancel-vs-complete race**: the CAS `WHERE` clause wins one writer; the loser sees the post-CAS Run row and returns the appropriate status code based on it — see `process.md` §P6 for the loser's sequence diagram (O3 audit finding from rc55). |
| **Failure modes structurally closed** | The 4-recurrence cancel-vs-complete race (`F-nonatomic-run-status-write` rc35/rc36/rc38/rc39) is **structurally closed at this entry** by the abstract `updateIfNotTerminal` method. Any new write path on Run state introduces a 5th recurrence risk — gated by the `RunRepository.updateIfNotTerminal` abstract-method discipline + ADR-0142's single-owner pinning of the Run aggregate to Layer 2. |
| **Test grounding** | `RunHttpContractIT.cancelTerminalReturns409` + `RunHttpContractIT.cancel_route_is_post_not_delete` + `RunHttpContractIT.getCrossTenantRunReturns404` (cross-tenant access collapses to 404 not_found at W0 per Rule R-J.b; the historical `tenantMismatchReturns403` name was retired per AUD-2026-05-27 PR77-P2-1) — confirm the canonical authorization + state-transition shape on the cancel route. |

---

### S6 (cross-reference) — Weather Clarification (PR 92 v1.2 baseline)

A complete end-to-end HITL-plus-tool scenario walking through M1–M6 and demonstrating: MQ ingress → AccessIntent normalisation → Native ReAct Agent first round → INTERRUPT_REGISTERED control event → SUSPENDED → callback → RESUMING → tool call → second LLM round → COMPLETED. Source: [`../../../../docs/logs/reviews/2026-05-28-agent-service-m1-m6-design-draft.cn.md`](../../../../docs/logs/reviews/2026-05-28-agent-service-m1-m6-design-draft.cn.md) §Scenario. Authority: ADR-0155.

---

## 6. Cross-scenario invariants

The following invariants hold across ALL S1-S5; they are the "red lines"
the rc55 audit identified (rc55 sibling sweep `docs/logs/reviews/2026-05-26-agent-service-l1-sibling-sweep.en.md`),
restated here as scenario-view obligations:

1. **No tenantId-less data flow.** Every RunEvent variant declares
   `tenantId` per Rule R-C.2.a; every persistence write is RLS-bound
   per Rule R-J.a; every cross-layer call propagates `tenantId` via
   `RunContext.tenantId()` (NOT via `TenantContextHolder`, per Rule
   R-C.e — runtime sub-package never imports the platform-side ThreadLocal).
2. **No Run state write outside `RunRepository.updateIfNotTerminal(...)`
   atomic CAS.** Layer 4 holds typed reference + invokes; Layer 2 owns
   the CAS contract; Layer 5a NEVER writes Run state. Create-only
   `RunRepository.save(new Run(…PENDING…))` is grandfathered per the
   rc39 ADR-0118 source-guard discipline.
3. **No single-tier internal queue with mode-based durability.** Layer
   3's binding (when its code home lands per ADR-0141) MUST route by
   intent into the three physical channels (`control` / `data` /
   `rhythm`) declared in `bus-channels.yaml`. Each channel chooses its
   own durability tier independently.
4. **Neither Fast-Path NOR Slow-Path may violate Rule R-G** (reactive
   I/O — no RestTemplate / JdbcTemplate under `service.runtime.**`),
   **Rule R-H** (no `Thread.sleep` under `service.{platform,runtime}.**`),
   or **Rule R-J.a** (RLS on tenant_id tables). The Fast-Path narrowed
   semantics per ADR-0139 say "no mandatory checkpoint/snapshot" — NOT
   "no mandatory persistence"; metadata persistence remains mandatory.

These four red lines are gate-closure criteria for every wave touching
the scenarios surface; any draft that violates one is rejected.

---

## 7. Cross-references

- Process View: each Sk scenario has a sibling Pk sequence diagram in
  [`process.md`](process.md) (with S5 splitting into P3 winner + P6
  loser per the O3 rc55 finding).
- Logical View: the 5-layer model + 5a/5b split per ADR-0140 + Run
  aggregate single-owner per ADR-0142 + RunEvent hierarchy per
  ADR-0145 lives in [`logical.md`](logical.md).
- Physical View: 5-plane deployment + RLS + 3-track bus + sandbox
  boundary lives in [`physical.md`](physical.md).
- Development View: package tree + Layer↔Package matrix per ADR-0144
  + 5 L2 Boundary Contracts lives in [`development.md`](development.md).
- SPI Appendix: 9 active SPI interfaces with 4-way parity per Rule
<<<<<<<< HEAD:docs/architecture/l0/l1/agent-service/scenarios.md
  G-1.1.b lives in [`spi-appendix.md`](05-contracts/spi-appendix.md).
- Module-root grounding: [`agent-service/ARCHITECTURE.md`](../../../../../agent-service/ARCHITECTURE.md)
  carries shipped-state implementation details + dependencies + wave
  plan + risks.
- Historical: rc53 review file [`docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md`](../../../../logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md)
========
  G-1.1.b lives in [`spi-appendix.md`](spi-appendix.md).
- Module-root grounding: [`ARCHITECTURE.md`](ARCHITECTURE.md)
  carries shipped-state implementation details + dependencies + wave
  plan + risks.
- Historical: rc53 review file [`docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md`](../../../../docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md)
>>>>>>>> origin/main:architecture/docs/L1/agent-service/scenarios.md
  §14 is the original authoring of S1-S5; demoted to historical
  authoring record per ADR-0143.

---
adr_id: ADR-0155
title: AgentService L1 v1.2 internal module design absorption
status: accepted
date: 2026-05-28
---

# ADR-0155 — AgentService L1 v1.2 internal module design absorption

This is the engineering-prose companion to
[`0155-agent-service-l1-v1-2-internal-module-design.yaml`](./0155-agent-service-l1-v1-2-internal-module-design.yaml).
The yaml is the structured authority; this file gives the reasoning trail
for the six boundary-decision reversals.

## 1. STM-03 sole-caller (TCC-03 owns transitions)

PR 92 self-audit finding H1 caught a cross-module breach: the v1 draft
of `TTI-09 (HITL interrupt gateway)` proposed calling STM-03 directly to
push the Run from RUNNING to SUSPENDED. This breaks the M4-sole-driver
constraint already declared by Rule R-C.2.b and the existing logical
view (rc55 §1, ADR-0142 Run aggregate single owner).

The reversal: TTI-09 emits a `ControlEvent { kind: INTERRUPT_REGISTERED,
payloadRef: InterruptRegistration }` onto IEQ-02 (control channel),
TCC-06A consumes it, validates current state == RUNNING, and drives the
STM-03 CAS itself. M4 remains the unique caller; no defence-in-depth
double-write path exists.

## 2. responseSnapshot owner (TCC-03 terminal-state hook)

Self-audit H4: the v1 draft made M1 Access Layer responsible for writing
the responseSnapshot back to STM-08 after delivering the terminal reply.
This created two structural defects:

- M1 is not a state-decision module; idempotent response-snapshot
  writeback is a "terminal-transition consequence" semantically owned by
  whoever drove the terminal transition.
- Multiple reply channels (SYNC_HTTP, SSE_STREAM, MQ_REPLY,
  PUSH_NOTIFICATION) make the "when M1 writes back" timing ambiguous.

Reversal: TCC-03, immediately after a successful CAS to {COMPLETED,
FAILED, CANCELLED}, synchronously (within the per-Run actor) calls
STM-08 to bind the response snapshot. M1's reply projection becomes a
pure read of the same snapshot.

## 3. M6 prompt-construction reversal (v1.2)

This is the deepest reversal. The v1 draft made M6 the canonical prompt
constructor — owning system-instruction assembly, history retrieval,
RAG chunk injection, tool-spec composition. Mid-design human review
(self-audit §11.1) raised: this is impossible for third-party frameworks
(AgentScope-java + LangGraph4j have their own Formatter chains) and
out-of-scope for remote agents (their prompt assembly is entirely
remote). Forcing M6 to be the constructor:

- Either makes M6 a universal Agent-template library (violates
  single-responsibility);
- Or makes Agent code call M6 with a "draft" that is already a
  fully-formed messages list (M6 just re-emits it).

The v1.2 resolution: M6 is a messages-in-flight aspect, analogous to an
HTTP API Gateway. Agents construct their own messages (native code
self-assembly / third-party Formatter / remote autonomy). M6 receives
the constructed messages at the model-call boundary and applies:

- TTI-02 boundary treatment (policy chain, PII redaction, token-budget
  audit, fallback trim with `BUDGET_FALLBACK_TRIM` audit event).
- TTI-03 vendor-adapter invocation + ContentBlock normalisation.

The `BuiltPrompt` contract is deleted. `GovernedMessages` replaces it as
M6's downstream output. Context assembly stays the Agent's job;
`PlatformMemoryProvider` is the read-only SPI Agents call to retrieve
STM-04 facts.

## 4. ExecutorAdapter three forms + in-process deployment

PR 92 EDE-02/03/04 binds three deployment shapes explicitly:

- **Native** (`EDE-02`, in-process): platform beans injected via DI;
  Agent code calls `PlatformChatClient.invoke(...)` synchronously into
  M6.
- **Third-party** (`EDE-03`, in-process): startup-time replacement of
  the third-party framework's `Model / Toolkit / Memory` abstractions
  with platform bridges; compliance scan refuses to register if bridges
  are incomplete.
- **Remote** (`EDE-04`, out-of-process): A2A protocol client; remote
  agent's internal resource calls (`model / tool / memory`) are NOT
  intercepted by local M6 — they happen in the remote process and are
  outside this jurisdiction. M6 only audits A2A outbound messages at
  TTI-08 policy chain.

Code ownership and deployment topology are now declared as orthogonal
dimensions (a self-developed agent CAN be deployed remotely; that
combination uses EDE-04). `EDE-08 InjectionMode { NATIVE_DI |
THIRD_PARTY_BRIDGE | EVENT_RELAY | NONE }` captures the wiring choice.

## 5. IEQ three-channel topology

Internal Event Queue is physically partitioned into three independent
channels:

- **Control**: `AccessIntent` from M1, `cancel / resume / callback /
  deadline-fired / interrupt-registered / resume-accepted / spawn-child`
  produced by sibling modules.
- **Data**: `WorkItem` carrying `engine-tick / tool-invoke /
  child-run-start / checkpoint / resume-tick`.
- **Egress**: per-Run topic for outward projection (`StateChanged /
  token / tool-progress / artifact / input-required / terminal`).

Each channel has its own bounded buffer + worker pool + back-pressure
policy. Cross-channel ordering is undefined; per-Run causality lives in
STM-09 monotonic cursor.

## 6. CANCEL_RACE_RESOLVED_AS_{COMPLETED, CANCELLED}

Self-audit M3: the v1 draft was silent on what happens when CANCEL
arrives while the Run is mid-`WORKITEM_DONE`. v1.2 rules:

- If child Runs are still unsettled: stay in `CANCEL_REQUESTED`, wait
  for child Run settlement, then `CANCEL_REQUESTED → CANCELLED`.
- If no children and the in-flight `WORKITEM_DONE` carries a complete
  final artifact: take `COMPLETED` (user already received the result).
  RunEvent reason = `CANCEL_RACE_RESOLVED_AS_COMPLETED`.
- If no children and the artifact is partial: take `CANCELLED`.
  RunEvent reason = `CANCEL_RACE_RESOLVED_AS_CANCELLED`.

## Cross-links

- PR 92 design source: [`docs/logs/reviews/2026-05-28-agent-service-m1-m6-design-draft.cn.md`](../logs/reviews/2026-05-28-agent-service-m1-m6-design-draft.cn.md).
- PR 92 self-audit: [`docs/logs/reviews/2026-05-28-agent-service-design-self-audit.cn.md`](../logs/reviews/2026-05-28-agent-service-design-self-audit.cn.md).
- Canonical L1 logical view (post-absorption): [`architecture/docs/L1/agent-service/logical.md`](../../architecture/docs/L1/agent-service/logical.md).

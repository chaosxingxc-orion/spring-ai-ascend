---
level: L1
view: process
module: agent-service
status: active
authority: "ADR-0143 (rc55 — canonical 4+1 source moved here) + ADR-0138 (rc53 — 5-layer L1 ratification) + ADR-0139 (rc53 — Fast/Slow Path narrowed semantics) + ADR-0142 (rc55 — Run aggregate single owner; Layer 4 invokes Layer 2 via RunRepository CAS) + ADR-0145 (rc55 — sealed RunEvent emissions per scenario)"
---

# agent-service — Process View

> Authoring source: rc53 review file §16 (`docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md`), ported in rc55 W4 with corrections:
>
> - **R2** + **M7** (`F-design-only-mechanism-shown-as-shipped`): every diagram caption annotates `DualTrackRouter` / `SlowTrackJudge` / `service.queue` references as `(design_only — W2, ADR-0112 / ADR-0141)`.
> - **O3** (cancel-race resolution): new P6 sequence diagram shows the LOSER's path (CAS no-op + post-CAS re-read + appropriate response code).
> - **R3** + **ADR-0142** (Run aggregate single-owner): every Run state write shown as Layer 4 → `RunRepository.updateIfNotTerminal(tenantId, runId, λ)` invocation into Layer 2; Layer 4 NEVER calls `Run.withStatus(...)` directly.
> - **M6** + **ADR-0145**: RunEvent emissions annotated on every state-transition arrow.
> - **Cross-scenario invariants** from `scenarios.md` §6 (red lines) preserved in every sequence.

## 1. P1 — Standard Synchronous Intake → State Machine → (optional) Suspend → Resume (covers S1 + S2)

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client (Web / App)
    participant GW as L1: Access::Gateway
    participant Trace as L1: TraceExtractFilter
    participant TF as L1: TenantContextFilter + JwtTenantClaimCrossCheck
    participant Idem as L1: IdempotencyHeaderFilter
    participant Run as L1: RunController
    participant RR as L2: RunRepository  (single owner per ADR-0142)
    participant Orch as L4: Orchestrator
    participant DTR as L4: DualTrackRouter <i>(design_only — W2, ADR-0112)</i>
    participant Reg as L5a: EngineRegistry
    participant Exec as L5a: ExecutorAdapter
    participant Mid as L4: RuntimeMiddleware chain  (HookPoint dispatch — Layer 4 EXCLUSIVE per ADR-0140)
    participant Queue as L3: Internal Event Queue <i>(design_only — ADR-0141)</i>

    Client->>GW: POST /v1/runs<br/>X-Tenant-Id, Idempotency-Key, JWT
    GW->>Trace: filter chain
    Trace->>TF: forward (originate trace_id if absent)
    TF->>TF: bind tenantId (JWT claim cross-check per ADR-0040)
    TF->>Idem: forward
    Idem->>Idem: claimOrFind(tid, key, hash) — ADR-0057
    alt Idempotency hit
        Idem-->>Client: 200 cached response<br/>(or 409 idempotency_conflict / 409 idempotency_body_drift)
    else fresh request
        Idem->>Run: pass through
        Run->>RR: save(Run with status=PENDING, tenantId=tid)<br/><i>create-only path per rc39 source-guard</i>
        RR-->>Queue: <i>(when L3 lands)</i> publish RunCreatedEvent on data channel (ADR-0145; evolutionExport=IN_SCOPE)
        RR-->>Run: Run record (PENDING)
        Run->>Orch: dispatch(runId, envelope)
        Orch->>DTR: judge(envelope, predicate)
        alt Fast-Path eligible (S1)
            DTR-->>Orch: FastPath
            Orch->>RR: updateIfNotTerminal(tid, runId, λ→RUNNING) — ATOMIC CAS per ADR-0142
            RR->>RR: RunStateMachine.validate(PENDING, RUNNING) — atomic inside CAS
            RR-->>Queue: <i>(when L3 lands)</i> publish RunStateTransitionEvent(PENDING→RUNNING) on data channel
            RR-->>Orch: ok
            Orch->>Reg: resolve(envelope)
            Reg-->>Orch: ExecutorAdapter
            Orch->>Exec: execute(runContext)
            Exec->>Mid: emits HookPoint.before_llm (Layer 5a → Layer 4)
            Mid->>Mid: dispatch chain
            Mid-->>Exec: HookOutcome.Proceed
            Exec-->>Orch: Result (no SuspendSignal)
            Orch->>RR: updateIfNotTerminal(tid, runId, λ→SUCCEEDED) — ATOMIC CAS
            RR-->>Queue: publish RunStateTransitionEvent(RUNNING→SUCCEEDED) + TerminalTransitionEvent(SUCCEEDED)
            Run-->>Client: 200 RunResponse
        else Slow-Path required (S2)
            DTR-->>Orch: SlowPath
            Run-->>Client: 202 TaskCursor (Rule R-F)
            Orch->>RR: updateIfNotTerminal(tid, runId, λ→RUNNING) — ATOMIC CAS
            Orch->>Reg: resolve(envelope)
            Reg-->>Orch: ExecutorAdapter
            Orch->>Exec: execute(runContext)
            Exec->>Mid: emits HookPoint.before_tool
            Mid-->>Exec: HookOutcome.Proceed
            Exec--xOrch: throws SuspendSignal(parentNodeKey, ...)
            Orch->>RR: updateIfNotTerminal(tid, runId, λ→SUSPENDED) — ATOMIC CAS
            RR-->>Queue: publish SuspendRequestedEvent + RunStateTransitionEvent(RUNNING→SUSPENDED) on control + data
            Note over Orch,RR: Run is now suspended;<br/>Checkpointer persists snapshot.<br/>Client polls /v1/runs/{runId} or uses SSE (W2-shipped).
        end
    end
```

## 2. P2 — Fast-Path / Slow-Path Decision Tree (covers S1 / S2 divergence)

```mermaid
sequenceDiagram
    autonumber
    participant Orch as L4: Orchestrator
    participant DTR as L4: DualTrackRouter <i>(design_only — W2, ADR-0112)</i><br/>SlowTrackJudge implementation
    participant RC as L2: Run record  (tenantId required, Rule R-C.2.a)
    participant Cfg as L4: routing policy <i>(design_only — dual-track-routing-policy.yaml W2)</i>

    Orch->>DTR: judge(runId, envelope, run)
    DTR->>RC: read estimated_wall_clock + has_external_input + has_s2c_callback + has_a2a_collab + estimated_deployment_locus
    RC-->>DTR: metadata
    DTR->>Cfg: lookup tenant-policy thresholds
    Cfg-->>DTR: { fast_path_wall_clock_max=5s, fast_path_forbidden_features=[s2c, a2a, cross_deployment] }
    alt All fast-path predicates pass
        DTR-->>Orch: FastPath
        Note over Orch: Persistence shape: Run + Task metadata only.<br/>NO mandatory checkpoint/snapshot (ADR-0139).<br/>tenantId / RLS / reactive / SuspendSignal STILL apply (red line 4).
    else Any predicate fails
        DTR-->>Orch: SlowPath
        Note over Orch: Persistence shape: + Checkpointer snapshots at every tool-call boundary.<br/>SuspendSignal flow + ResumeDispatcher active.
    end
```

## 3. P3 — Cancel During Execution + Re-auth + Cancel Race WINNER (covers S5 winner side)

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client
    participant Run as L1: RunController.cancel
    participant TF as L1: TenantContextFilter
    participant RR as L2: RunRepository  (CAS owner per ADR-0142)
    participant SM as L2: RunStateMachine  (atomic inside CAS)
    participant Queue as L3: Internal Event Queue <i>(design_only)</i>

    Client->>Run: POST /v1/runs/{runId}/cancel<br/>X-Tenant-Id: tid_request, JWT
    Run->>TF: filter chain (tenant binding)
    TF->>Run: tenantId = tid_request
    Run->>RR: findById(runId)
    RR-->>Run: run (status=RUNNING, tenantId=tid_run)
    alt tid_request != tid_run  (cross-tenant)
        Run-->>Client: 404 not_found  (Rule R-J.b at W0;<br/>W1 widening to 403 tenant_mismatch + WARN audit deferred per ADR-0108)
    else tid_request == tid_run
        Run->>RR: updateIfNotTerminal(tid_request, runId, λ run → run.withStatus(CANCELLED)) — ATOMIC CAS
        Note over RR: CAS WHERE status NOT IN (CANCELLED, SUCCEEDED, FAILED, EXPIRED)
        RR->>SM: validate(RUNNING, CANCELLED)
        SM-->>RR: ok (legal transition)
        alt CAS succeeded (winner — this writer)
            RR-->>Queue: publish CancelRequestedEvent(actor=jwt_sub) + RunStateTransitionEvent(RUNNING→CANCELLED) + TerminalTransitionEvent(CANCELLED)
            RR-->>Run: cancelled
            Run-->>Client: 200 (CANCELLED)
        else CAS no-op (Run already in same-status terminal)
            RR-->>Run: post-CAS Run = CANCELLED
            RR-->>Queue: publish CancelRequestedEvent(actor=jwt_sub) [audit signal only; no state change]
            Run-->>Client: 200 (idempotent — already CANCELLED)
        else CAS rejected (Run in different terminal)
            RR-->>Run: post-CAS Run = SUCCEEDED / FAILED / EXPIRED
            RR-->>Queue: publish CancelRequestedEvent(actor=jwt_sub) [rejection audit signal]
            Run-->>Client: 409 illegal_state_transition
        end
    end
```

## 4. P4 — A2A Peer Collaboration (covers S3)

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client
    participant RunA as agent-service A: RunController
    participant OrchA as agent-service A: L4 Orchestrator
    participant ExecA as agent-service A: L5a ExecutorAdapter
    participant RRA as agent-service A: L2 RunRepository
    participant IngressA as agent-service A: L1 IngressGateway <i>(design_only — Rule R-I.b)</i>
    participant Bus as agent-bus: 3-track channels  (Rule R-E)
    participant IngressB as agent-service B: L1 IngressGateway
    participant RunB as agent-service B: L4 Orchestrator
    participant RRB as agent-service B: L2 RunRepository

    Client->>RunA: POST /v1/runs (parent run)
    RunA->>RRA: save(parentRun PENDING)
    RunA->>OrchA: dispatch
    OrchA->>RRA: updateIfNotTerminal(λ→RUNNING)
    OrchA->>ExecA: execute
    ExecA--xOrchA: throws SuspendSignal(child-run variant)<br/>(SuspendReason.AwaitChildren)
    OrchA->>RRA: updateIfNotTerminal(λ→SUSPENDED)
    Note over RRA: publish ChildRunSpawnedEvent + SuspendRequestedEvent + RunStateTransitionEvent(RUNNING→SUSPENDED)
    OrchA->>IngressA: spawnChildOnPeer(parentRunId, childMode, childDef, tenantId)
    IngressA->>Bus: publish on control channel (IngressEnvelope)
    Bus->>IngressB: deliver (cross-tenant rejected at IngressB per Rule R-I.1)
    IngressB->>RunB: createChildRun(envelope)
    RunB->>RRB: save(childRun PENDING, parentRunId=A.runId, tenantId=…)
    Note over RRB: publish RunCreatedEvent(parentRunId=A.runId)
    RunB->>RunB: full execution lifecycle (S1 or S2)
    RunB->>RRB: updateIfNotTerminal(λ→SUCCEEDED)
    RunB->>Bus: publish ChildRunCompletedEvent on data channel
    Bus->>IngressA: deliver
    IngressA->>OrchA: childCompleted(childRunId, terminalStatus)
    OrchA->>RRA: updateIfNotTerminal(λ→RUNNING)<br/>publish ResumeRequestedEvent(resumeCause=CHILD_COMPLETED)
    OrchA->>ExecA: resume execution
    ExecA-->>OrchA: Result
    OrchA->>RRA: updateIfNotTerminal(λ→SUCCEEDED)
    RunA-->>Client: 200 RunResponse (when polled; original 202 carries TaskCursor)
```

## 5. P5 — S2C Client Callback (covers S4)

```mermaid
sequenceDiagram
    autonumber
    participant Client as External Client
    participant RunCtl as L1: RunController.resume <i>(W2-shipped)</i>
    participant Orch as L4: Orchestrator
    participant Exec as L5a: ExecutorAdapter
    participant RR as L2: RunRepository
    participant Trans as agent-bus: S2cCallbackTransport SPI<br/>(bus.spi.s2c — runtime_enforced per Rule R-M.d)
    participant Bus as agent-bus: 3-track channels

    Exec--xOrch: throws SuspendSignal.forClientCallback(parentNodeKey, S2cCallbackEnvelope)<br/>(ADR-0074 checked variant)
    Orch->>Trans: publish(envelope, capability=s2c.client.callback)
    Trans->>Bus: publish on CONTROL channel (highest priority, out-of-band)
    Orch->>RR: updateIfNotTerminal(λ→SUSPENDED with SuspendReason.AwaitClientCallback)
    Note over RR: publish S2cCallbackRequestedEvent (evolutionExport=OPT_IN — client-bound)<br/>+ SuspendRequestedEvent + RunStateTransitionEvent(RUNNING→SUSPENDED)
    Bus->>Client: deliver envelope (via webhook / SSE / push transport)

    Note over Client: Client resolves capability<br/>(user input, file access, etc.)

    Client->>RunCtl: POST /v1/runs/{runId}/resume<br/>{ callbackId, response }
    RunCtl->>Trans: validateResponseSchema(response, s2c-callback.v1.yaml#response)
    alt schema-valid
        Trans->>Bus: publish response on DATA channel (16 KiB inline cap per Rule R-E)
        Bus->>Orch: deliver
        Orch->>RR: updateIfNotTerminal(λ→RUNNING)
        Note over RR: publish S2cCallbackCompletedEvent(outcome=SUCCESS) + ResumeRequestedEvent + RunStateTransitionEvent(SUSPENDED→RUNNING)
        Orch->>Exec: resume with resumePayload injected (unwinds SuspendSignal.forClientCallback)
        Exec-->>Orch: Result
        Orch->>RR: updateIfNotTerminal(λ→SUCCEEDED)
    else schema-invalid
        Trans-->>RunCtl: validation_failed
        RunCtl->>Orch: notifyResumeFailure(s2c_response_invalid)
        Orch->>RR: updateIfNotTerminal(λ→FAILED with finalReason=s2c_response_invalid)
        Note over RR: publish S2cCallbackCompletedEvent(outcome=SCHEMA_INVALID) + RunStateTransitionEvent(SUSPENDED→FAILED) + TerminalTransitionEvent(FAILED)
        RunCtl-->>Client: 400 invalid_request
    end

    alt SuspendReason.AwaitClientCallback expires (client times out)
        Note over Orch: deadline timer fires (Tick Engine rhythm channel)
        Orch->>RR: updateIfNotTerminal(λ→FAILED with finalReason=s2c_callback_timeout)
        Note over RR: publish S2cCallbackCompletedEvent(outcome=TIMEOUT) + TerminalTransitionEvent(FAILED)
    end
```

## 6. P6 — Cancel Race LOSER Sequence (O3 — new in rc55)

> This sequence diagram closes the O3 audit finding in rc55 W0 sibling
> sweep. The cancel-race winner (P3 above) is well-documented; the
> loser side was implicit — what code path does the loser take, and
> what response code does the user see? This diagram makes it explicit.

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client (cancel requester)
    participant Run as L1: RunController.cancel
    participant TF as L1: TenantContextFilter
    participant RR as L2: RunRepository  (CAS owner per ADR-0142)
    participant Orch as L4: Orchestrator  (concurrent terminal writer — the "winner")

    par Concurrent cancel-vs-complete race
        Client->>Run: POST /v1/runs/{runId}/cancel
        Run->>TF: tenant binding
        Run->>RR: findById(runId)
        RR-->>Run: run (status=RUNNING, tenantId=tid)
    and
        Note over Orch: Run completes successfully<br/>RIGHT BEFORE the cancel CAS attempt
        Orch->>RR: updateIfNotTerminal(tid, runId, λ→SUCCEEDED) — ATOMIC CAS
        Note over RR: CAS WINS (status transitions RUNNING → SUCCEEDED)<br/>publish RunStateTransitionEvent + TerminalTransitionEvent
        RR-->>Orch: ok
    end

    Note over RR: Cancel side's perspective: Run was RUNNING at findById,<br/>but the orchestrator's terminal CAS commits BEFORE the cancel's CAS.

    Run->>RR: updateIfNotTerminal(tid, runId, λ→CANCELLED) — ATOMIC CAS
    Note over RR: CAS WHERE status NOT IN (CANCELLED, SUCCEEDED, FAILED, EXPIRED)<br/>**LOSES** because status is now SUCCEEDED.
    RR->>RR: post-CAS re-read: status = SUCCEEDED  (not the requested CANCELLED)
    RR-->>Run: outcome=CASMissedTerminal, post-CAS-status=SUCCEEDED

    Run->>Run: classify outcome:<br/>requestedTransition=CANCELLED<br/>actualTerminal=SUCCEEDED<br/>requested != actual + actual is terminal<br/>=> 409 illegal_state_transition (S5(c))
    Run-->>Client: 409 illegal_state_transition<br/>{error:{code:"illegal_state_transition", message:"Run already terminal: SUCCEEDED", details:{currentStatus:"SUCCEEDED"}}}

    Note over Run: Audit signal: still emit CancelRequestedEvent<br/>(rejection audit per ADR-0145; outcome encoded in event payload)
```

**Loser-side response code matrix** (cancel race outcomes — completes
the §S5 outcome table in `scenarios.md`):

| Actual post-CAS terminal | Requested transition | Response code | Rationale |
|---|---|---|---|
| CANCELLED | CANCELLED | 200 | Idempotent — same-status terminal cancel |
| SUCCEEDED | CANCELLED | 409 `illegal_state_transition` | Different-terminal cancel rejected |
| FAILED | CANCELLED | 409 `illegal_state_transition` | Different-terminal cancel rejected |
| EXPIRED | CANCELLED | 409 `illegal_state_transition` | Different-terminal cancel rejected |

Note: every loser-side path still emits `CancelRequestedEvent`
(per `docs/contracts/run-event.v1.yaml`) as a rejection audit signal,
so downstream observability can count rejected cancels per tenant +
correlate with concurrent orchestrator completions. The CAS no-op
itself does NOT emit `RunStateTransitionEvent` (no transition
happened); the `TerminalTransitionEvent` was already emitted by the
winner's CAS.

## 7. Cross-references

- Scenarios: each Pk sequence maps to a sibling Sk scenario in
  [`scenarios.md`](scenarios.md). P3 and P6 together cover S5
  (winner + loser).
- Logical: layer numbering and component names come from
  [`logical.md`](logical.md) §1 (5-layer diagram with ADR-0140
  5a/5b split + ADR-0142 single-owner pinning).
- Physical: cross-channel routing constraints (`control` / `data` /
  `rhythm`) per [`physical.md`](physical.md) §3 (three-track bus
  binding per Rule R-E + `bus-channels.yaml`).
- Development: package homes for each participant in the sequences
  per [`development.md`](development.md) §1 + §2 (layer↔package
  matrix per ADR-0144).
- RunEvent emissions: every state-transition arrow's emission set
  cross-walks to [`logical.md`](logical.md) §7 (RunEvent sealed
  hierarchy + scenario-coverage matrix) and
  [`docs/contracts/run-event.v1.yaml`](../../contracts/run-event.v1.yaml)
  for variant field definitions.

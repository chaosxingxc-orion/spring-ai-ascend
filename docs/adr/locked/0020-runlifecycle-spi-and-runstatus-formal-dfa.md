---
adr_id: ADR-0020
product_claim: "PC-001|PC-003"
binds_features: [FEAT-RUN-LIFECYCLE-CONTROL]
binds_rules: [R-C.2]
---

# 0020. RunLifecycle SPI Separation and RunStatus Formal DFA

**Status:** accepted
**Deciders:** architecture
**Date:** 2026-05-12
**Technical story:** Third architecture reviewer raised three related issues: (Issue 4) `Orchestrator` SPI overloaded with five concerns in one `run()` method; (Issue 5) `RunStatus` state machine has no formal transition rules; (Issue 8) SPI defines states (`CANCELLED`, `FAILED`) but provides no operations (`cancel()`, `retry()`) to reach them. Self-audit surfaced four additional gaps: (HD-B.1) no `EXPIRED` terminal state for deadline-elapsed suspensions; (HD-B.2) no transition audit trail; (HD-B.3) transition idempotency unspecified; (HD-B.4) no optimistic-lock version on `Run`. This ADR addresses all seven through one cohesive design move.

## Context

`Orchestrator` currently exposes one method: `Object run(UUID, String, ExecutorDefinition, Object)`. This
method implicitly handles: (1) run creation or resume, (2) checkpoint management, (3) child-run dispatch,
(4) failure recovery, (5) result formatting. These five concerns evolve independently across waves — cancel
arrives at W2, streaming at W2, re-authorization at W2, Temporal lifecycle at W4.

`RunStatus` has six values (`PENDING, RUNNING, SUSPENDED, SUCCEEDED, FAILED, CANCELLED`) but §4 never
specifies which transitions are legal. Without a formal DFA, the three planned orchestrator implementations
(`SyncOrchestrator`, `PostgresOrchestrator`, `TemporalOrchestrator`) may disagree on legal state flows.

`RunStatus.EXPIRED` is needed for timer-bound suspensions whose deadline elapses (per ADR-0019
`SuspendReason.deadline()`) without resume.

## Decision Drivers

- W2 introduces `cancel` and `resume` HTTP endpoints. `RunController` must call lifecycle operations
  without coupling to the execution dispatch path.
- W4 Temporal takes over the run lifecycle entirely; `TemporalOrchestrator` must implement the same
  `RunLifecycle` SPI as `SyncOrchestrator`.
- `RunStatus.EXPIRED` is required by the watchdog sweeper that fires when `SuspendReason.deadline()` elapses.

## Considered Options

1. **Keep `Orchestrator.run()` monolithic; add `cancel()` and `resume()` overloads on the same interface**
   — low migration cost; still couples lifecycle and dispatch.
2. **Split `RunLifecycle` out as a separate SPI interface; keep `Orchestrator` for execution dispatch only**
   (this decision) — clean separation; W4 Temporal can implement `RunLifecycle` without touching dispatch.
3. **Add lifecycle methods on `RunRepository`** — repository becomes a state-machine controller, violating
   single-responsibility.

## Decision Outcome

**Chosen option:** Option 2 — separate `RunLifecycle` SPI.

### Part 1 — RunLifecycle SPI (Issues 4 + 8)

```java
// com.huawei.ascend.runtime.orchestration.spi — pure java.*
public interface RunLifecycle {
    /**
     * Requests cancellation of a live Run. Idempotent if already CANCELLED.
     * Returns HTTP 409 (via IllegalStateException) if Run is in a terminal
     * non-CANCELLED state (SUCCEEDED, FAILED, EXPIRED).
     */
    Run cancel(UUID runId, String tenantId, String reason);

    /**
     * Resumes a SUSPENDED Run. Re-authorization boundary: tenantId MUST match
     * Run.tenantId (§4 #14, Rule 17). Returns HTTP 409 if Run is not SUSPENDED.
     */
    Run resume(UUID runId, String tenantId, Object resumePayload);

    /**
     * Retries a FAILED Run. Produces a new attemptId.
     * Returns HTTP 409 if Run is not FAILED.
     */
    Run retry(UUID runId, String tenantId);
}
```

`Orchestrator` retains its current `Object run(...)` signature. A streaming overload
(`Flux<RunEvent> stream(...)`) is defined in §4 #11 and implemented at W2 (Rule 15 trigger).

`RunLifecycle` is a **W2 interface** — not shipped at W0 (no callers; no HTTP controller yet). This ADR
defines the interface contract; the Java file is created at W2 alongside `RunController`.

### Part 2 — RunStatus formal DFA (Issue 5 + HD-B.1)

Legal transitions — complete DFA:

| From | Allowed `To` values | Trigger |
|---|---|---|
| `PENDING` | `RUNNING`, `CANCELLED` | orchestrator picks up / cancel before start |
| `RUNNING` | `SUSPENDED`, `SUCCEEDED`, `FAILED`, `CANCELLED` | signal / result / error / cancel |
| `SUSPENDED` | `RUNNING`, `EXPIRED`, `FAILED`, `CANCELLED` | resume / deadline / error / cancel |
| `FAILED` | `RUNNING` | `RunLifecycle.retry()` (new `attemptId`) |
| `SUCCEEDED` | — | terminal |
| `CANCELLED` | — | terminal |
| `EXPIRED` | — | terminal (HD-B.1) |

`RunStatus.EXPIRED` is added to the enum at W0 (shipped in this delivery).

Transitions not listed above are illegal. `RunStateMachine.validate(from, to)` enforces this; it is called
from `Run.withStatus(newStatus)` and `Run.withSuspension(...)`. Illegal transitions throw
`IllegalStateException`; orchestrator and HTTP controller layers map to HTTP 409.

### Part 3 — Transition audit trail (HD-B.2, deferred to W2)

At W2 Postgres, every transition writes a row to `run_state_change`:

```sql
CREATE TABLE run_state_change (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id       UUID NOT NULL REFERENCES runs(run_id),
    tenant_id    TEXT NOT NULL,
    from_status  TEXT NOT NULL,
    to_status    TEXT NOT NULL,
    actor        TEXT,         -- "system" | principal identity
    reason       TEXT,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

W0 in-memory impl: no-op. Tracked as `run_state_change_audit_log` in `architecture-status.yaml`.

### Part 4 — Transition idempotency (HD-B.3)

- `cancel` on an already-`CANCELLED` run → returns existing `Run` (idempotent, 200 OK).
- `cancel` on `SUCCEEDED`, `FAILED`, or `EXPIRED` → `IllegalStateException` → HTTP 409.
- `retry` on a non-`FAILED` run → `IllegalStateException` → HTTP 409.

`RunStateMachine.validate(from, to)` throws with a descriptive message; HTTP status mapping is the caller's responsibility.

### Part 5 — Optimistic lock (HD-B.4, deferred to W2)

`Run` record gains a `long version` field at W2 alongside Postgres-backed `RunRepository`. `RunRepository.save(run)` performs `UPDATE ... WHERE run_id = ? AND version = ?`; no-row-match throws `OptimisticLockException`. Tracked as `run_optimistic_lock` in `architecture-status.yaml`.

### Consequences

**Positive:**
- `RunController` (W2) calls `RunLifecycle` methods; `Orchestrator` stays focused on execution dispatch.
- `TemporalOrchestrator` (W4) implements `RunLifecycle` via Temporal workflow signals; no change to `Orchestrator.run()`.
- Formal DFA makes all three planned orchestrator implementations agree on legal transitions from W0.
- `EXPIRED` terminal state enables a W2 watchdog sweeper.

**Negative:**
- W2 must ship two beans (`Orchestrator` impl + `RunLifecycle` impl); potential for DI misconfiguration.

### Reversal cost

Low for the DFA (code change only). Medium for the `RunLifecycle` SPI split (requires updating
`RunController` callers at W2 if the interface changes shape).

## References

- Third-reviewer document: `docs/logs/reviews/Architectural Perspective Review` (Issues 4, 5, 8)
- Response document: `docs/logs/reviews/2026-05-12-third-reviewer-response.en.md` (Cat-B)
- §4 #20 (RunStatus formal transition DFA + transition audit trail)
- Rule R-C.d (active): RunStatus transition validity
- Rule R-J.b (deferred, W2): RunLifecycle re-authorization
- `architecture-status.yaml` rows: `run_status_transition_validator`, `run_lifecycle_spi`,
  `run_state_change_audit_log`, `run_optimistic_lock`
- W2 wave plan: `docs/archive/2026-05-13-plans-archived/engineering-plan-W0-W4.md` §4.2 (archived per ADR-0037)

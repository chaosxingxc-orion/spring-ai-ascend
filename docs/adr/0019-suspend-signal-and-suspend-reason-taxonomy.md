# 0019. SuspendSignal: Checked-Exception Primitive and Sealed SuspendReason Taxonomy

**Status:** accepted
**Deciders:** architecture
**Date:** 2026-05-12
**Technical story:** Third architecture reviewer raised two issues: (Issue 1) SuspendSignal as a checked exception poisons functional composition; (Issue 2) composition model supports only sequential parent→child nesting and cannot express fan-out. Self-audit surfaced three additional gaps: (HD-A.1) no suspend deadline, (HD-A.2) child failure propagation undefined, (HD-A.3) per-reason resume-payload schema missing. This ADR addresses all five through one cohesive design move.

## Context

`SuspendSignal extends Exception` (checked). It is thrown from `NodeFunction.apply`, `Reasoner.reason`,
`RunContext.suspendForChild`, `GraphExecutor.execute`, and `AgentLoopExecutor.execute` — then caught
exclusively inside `Orchestrator` (the catch/checkpoint/dispatch/resume loop). This is the runtime's
one interrupt primitive for both `GraphExecutor` (deterministic graph) and `AgentLoopExecutor`
(ReAct-style).

**Issue 1 claim**: exception semantics are "fundamentally at odds" with continuation/yield semantics,
and the checked form pollutes `throws` signatures and blocks higher-order composition.

**Issue 2 claim**: the composition primitive is unary (one parent waits for one child), blocking real
workflows that fan out to N parallel subtasks.

**Self-audit hidden defects:**
- **HD-A.1**: No max-suspend duration. A run suspended for an external approval that never arrives parks forever.
- **HD-A.2**: Child-run failure propagation to parent is undefined. `SyncOrchestrator.executeLoop` handles only the success path.
- **HD-A.3**: Per-reason resume-payload schema is unspecified. `AwaitChildren` resume requires `Map<UUID, Object>`; `AwaitTimer` requires no payload; `AwaitApproval` requires an approver decision. All currently typed as `Object`.

## Decision Drivers

- Java 21 (LTS) has no first-class coroutine or continuation primitive.
- Suspend points in this architecture are NOT arbitrary — they occur only at explicitly bounded
  `RunContext.suspendForChild` call sites. This is not "arbitrary yield."
- The `throws SuspendSignal` declaration on SPI boundary methods is a design feature: it forces callers
  (executors) to explicitly handle or propagate the signal, making suspend compile-time visible at the SPI.
- Functional composition (`map`, `filter`, `Stream`) over `NodeFunction` lambdas that throw checked
  exceptions is infeasible in Java. But the architecture does not require such composition — graph nodes
  are dispatched by name, not composed functionally.

## Considered Options

1. **Checked exception (current; this decision)** — keep `SuspendSignal extends Exception`; add sealed
   `SuspendReason` for typed-reason dispatch.
2. **Unchecked exception** (`extends RuntimeException`) — removes `throws` clause; makes suspend invisible
   to the compiler; suspend can propagate silently from any code path.
3. **Result-monad return type** — `NodeFunction.apply` returns `Result<Object, SuspendRequest>`. Forces
   every call site to handle `SuspendRequest`; high migration cost; no reduction in explicit handling burden.
4. **Virtual-thread parking** (Project Loom) — executor parks the carrier thread; requires a Loom
   scheduler; breaks the property that "executors do not persist or wait"; not portable to W4 Temporal.

## Decision Outcome

**Chosen option:** Option 1 — keep checked exception; add sealed `SuspendReason` taxonomy.

### Part 1 — Checked exception (Issue 1 verdict: reject primitive change / accept scoping)

`SuspendSignal` remains `extends Exception` (checked). The reviewer's claim that this is "fundamentally
at odds" with continuation semantics holds philosophically, but in Java this is the only option that:
- Makes every suspend site compile-time visible via the `throws` clause.
- Forces the orchestrator catch point to be exhaustive.
- Prevents accidental suspend propagation out of the orchestrator boundary into non-executor code.

**What we accept from Issue 1**: restrict `throws SuspendSignal` to five designated SPI boundary methods
only. An ArchUnit rule (`SuspendSignalBoundaryTest`) added at W2 alongside `HookChainConformanceTest`
asserts no class outside the orchestration SPI surface declares `throws SuspendSignal`.

### Part 2 — Sealed SuspendReason taxonomy (Issue 2 + HD-A.1 + HD-A.2 + HD-A.3)

Introduce `sealed interface SuspendReason` with the following permitted variants:

```java
// com.huawei.ascend.runtime.orchestration.spi — pure java.*
public sealed interface SuspendReason
        permits SuspendReason.ChildRun, SuspendReason.AwaitChildren,
                SuspendReason.AwaitTimer, SuspendReason.AwaitExternal,
                SuspendReason.AwaitApproval, SuspendReason.RateLimited {

    Instant deadline();  // HD-A.1: every reason declares when the suspension expires

    // Single child run — current W0 model
    record ChildRun(
            UUID childRunId,
            ChildFailurePolicy failurePolicy,  // HD-A.2: parent reacts to child failure
            Instant deadline
    ) implements SuspendReason {}

    // N-ary fan-out (Issue 2): parent waits for multiple children
    record AwaitChildren(
            List<UUID> childRunIds,
            JoinPolicy joinPolicy,       // ALL | ANY | N_OF
            int nOfCount,               // used when joinPolicy == N_OF
            ChildFailurePolicy failurePolicy,
            Instant deadline
    ) implements SuspendReason {}

    record AwaitTimer(Instant fireAt) implements SuspendReason {
        public Instant deadline() { return fireAt; }
    }

    record AwaitExternal(String callbackToken, Instant deadline) implements SuspendReason {}

    record AwaitApproval(String approvalRequestId, Instant deadline) implements SuspendReason {}

    record RateLimited(String resourceKey, Instant retryAfter) implements SuspendReason {
        public Instant deadline() { return retryAfter; }
    }
}

public enum JoinPolicy { ALL, ANY, N_OF }
public enum ChildFailurePolicy { PROPAGATE, IGNORE, COMPENSATE }
```

`SuspendSignal` is updated at W2 to carry a `SuspendReason` alongside existing fields. The `final`
modifier is removed; `SuspendSignal` becomes a concrete non-final class. The existing
`(parentNodeKey, resumePayload, childMode, childDef)` constructor is retained for W0 backward
compatibility; a second constructor accepting `SuspendReason` is added at W2.

**W0 scope**: only the `ChildRun` variant is implemented in `SyncOrchestrator`. All other variants are
contract-level. The sealed interface is defined in code at W2 alongside the async orchestrator.

The per-reason resume-payload schema (HD-A.3) is implied by the variant:
- `ChildRun` / `AwaitChildren` → `Map<UUID, Object>` (per-child results, indexed by childRunId)
- `AwaitTimer` → `Void`
- `AwaitApproval` → approver's decision record

### Consequences

**Positive:**
- Fan-out is expressible at the contract level from W0.
- Every suspension carries a deadline — enabling a W2 watchdog sweeper to expire stuck runs.
- `ChildFailurePolicy` makes child-failure semantics explicit and per-call-site configurable.
- Per-variant resume-payload schema is self-documenting; orchestrators pattern-match on `SuspendReason`.

**Negative:**
- W0 reference impl cannot demonstrate fan-out (deferred to W2); taxonomy is design-only at W0.
- W2 `SuspendSignal` constructor update requires updating all `RunContext.suspendForChild` call sites.

### Reversal cost

Medium — changing the suspend primitive requires updating all executor implementations that throw
`SuspendSignal`. The sealed interface is additive; new variants can be added without breaking existing code.

## Pros and Cons of Options

### Option 1: Checked exception + sealed SuspendReason (chosen)
- Pro: compile-time visibility at every suspend site.
- Pro: typed taxonomy enables per-reason orchestrator logic.
- Con: `throws SuspendSignal` in lambda signatures blocks direct use with `java.util.function.Function`.

### Option 2: Unchecked exception
- Pro: no `throws` clause in SPI signatures.
- Con: suspend propagates silently through any code path, including non-executor code.

### Option 3: Result monad
- Pro: purely functional; no exceptions.
- Con: every node must return `Result<O, SuspendRequest>`; existing executor code must be rewritten.

### Option 4: Virtual-thread parking
- Pro: looks like blocking code; no special return type or throws.
- Con: requires Loom scheduler; complex lifecycle; not portable to W4 Temporal.

## References

- Third-reviewer document: `docs/logs/reviews/Architectural Perspective Review` (Issues 1, 2)
- Response document: `docs/logs/reviews/2026-05-12-third-reviewer-response.en.md` (Cat-A)
- §4 #19 (fan-out, suspend-reason taxonomy, suspend-deadline contract)
- `architecture-status.yaml` rows: `suspend_reason_taxonomy`, `parallel_child_dispatch`, `suspend_deadline_watchdog`
- W2 wave plan: `docs/archive/2026-05-13-plans-archived/engineering-plan-W0-W4.md` §4.2 (archived per ADR-0037)

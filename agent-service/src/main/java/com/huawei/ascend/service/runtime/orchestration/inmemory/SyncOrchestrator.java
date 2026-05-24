package com.huawei.ascend.service.runtime.orchestration.inmemory;

import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.middleware.HookDispatcher;
import com.huawei.ascend.engine.orchestration.spi.Checkpointer;
import com.huawei.ascend.engine.spi.EngineMatchingException;
import com.huawei.ascend.engine.orchestration.spi.ExecutorDefinition;
import com.huawei.ascend.middleware.spi.HookContext;
import com.huawei.ascend.middleware.spi.HookPoint;
import com.huawei.ascend.engine.orchestration.spi.Orchestrator;
import com.huawei.ascend.engine.orchestration.spi.RunContext;
import com.huawei.ascend.engine.orchestration.spi.SuspendSignal;
import com.huawei.ascend.service.runtime.posture.AppPostureGate;
import com.huawei.ascend.service.runtime.resilience.spi.SuspendReason;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.engine.orchestration.spi.RunMode;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;
import com.huawei.ascend.service.runtime.runs.RunStateMachine;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackEnvelope;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackResponse;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackTransport;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;

/**
 * Reference Orchestrator for in-memory / dev-posture execution.
 *
 * Owns the suspend/checkpoint/resume loop:
 *  1. On SuspendSignal: persist checkpoint, mark parent SUSPENDED, dispatch child.
 *  2. On child completion: load parent checkpoint, transition parent back to RUNNING,
 *     re-invoke parent executor with child result as the resume payload.
 *
 * This implementation is single-threaded (child dispatch is synchronous / recursive).
 * W2 replaces this with a Postgres-backed async orchestrator; the SPI surface is identical.
 */
public final class SyncOrchestrator implements Orchestrator {

    /**
     * Default upper bound on a single S2C round-trip when the envelope
     * carries no explicit deadline. Chosen to be long enough to cover normal
     * client think-time without permanently pinning an orchestrator worker
     * thread when a transport misbehaves.
     */
    static final Duration DEFAULT_S2C_CALL_TIMEOUT = Duration.ofSeconds(30);

    private final RunRepository runs;
    private final Checkpointer checkpointer;
    private final EngineRegistry engineRegistry;
    private final HookDispatcher hookDispatcher;
    private final Duration s2cCallTimeout;

    /**
     * W2.x Phase 1 (ADR-0072): dispatch goes through {@link EngineRegistry}
     * exclusively. Pattern-matching on {@link ExecutorDefinition} subtypes
     * outside the registry is forbidden by Rule 43.
     *
     * <p>W2.x Phase 2 (ADR-0073): the orchestrator fires three structural
     * hooks ({@link HookPoint#ON_ERROR}, {@link HookPoint#BEFORE_SUSPENSION},
     * {@link HookPoint#BEFORE_RESUME}) via {@link EngineRegistry#hookDispatcher()}.
     * Returned {@link com.huawei.ascend.middleware.spi.HookOutcome} is
     * DISCARDED at every call-site; Run-state consumption (Fail abort,
     * ShortCircuit bypass) is deferred to W2 Telemetry Vertical per Rule 45.b.
     * The discard is intentional — the dispatcher already enforces in-chain
     * fail-fast among middlewares, but the Run lifecycle is unaffected today.
     */
    public SyncOrchestrator(RunRepository runs, Checkpointer checkpointer, EngineRegistry engineRegistry) {
        this(runs, checkpointer, engineRegistry, DEFAULT_S2C_CALL_TIMEOUT);
    }

    public SyncOrchestrator(RunRepository runs, Checkpointer checkpointer,
                            EngineRegistry engineRegistry, Duration s2cCallTimeout) {
        AppPostureGate.requireDevForInMemoryComponent("SyncOrchestrator");
        this.runs = Objects.requireNonNull(runs);
        this.checkpointer = Objects.requireNonNull(checkpointer);
        this.engineRegistry = Objects.requireNonNull(engineRegistry, "engineRegistry is required");
        this.hookDispatcher = engineRegistry.hookDispatcher();
        this.s2cCallTimeout = Objects.requireNonNull(s2cCallTimeout, "s2cCallTimeout is required");
        if (s2cCallTimeout.isNegative() || s2cCallTimeout.isZero()) {
            throw new IllegalArgumentException("s2cCallTimeout must be positive, got " + s2cCallTimeout);
        }
    }

    @Override
    public Object run(UUID runId, String tenantId, ExecutorDefinition def, Object initialPayload) {
        // Persist the freshly-created Run before the first status transition so the
        // atomic compare-and-set in mutateIfNotTerminal (RunRepository.updateIfNotTerminal,
        // backed by computeIfPresent) has a row to operate on. A top-level Run reaches
        // here unsaved; child Runs are pre-saved by the suspension branch below.
        Run run = runs.findById(runId).orElseGet(() -> runs.save(createRun(runId, tenantId, def)));
        run = mutateIfNotTerminal(run, r -> r.withStatus(RunStatus.RUNNING));
        if (RunStateMachine.isTerminal(run.status())) {
            return null;
        }
        return executeLoop(run, def, initialPayload);
    }

    /**
     * W0 atomicity invariant (ADR-0024): checkpoint write and RunRepository.save(suspended)
     * are on the same call stack; single-threaded recursion ensures sequential ordering.
     * W2 mandate: both writes MUST move inside a single @Transactional block.
     */
    private Object executeLoop(Run run, ExecutorDefinition def, Object payload) {
        while (true) {
            RunContextImpl ctx = new RunContextImpl(run.tenantId(), run.runId(), checkpointer);
            try {
                Object result = dispatch(ctx, def, payload);
                // Cancel-vs-complete race guard: a parallel HTTP cancel may have
                // already transitioned the Run to a terminal state (CANCELLED).
                // mutateIfNotTerminal re-reads from the repository before writing
                // SUCCEEDED so the terminal state wins instead of being silently
                // overwritten.
                run = mutateIfNotTerminal(run, r -> r.withStatus(RunStatus.SUCCEEDED).withFinishedAt(Instant.now()));
                if (RunStateMachine.isTerminal(run.status()) && run.status() != RunStatus.SUCCEEDED) {
                    // A parallel cancel won the terminal race; the Run is now CANCELLED.
                    // Do not report the executor result as a success for a cancelled Run.
                    return null;
                }
                return result;
            } catch (SuspendSignal signal) {
                // v2.0.0-rc3 refactor (cross-constraint audit α-2 / β-5): S2C
                // client-callback suspension is now a checked SuspendSignal variant
                // (isClientCallback()==true) instead of the parallel unchecked
                // S2cCallbackSignal RuntimeException. ADR-0019's checked-suspension
                // doctrine is preserved fully — every executor lambda already
                // declares `throws SuspendSignal` so the type system pins both
                // paths at compile time.
                if (signal.isClientCallback()) {
                    S2cCallbackEnvelope envelope = (S2cCallbackEnvelope) signal.clientCallback();
                    hookDispatcher.fire(new HookContext(
                            HookPoint.BEFORE_SUSPENSION,
                            run.runId(),
                            run.tenantId(),
                            Map.of("parentNodeKey", signal.parentNodeKey(),
                                    "callbackId", envelope.callbackId())));
                    final SuspendSignal sclient = signal;
                    run = mutateIfNotTerminal(run,
                            r -> r.withSuspension(sclient.parentNodeKey(), Instant.now()));
                    if (RunStateMachine.isTerminal(run.status())) {
                        return null;
                    }
                    Object newPayload;
                    try {
                        newPayload = handleClientCallback(envelope);
                    } catch (RuntimeException s2cFailure) {
                        // Post-review fix (plan C / P0-2): the S2C failure path was
                        // documented in s2c-callback.v1.yaml + Rule 46 to transition
                        // the Run to FAILED, but the prior code let IllegalStateException
                        // from handleClientCallback escape the try entirely, leaving
                        // the Run in SUSPENDED. Finalize the Run here, fire ON_ERROR
                        // carrying the typed reason extracted from the failure message
                        // prefix, then rethrow so the caller still observes the exception.
                        String reason = extractS2cFailureReason(s2cFailure);
                        run = mutateIfNotTerminal(run,
                                r -> r.withStatus(RunStatus.FAILED).withFinishedAt(Instant.now()));
                        hookDispatcher.fire(new HookContext(
                                HookPoint.ON_ERROR,
                                run.runId(),
                                run.tenantId(),
                                Map.of("reason", reason,
                                        "callbackId", envelope.callbackId(),
                                        "exception", s2cFailure.getClass().getName(),
                                        "message", String.valueOf(s2cFailure.getMessage()))));
                        throw s2cFailure;
                    }
                    hookDispatcher.fire(new HookContext(
                            HookPoint.BEFORE_RESUME,
                            run.runId(),
                            run.tenantId(),
                            Map.of("callbackId", envelope.callbackId())));
                    run = runs.findById(run.runId()).orElseThrow();
                    if (RunStateMachine.isTerminal(run.status())) {
                        return null;
                    }
                    run = mutateIfNotTerminal(run,
                            r -> r.withStatus(RunStatus.RUNNING).withUpdatedAt(Instant.now()));
                    if (RunStateMachine.isTerminal(run.status())) {
                        return null;
                    }
                    payload = newPayload;
                } else {
                    // Ordinary child-run suspension path.
                    hookDispatcher.fire(new HookContext(
                            HookPoint.BEFORE_SUSPENSION,
                            run.runId(),
                            run.tenantId(),
                            Map.of("parentNodeKey", signal.parentNodeKey())));
                    final SuspendSignal schild = signal;
                    run = mutateIfNotTerminal(run,
                            r -> r.withSuspension(schild.parentNodeKey(), Instant.now()));
                    if (RunStateMachine.isTerminal(run.status())) {
                        return null;
                    }

                    UUID childRunId = UUID.randomUUID();
                    // Pre-create child run with parentRunId so the nesting chain is queryable.
                    runs.save(new Run(childRunId, run.tenantId(), "orchestrated",
                            RunStatus.PENDING, modeFor(signal.childDef()), Instant.now(),
                            null, null, run.runId(), null, null, null));
                    Object childResult = run(childRunId, run.tenantId(),
                            signal.childDef(), signal.resumePayload());

                    hookDispatcher.fire(new HookContext(
                            HookPoint.BEFORE_RESUME,
                            run.runId(),
                            run.tenantId(),
                            Map.of("childRunId", childRunId)));
                    run = runs.findById(run.runId()).orElseThrow();
                    if (RunStateMachine.isTerminal(run.status())) {
                        return null;
                    }
                    run = mutateIfNotTerminal(run,
                            r -> r.withStatus(RunStatus.RUNNING).withUpdatedAt(Instant.now()));
                    if (RunStateMachine.isTerminal(run.status())) {
                        return null;
                    }
                    payload = childResult;
                }
            } catch (EngineMatchingException eme) {
                // Rule 44: engine_mismatch transitions the Run to FAILED with reason.
                // Phase 7 audit fix (plan D:/.claude/plans/spi-atomic-willow.md L-1):
                // prior code reached the generic RuntimeException branch which only
                // fired ON_ERROR and rethrew, leaving the Run in its prior status.
                // The idempotent guard avoids re-transition when a recursive parent
                // frame catches the same exception (RunStateMachine forbids FAILED -> FAILED).
                // Cancel-vs-fail race guard: a parallel HTTP cancel may already
                // have written CANCELLED — mutateIfNotTerminal preserves CANCELLED.
                run = mutateIfNotTerminal(run,
                        r -> r.withStatus(RunStatus.FAILED).withFinishedAt(Instant.now()));
                hookDispatcher.fire(new HookContext(
                        HookPoint.ON_ERROR,
                        run.runId(),
                        run.tenantId(),
                        Map.of("exception", eme.getClass().getName(),
                                "message", String.valueOf(eme.getMessage()),
                                "reason", "engine_mismatch",
                                "requestedEngineType", String.valueOf(eme.requestedEngineType()),
                                "actualPayloadType", String.valueOf(eme.actualPayloadType()))));
                throw eme;
            } catch (RuntimeException e) {
                hookDispatcher.fire(new HookContext(
                        HookPoint.ON_ERROR,
                        run.runId(),
                        run.tenantId(),
                        Map.of("exception", e.getClass().getName(),
                                "message", String.valueOf(e.getMessage()))));
                throw e;
            }
        }
    }

    private Object dispatch(RunContext ctx, ExecutorDefinition def, Object payload)
            throws SuspendSignal {
        // Rule 43: never pattern-match on ExecutorDefinition subtypes here —
        // EngineRegistry encapsulates the class-to-engineType mapping.
        return engineRegistry.resolveByPayload(def).execute(ctx, def, payload);
    }

    /**
     * Re-read the persisted Run; if non-terminal, apply the {@code mutator} to
     * construct the candidate and save it. If the persisted state is terminal,
     * return the persisted row unchanged without invoking the mutator.
     *
     * <p>Closes the cancel-vs-non-terminal-save race: a stale local snapshot
     * could otherwise produce a candidate (via {@code Run.withStatus(...)}) that
     * validates a legal transition against the local field (e.g.
     * {@code RUNNING → SUSPENDED}), then blind-overwrite a parallel CANCELLED
     * write. Delegating to {@link RunRepository#updateIfNotTerminal} makes the
     * re-read, terminal check, mutate, and write a single atomic step (the
     * in-memory impl backs it with a {@code computeIfPresent} per-key remap),
     * so a cancel landing between the read and the write can no longer be lost
     * — the same atomic primitive {@code RunController.cancel} already uses. A
     * private read-then-save here re-opened that window because the two calls
     * were not atomic relative to a parallel terminal write.
     *
     * <p>The W2 Postgres-backed orchestrator satisfies the same contract with a
     * conditional UPDATE (compare-and-set); the SPI surface is identical.
     *
     * <p>Callers check {@link RunStateMachine#isTerminal(RunStatus)} on the
     * returned Run to decide whether to short-circuit the orchestrator loop.
     */
    private Run mutateIfNotTerminal(Run local, UnaryOperator<Run> mutator) {
        return runs.updateIfNotTerminal(local.runId(), mutator).orElse(local);
    }

    /**
     * Extract a typed S2C failure reason token from an exception raised inside
     * {@link #handleClientCallback}. Used by the executeLoop catch block to
     * label the ON_ERROR hook context with one of the five canonical S2C
     * failure reasons (post-review fix plan C / P0-2).
     *
     * <p>Matching is case-insensitive prefix to survive the
     * {@code SuspendReason.AwaitClientCallback} constant rendering convention.
     */
    static String extractS2cFailureReason(RuntimeException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return "s2c_unknown_failure";
        }
        String lower = msg.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("s2c_transport_unavailable")) {
            return "s2c_transport_unavailable";
        }
        if (lower.startsWith("s2c_transport_failure")) {
            return "s2c_transport_failure";
        }
        if (lower.startsWith("s2c_response_invalid")) {
            return "s2c_response_invalid";
        }
        if (lower.startsWith("s2c_client_error")) {
            return "s2c_client_error";
        }
        if (lower.startsWith("s2c_timeout")) {
            return "s2c_timeout";
        }
        return "s2c_unknown_failure";
    }

    private Run createRun(UUID runId, String tenantId, ExecutorDefinition def) {
        return new Run(runId, tenantId, "orchestrated",
                RunStatus.PENDING, modeFor(def), Instant.now(),
                null, null, null, null, null, null);
    }

    private static RunMode modeFor(ExecutorDefinition def) {
        return switch (def) {
            case ExecutorDefinition.GraphDefinition g -> RunMode.GRAPH;
            case ExecutorDefinition.AgentLoopDefinition a -> RunMode.AGENT_LOOP;
        };
    }

    /**
     * W2.x Phase 3 (ADR-0074): dispatch an S2C callback via the registered
     * {@link S2cCallbackTransport}, await the response, validate, and return
     * the validated payload to be used as the parent's resume payload.
     *
     * <p>Validation invariants (per s2c-callback.v1.yaml + Phase 3a audit matrix):
     * <ul>
     *   <li>Response {@code callbackId} MUST match request {@code callbackId}.</li>
     *   <li>Outcome {@code ERROR}  -- Run transitions to FAILED with
     *       {@link SuspendReason.AwaitClientCallback#S2C_CLIENT_ERROR}.</li>
     *   <li>Outcome {@code TIMEOUT} -- Run transitions to FAILED with
     *       {@link SuspendReason.AwaitClientCallback#S2C_TIMEOUT}.</li>
     *   <li>Validation failure -- Run transitions to FAILED with
     *       {@link SuspendReason.AwaitClientCallback#S2C_RESPONSE_INVALID}.</li>
     *   <li>Transport unavailable -- Run transitions to FAILED with
     *       {@code s2c_transport_unavailable}.</li>
     * </ul>
     *
     * <p>The {@link java.util.concurrent.CompletionStage} returned by the transport is awaited
     * with an upper bound derived from {@link S2cCallbackEnvelope#deadline()}
     * when present, otherwise from {@link #DEFAULT_S2C_CALL_TIMEOUT}. A
     * transport that never completes its future will trip the timeout instead
     * of pinning the orchestrator worker thread indefinitely; the future is
     * cancelled so the transport can release resources.
     */
    private Object handleClientCallback(S2cCallbackEnvelope envelope) {
        S2cCallbackTransport transport = engineRegistry.s2cCallbackTransport();
        if (transport == null) {
            throw new IllegalStateException("s2c_transport_unavailable: SyncOrchestrator received "
                    + "an S2C SuspendSignal but no S2cCallbackTransport is registered "
                    + "(register via EngineRegistry.registerS2cCallbackTransport).");
        }
        long timeoutMs = resolveCallTimeoutMillis(envelope);
        CompletableFuture<S2cCallbackResponse> future =
                transport.dispatch(envelope).toCompletableFuture();
        S2cCallbackResponse response;
        try {
            response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new IllegalStateException(SuspendReason.AwaitClientCallback.S2C_TIMEOUT
                    + ": transport did not complete within " + timeoutMs + " ms (deadline="
                    + envelope.deadline() + ")", te);
        } catch (CompletionException ce) {
            throw new IllegalStateException("s2c_transport_failure: " + ce.getCause(), ce.getCause());
        } catch (java.util.concurrent.ExecutionException ee) {
            throw new IllegalStateException("s2c_transport_failure: " + ee.getCause(), ee.getCause());
        } catch (InterruptedException ie) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("s2c_transport_failure: orchestrator thread interrupted", ie);
        }
        if (response == null) {
            throw new IllegalStateException(SuspendReason.AwaitClientCallback.S2C_RESPONSE_INVALID
                    + ": transport returned null response");
        }
        if (!Objects.equals(response.callbackId(), envelope.callbackId())) {
            throw new IllegalStateException(SuspendReason.AwaitClientCallback.S2C_RESPONSE_INVALID
                    + ": response.callbackId=" + response.callbackId()
                    + " does not match request.callbackId=" + envelope.callbackId());
        }
        return switch (response.outcome()) {
            case OK -> response.responsePayload();
            case ERROR -> throw new IllegalStateException(
                    SuspendReason.AwaitClientCallback.S2C_CLIENT_ERROR
                            + ": " + response.errorCode() + " -- " + response.errorMessage());
            case TIMEOUT -> throw new IllegalStateException(
                    SuspendReason.AwaitClientCallback.S2C_TIMEOUT
                            + ": client did not respond within deadline=" + envelope.deadline());
        };
    }

    /**
     * Compute the orchestrator-side upper bound on a single S2C call.
     *
     * <p>The result is the minimum of (a) the orchestrator's configured
     * {@link #s2cCallTimeout} ceiling and (b) the remaining duration until
     * {@link S2cCallbackEnvelope#deadline()}, clamped to a minimum of 1 ms so
     * callers with a deadline already in the past trip the timeout immediately
     * rather than blocking. When the envelope carries no deadline, we use the
     * configured ceiling unchanged.
     *
     * <p>Both bounds are applied so a misbehaving client cannot defeat the
     * orchestrator's per-call ceiling by shipping a far-future deadline
     * (the prior implementation only applied the envelope deadline, allowing
     * an arbitrarily long pin of the worker thread).
     */
    private long resolveCallTimeoutMillis(S2cCallbackEnvelope envelope) {
        long ceilingMs = s2cCallTimeout.toMillis();
        Instant deadline = envelope.deadline();
        if (deadline == null) {
            return ceilingMs;
        }
        long remaining = Duration.between(Instant.now(), deadline).toMillis();
        return Math.max(1L, Math.min(ceilingMs, remaining));
    }
}

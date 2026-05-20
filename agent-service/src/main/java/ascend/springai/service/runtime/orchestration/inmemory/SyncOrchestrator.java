package ascend.springai.service.runtime.orchestration.inmemory;

import ascend.springai.engine.runtime.EngineRegistry;
import ascend.springai.middleware.HookDispatcher;
import ascend.springai.engine.orchestration.spi.Checkpointer;
import ascend.springai.engine.spi.EngineMatchingException;
import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.middleware.spi.HookContext;
import ascend.springai.middleware.spi.HookPoint;
import ascend.springai.engine.orchestration.spi.Orchestrator;
import ascend.springai.engine.orchestration.spi.RunContext;
import ascend.springai.engine.orchestration.spi.SuspendSignal;
import ascend.springai.service.runtime.posture.AppPostureGate;
import ascend.springai.service.runtime.resilience.spi.SuspendReason;
import ascend.springai.service.runtime.runs.Run;
import ascend.springai.engine.orchestration.spi.RunMode;
import ascend.springai.service.runtime.runs.spi.RunRepository;
import ascend.springai.service.runtime.runs.RunStatus;
import ascend.springai.bus.spi.s2c.S2cCallbackEnvelope;
import ascend.springai.bus.spi.s2c.S2cCallbackResponse;
import ascend.springai.bus.spi.s2c.S2cCallbackTransport;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;

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

    private final RunRepository runs;
    private final Checkpointer checkpointer;
    private final EngineRegistry engineRegistry;
    private final HookDispatcher hookDispatcher;

    /**
     * W2.x Phase 1 (ADR-0072): dispatch goes through {@link EngineRegistry}
     * exclusively. Pattern-matching on {@link ExecutorDefinition} subtypes
     * outside the registry is forbidden by Rule 43.
     *
     * <p>W2.x Phase 2 (ADR-0073): the orchestrator fires three structural
     * hooks ({@link HookPoint#ON_ERROR}, {@link HookPoint#BEFORE_SUSPENSION},
     * {@link HookPoint#BEFORE_RESUME}) via {@link EngineRegistry#hookDispatcher()}.
     * Returned {@link ascend.springai.middleware.spi.HookOutcome} is
     * DISCARDED at every call-site; Run-state consumption (Fail abort,
     * ShortCircuit bypass) is deferred to W2 Telemetry Vertical per Rule 45.b.
     * The discard is intentional — the dispatcher already enforces in-chain
     * fail-fast among middlewares, but the Run lifecycle is unaffected today.
     */
    public SyncOrchestrator(RunRepository runs, Checkpointer checkpointer, EngineRegistry engineRegistry) {
        AppPostureGate.requireDevForInMemoryComponent("SyncOrchestrator");
        this.runs = Objects.requireNonNull(runs);
        this.checkpointer = Objects.requireNonNull(checkpointer);
        this.engineRegistry = Objects.requireNonNull(engineRegistry, "engineRegistry is required");
        this.hookDispatcher = engineRegistry.hookDispatcher();
    }

    @Override
    public Object run(UUID runId, String tenantId, ExecutorDefinition def, Object initialPayload) {
        Run run = runs.findById(runId).orElseGet(() -> createRun(runId, tenantId, def));
        run = runs.save(run.withStatus(RunStatus.RUNNING));
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
                runs.save(run.withStatus(RunStatus.SUCCEEDED).withFinishedAt(Instant.now()));
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
                    run = runs.save(run.withSuspension(signal.parentNodeKey(), Instant.now()));
                    Object newPayload;
                    try {
                        newPayload = handleClientCallback(run, envelope);
                    } catch (RuntimeException s2cFailure) {
                        // Post-review fix (plan C / P0-2): the S2C failure path was
                        // documented in s2c-callback.v1.yaml + Rule 46 to transition
                        // the Run to FAILED, but the prior code let IllegalStateException
                        // from handleClientCallback escape the try entirely, leaving
                        // the Run in SUSPENDED. Finalize the Run here, fire ON_ERROR
                        // carrying the typed reason extracted from the failure message
                        // prefix, then rethrow so the caller still observes the exception.
                        String reason = extractS2cFailureReason(s2cFailure);
                        if (run.status() != RunStatus.FAILED) {
                            run = runs.save(run.withStatus(RunStatus.FAILED).withFinishedAt(Instant.now()));
                        }
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
                    run = runs.save(run.withStatus(RunStatus.RUNNING).withUpdatedAt(Instant.now()));
                    payload = newPayload;
                } else {
                    // Ordinary child-run suspension path.
                    hookDispatcher.fire(new HookContext(
                            HookPoint.BEFORE_SUSPENSION,
                            run.runId(),
                            run.tenantId(),
                            Map.of("parentNodeKey", signal.parentNodeKey())));
                    run = runs.save(run.withSuspension(signal.parentNodeKey(), Instant.now()));

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
                    run = runs.save(run.withStatus(RunStatus.RUNNING).withUpdatedAt(Instant.now()));
                    payload = childResult;
                }
            } catch (EngineMatchingException eme) {
                // Rule 44: engine_mismatch transitions the Run to FAILED with reason.
                // Phase 7 audit fix (plan D:/.claude/plans/spi-atomic-willow.md L-1):
                // prior code reached the generic RuntimeException branch which only
                // fired ON_ERROR and rethrew, leaving the Run in its prior status.
                // The idempotent guard avoids re-transition when a recursive parent
                // frame catches the same exception (RunStateMachine forbids FAILED -> FAILED).
                if (run.status() != RunStatus.FAILED) {
                    run = runs.save(run.withStatus(RunStatus.FAILED).withFinishedAt(Instant.now()));
                }
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
        if (lower.startsWith("s2c_transport_unavailable")) return "s2c_transport_unavailable";
        if (lower.startsWith("s2c_transport_failure")) return "s2c_transport_failure";
        if (lower.startsWith("s2c_response_invalid")) return "s2c_response_invalid";
        if (lower.startsWith("s2c_client_error")) return "s2c_client_error";
        if (lower.startsWith("s2c_timeout")) return "s2c_timeout";
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
     * <p>The {@link CompletionStage} returned by the transport is awaited via
     * {@code toCompletableFuture().join()} -- this is intentional at W2.x:
     * SyncOrchestrator is single-threaded recursive; W2's async orchestrator
     * will use non-blocking composition (no Thread.sleep involved, so Rule 38
     * holds).
     */
    private Object handleClientCallback(Run run, S2cCallbackEnvelope envelope) {
        S2cCallbackTransport transport = engineRegistry.s2cCallbackTransport();
        if (transport == null) {
            throw new IllegalStateException("s2c_transport_unavailable: SyncOrchestrator received "
                    + "an S2C SuspendSignal but no S2cCallbackTransport is registered "
                    + "(register via EngineRegistry.registerS2cCallbackTransport).");
        }
        S2cCallbackResponse response;
        try {
            response = transport.dispatch(envelope).toCompletableFuture().join();
        } catch (CompletionException ce) {
            throw new IllegalStateException("s2c_transport_failure: " + ce.getCause(), ce.getCause());
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
}

package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.stream.Stream;

/**
 * The seam between the engine and a concrete agent framework. An implementation
 * knows how to run one agent and surface its framework-neutral results. The
 * engine runtime owns lifecycle events and route-specific event mapping.
 *
 * <p>Lifecycle contract: the host calls {@link #start()} once before any
 * {@link #execute} and {@link #stop()} once after it stops dispatching new
 * executions; both default to no-ops so stateless handlers need not care.
 * {@link #isHealthy()} is consumed by the host's health surface and its
 * readiness gate — a handler that cannot serve must return {@code false}.
 */
public interface AgentRuntimeHandler {

    /** The agent id this handler serves. */
    String agentId();

    /**
     * Whether this handler can serve executions right now. Consumed by the
     * runtime health indicator and by the serving readiness gate.
     */
    boolean isHealthy();

    /** Run the agent for the given context, emitting framework-specific results. */
    Stream<?> execute(AgentExecutionContext context);

    /** Adapter that maps framework-specific results to engine-neutral results. */
    StreamAdapter resultAdapter();

    /**
     * Open the long-lived resources this handler owns (clients, registrations).
     * Called by the host before it starts dispatching executions; an exception
     * fails runtime startup instead of leaving a serving-but-broken handler.
     */
    default void start() {
    }

    /**
     * Release the resources opened by {@link #start()}. Called by the host
     * after it has stopped dispatching new executions; in-flight executions
     * have either drained or been cancelled by then. A handler only releases
     * what it created — injected collaborators belong to their injector.
     */
    default void stop() {
    }

    /**
     * Cooperatively cancel one in-flight execution. The host also tears down
     * the execution's result stream on cancel, but that teardown only
     * interrupts a lazily produced (incremental) stream: a handler whose
     * {@link #execute} computes the full result synchronously before wrapping
     * it in a stream MUST override this and propagate the framework's native
     * interrupt — otherwise a cancel only flips the task's outward state while
     * the background computation runs to completion. The default no-op is
     * adequate only for lazy-stream handlers.
     */
    default void cancel(String taskId) {
    }
}

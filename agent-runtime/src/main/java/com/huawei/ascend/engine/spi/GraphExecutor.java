package com.huawei.ascend.engine.spi;

import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.bus.spi.engine.RunContext;
import com.huawei.ascend.bus.spi.engine.SuspendSignal;

/**
 * SPI for deterministic graph execution. Pure Java — no Spring imports.
 * Implementations traverse GraphDefinition nodes in edge order, passing payload
 * from node to node. A node may call RunContext.suspendForChild to nest a child run.
 *
 * resumePayload is null on first call; on resume it carries the child run's final result.
 * Implementations use RunContext.checkpointer() to save and load the resume position.
 *
 * <p>W2.x Phase 1: every {@code GraphExecutor} is automatically an
 * {@link ExecutorAdapter} with {@code engineType() = "graph"}. The bridge
 * default method casts the incoming {@link ExecutorDefinition} to
 * {@link ExecutorDefinition.GraphDefinition} or raises
 * {@link EngineMatchingException}. See ADR-0072 + CLAUDE.md Rule 44.
 */
public interface GraphExecutor extends ExecutorAdapter {

    /** Stable engine identifier. MUST match {@code known_engines[].id} in engine-envelope.v1.yaml. */
    String ENGINE_TYPE = "graph";

    @Override
    default String engineType() {
        return ENGINE_TYPE;
    }

    @Override
    default Class<? extends ExecutorDefinition> payloadType() {
        return ExecutorDefinition.GraphDefinition.class;
    }

    @Override
    default Object execute(RunContext ctx, ExecutorDefinition def, Object payload) throws SuspendSignal {
        if (def instanceof ExecutorDefinition.GraphDefinition g) {
            return execute(ctx, g, payload);
        }
        throw new EngineMatchingException(
                ENGINE_TYPE,
                def == null ? "null" : def.getClass().getSimpleName(),
                "GraphExecutor cannot execute payload of type " + (def == null ? "null" : def.getClass().getSimpleName())
                        + " — engine_mismatch");
    }

    /**
     * Execute or resume the graph defined by {@code def} within the given {@code ctx}.
     *
     * @param resumePayload null on first call; child run result on resume
     * @return the final payload produced by the terminal node
     * @throws SuspendSignal if a node requests suspension for a child run
     */
    Object execute(RunContext ctx, ExecutorDefinition.GraphDefinition def, Object resumePayload)
            throws SuspendSignal;
}

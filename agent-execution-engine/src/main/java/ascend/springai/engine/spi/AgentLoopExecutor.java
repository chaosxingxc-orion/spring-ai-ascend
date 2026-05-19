package ascend.springai.engine.spi;

import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.engine.orchestration.spi.RunContext;
import ascend.springai.engine.orchestration.spi.SuspendSignal;

/**
 * SPI for ReAct-style iterative agent-loop execution. Pure Java — no Spring imports.
 * Implementations drive a Reasoner through up to maxIterations reasoning steps.
 * A reasoning step may call RunContext.suspendForChild to nest a child run (graph or loop).
 *
 * resumePayload is null on first call; on resume it carries the child run's final result.
 * The saved iteration index (via RunContext.checkpointer()) is replayed with resumePayload.
 *
 * <p>W2.x Phase 1: every {@code AgentLoopExecutor} is automatically an
 * {@link ExecutorAdapter} with {@code engineType() = "agent-loop"}. The bridge
 * default method casts the incoming {@link ExecutorDefinition} to
 * {@link ExecutorDefinition.AgentLoopDefinition} or raises
 * {@link EngineMatchingException}. See ADR-0072 + CLAUDE.md Rule 44.
 */
public interface AgentLoopExecutor extends ExecutorAdapter {

    /** Stable engine identifier. MUST match {@code known_engines[].id} in engine-envelope.v1.yaml. */
    String ENGINE_TYPE = "agent-loop";

    @Override
    default String engineType() {
        return ENGINE_TYPE;
    }

    @Override
    default Class<? extends ExecutorDefinition> payloadType() {
        return ExecutorDefinition.AgentLoopDefinition.class;
    }

    @Override
    default Object execute(RunContext ctx, ExecutorDefinition def, Object payload) throws SuspendSignal {
        if (def instanceof ExecutorDefinition.AgentLoopDefinition a) {
            return execute(ctx, a, payload);
        }
        throw new EngineMatchingException(
                ENGINE_TYPE,
                def == null ? "null" : def.getClass().getSimpleName(),
                "AgentLoopExecutor cannot execute payload of type " + (def == null ? "null" : def.getClass().getSimpleName())
                        + " — engine_mismatch");
    }

    /**
     * Execute or resume the agent loop defined by {@code def} within the given {@code ctx}.
     *
     * @param resumePayload null on first call; child run result on resume
     * @return the final payload produced by the terminal reasoning step
     * @throws SuspendSignal if a reasoning step requests suspension for a child run
     */
    Object execute(RunContext ctx, ExecutorDefinition.AgentLoopDefinition def, Object resumePayload)
            throws SuspendSignal;
}

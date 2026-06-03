package com.huawei.ascend.engine.exec;

import com.huawei.ascend.engine.spi.AgentLoopExecutor;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.bus.spi.engine.RunContext;
import com.huawei.ascend.bus.spi.engine.SuspendSignal;

import java.nio.charset.StandardCharsets;

/**
 * Reference AgentLoopExecutor: drives the Reasoner through iterations until terminal.
 *
 * Resume protocol:
 *  - On SuspendSignal: saves the CURRENT iteration index to checkpointer under "_loop_resume_iter".
 *  - On resume (resumePayload != null): loads "_loop_resume_iter", replays that iteration
 *    with resumePayload (child result) as the payload — the reasoner must NOT call
 *    suspendForChild again on a resume iteration.
 */
public final class IterativeAgentLoopExecutor implements AgentLoopExecutor {

    private static final String RESUME_ITER_KEY = "_loop_resume_iter";
    // Accumulated payload at the suspension point — combined with child result on resume.
    public static final String RESUME_STATE_KEY = "_loop_resume_state";

    @Override
    public Object execute(RunContext ctx, ExecutorDefinition.AgentLoopDefinition def,
                          Object resumePayload) throws SuspendSignal {
        int startIteration;
        Object payload;

        if (resumePayload != null) {
            startIteration = ctx.checkpointer().load(ctx.runId(), RESUME_ITER_KEY)
                    .map(b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8)))
                    .orElse(0);
            // Reconstruct payload: combine pre-suspension accumulated state with child result.
            // W0 constraint: when accumulated state exists, resumePayload MUST be String;
            // Object.toString() on a non-String produces an unrecoverable byte stream. ADR-0022 (W2).
            String savedState = ctx.checkpointer().load(ctx.runId(), RESUME_STATE_KEY)
                    .map(b -> new String(b, StandardCharsets.UTF_8))
                    .orElse(null);
            if (savedState != null) {
                if (!(resumePayload instanceof String resumeStr)) {
                    throw new IllegalStateException(
                            "IterativeAgentLoopExecutor: resume cursor requires String resumePayload " +
                            "when accumulated state exists; received " +
                            resumePayload.getClass().getName() +
                            ". Typed payload crossing suspend boundaries is deferred to W2 (ADR-0022).");
                }
                payload = savedState + resumeStr;
            } else {
                payload = resumePayload;
            }
        } else {
            startIteration = 0;
            payload = def.initialContext();
        }

        for (int i = startIteration; i < def.maxIterations(); i++) {
            try {
                ExecutorDefinition.ReasoningResult result = def.reasoner().reason(ctx, payload, i);
                if (result.terminal()) {
                    return result.payload();
                }
                payload = result.payload();
            } catch (SuspendSignal signal) {
                ctx.checkpointer().save(ctx.runId(), RESUME_ITER_KEY,
                        String.valueOf(i).getBytes(StandardCharsets.UTF_8));
                // Only save accumulated state if prior iterations produced output (i > 0).
                // At i == 0 the payload is the initial context, not accumulated work.
                if (i > 0 && payload != null) {
                    if (!(payload instanceof String payloadStr)) {
                        throw new IllegalStateException(
                                "IterativeAgentLoopExecutor: resume cursor requires String payload at W0; " +
                                "received " + payload.getClass().getName() +
                                ". Typed payload is deferred to W2 (ADR-0022).");
                    }
                    ctx.checkpointer().save(ctx.runId(), RESUME_STATE_KEY,
                            payloadStr.getBytes(StandardCharsets.UTF_8));
                }
                throw signal;
            }
        }
        throw new IllegalStateException("Agent loop exceeded maxIterations=" + def.maxIterations()
                + " without reaching a terminal step");
    }
}

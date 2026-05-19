package ascend.springai.service.runtime.orchestration.inmemory;

import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.engine.spi.GraphExecutor;
import ascend.springai.engine.orchestration.spi.RunContext;
import ascend.springai.engine.orchestration.spi.SuspendSignal;

import java.nio.charset.StandardCharsets;

/**
 * Reference GraphExecutor: traverses nodes in sequential edge order.
 * Terminal = node with no outgoing edge in the edges map.
 *
 * Resume protocol:
 *  - On SuspendSignal: saves the NEXT node key to checkpointer under "_graph_next_node".
 *  - On resume (resumePayload != null): loads "_graph_next_node", starts there.
 */
public final class SequentialGraphExecutor implements GraphExecutor {

    private static final String RESUME_KEY = "_graph_next_node";

    @Override
    public Object execute(RunContext ctx, ExecutorDefinition.GraphDefinition def,
                          Object resumePayload) throws SuspendSignal {
        String current;
        Object payload;

        if (resumePayload != null) {
            current = ctx.checkpointer().load(ctx.runId(), RESUME_KEY)
                    .map(b -> new String(b, StandardCharsets.UTF_8))
                    .orElse(def.startNode());
            payload = resumePayload;
        } else {
            current = def.startNode();
            payload = null;
        }

        while (current != null) {
            ExecutorDefinition.NodeFunction node = def.nodes().get(current);
            if (node == null) throw new IllegalStateException("No node registered for key: " + current);
            try {
                payload = node.apply(ctx, payload);
                current = def.edges().get(current);
            } catch (SuspendSignal signal) {
                String nextNode = def.edges().get(current);
                if (nextNode != null) {
                    ctx.checkpointer().save(ctx.runId(), RESUME_KEY,
                            nextNode.getBytes(StandardCharsets.UTF_8));
                }
                throw signal;
            }
        }
        return payload;
    }
}

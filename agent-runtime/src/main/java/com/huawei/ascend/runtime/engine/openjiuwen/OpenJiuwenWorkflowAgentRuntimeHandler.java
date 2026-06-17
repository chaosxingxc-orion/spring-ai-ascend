package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Abstract base for hosting an OpenJiuwen {@link Workflow} inside agent-runtime.
 *
 * <p>Subclasses implement {@link #createOpenJiuwenWorkflow(AgentExecutionContext)} to
 * build the Workflow DAG. The base class owns the invoke loop, interrupt detection,
 * and resume orchestration — reusing the same session-id stability and Checkpointer
 * guarantees that the ReActAgent handler relies on.
 *
 * <h3>Execution model</h3>
 * <pre>
 *   workflow.invoke(inputs, session, null)
 *     → COMPLETED      → AgentExecutionResult.completed(finalOutput)
 *     → INPUT_REQUIRED → AgentExecutionResult.interrupted(userInputInterrupt)
 *     → ERROR          → AgentExecutionResult.failed(errorCode, message)
 * </pre>
 *
 * @see OpenJiuwenAgentRuntimeHandler for the ReActAgent counterpart
 */
public abstract class OpenJiuwenWorkflowAgentRuntimeHandler
        extends AbstractAgentRuntimeHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OpenJiuwenWorkflowAgentRuntimeHandler.class);

    private final OpenJiuwenWorkflowStreamAdapter resultMapper =
            new OpenJiuwenWorkflowStreamAdapter();

    /** Holds resume context between an interrupt and the next resume call. */
    static class WorkflowResumeContext {
        final String sessionId;
        final String interruptedNodeId;
        WorkflowResumeContext(String sessionId, String interruptedNodeId) {
            this.sessionId = sessionId;
            this.interruptedNodeId = interruptedNodeId;
        }
    }

    /** Per-agentStateKey resume state. Key = context.agentStateKey(). */
    private final Map<String, WorkflowResumeContext> pendingResumes = new ConcurrentHashMap<>();

    protected OpenJiuwenWorkflowAgentRuntimeHandler(String agentId) {
        super(agentId);
    }

    // ── SPI for subclasses ──────────────────────────────────────────

    /**
     * Build the Workflow DAG for this execution.
     * Called once per {@link #execute(AgentExecutionContext)} invocation.
     *
     * @param context execution context carrying tenant/user/session identity
     * @return a fully wired Workflow (start/end nodes, components, connections)
     */
    protected abstract Workflow createOpenJiuwenWorkflow(AgentExecutionContext context);

    // ── StreamAdapter ────────────────────────────────────────────────

    @Override
    public StreamAdapter resultAdapter() {
        return rawResults -> rawResults.map(this::mapRawResult);
    }

    // ── Core execution ───────────────────────────────────────────────

    @Override
    protected final Stream<?> doExecute(AgentExecutionContext context,
                                         TrajectoryEmitter trajectory) {
        String sessionId;
        Object nextInputs;

        WorkflowResumeContext resume = pendingResumes.remove(context.getAgentStateKey());
        if (resume != null) {
            // ── Resume path ──────────────────────────────────────────
            sessionId = resume.sessionId;
            String nodeId = resume.interruptedNodeId;
            String userInput = context.lastUserText();

            InteractiveInput resumeInput = new InteractiveInput();
            resumeInput.update(nodeId, Map.of("answer", userInput));

            nextInputs = resumeInput;
            LOGGER.info("Workflow resume sessionId={} nodeId={}", sessionId, nodeId);
        } else {
            // ── Fresh execution ──────────────────────────────────────
            sessionId = context.getAgentStateKey() + "-" + UUID.randomUUID();
            nextInputs = Map.of("query", context.lastUserText());
            LOGGER.info("Workflow start sessionId={}", sessionId);
        }

        Workflow workflow = createOpenJiuwenWorkflow(context);
        WorkflowSessionApi session = new WorkflowSessionApi(null, sessionId, Map.of());

        // Invoke loop: may yield COMPLETED or INPUT_REQUIRED
        while (true) {
            WorkflowOutput output = workflow.invoke(nextInputs, session, null);

            if (output.getState() == WorkflowExecutionState.COMPLETED) {
                LOGGER.info("Workflow completed sessionId={}", sessionId);
                return Stream.of(output);
            }

            if (output.getState() == WorkflowExecutionState.INPUT_REQUIRED) {
                String nodeId = OpenJiuwenWorkflowStreamAdapter.extractNodeId(output);
                pendingResumes.put(context.getAgentStateKey(),
                        new WorkflowResumeContext(sessionId, nodeId));
                LOGGER.info("Workflow interrupted sessionId={} nodeId={}", sessionId, nodeId);
                return Stream.of(output);
            }

            // ERROR or other
            LOGGER.error("Workflow error sessionId={} result={}", sessionId, output.getResult());
            return Stream.of(output);
        }
    }

    // ── Result mapping ────────────────────────────────────────────────

    private AgentExecutionResult mapRawResult(Object rawResult) {
        if (rawResult instanceof AgentExecutionResult result) {
            return result;
        }
        if (rawResult instanceof WorkflowOutput output) {
            return resultMapper.map(output);
        }
        if (rawResult == null) {
            return AgentExecutionResult.failed("WORKFLOW_ERROR", "workflow returned null");
        }
        return AgentExecutionResult.failed("WORKFLOW_ERROR",
                "unexpected result type: " + rawResult.getClass().getName());
    }
}

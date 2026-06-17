package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowChunk;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
        Object inputs;

        WorkflowResumeContext resume = pendingResumes.remove(context.getAgentStateKey());
        if (resume != null) {
            // ── Resume path — use invoke() for precise InteractiveInput control ──
            sessionId = resume.sessionId;
            String nodeId = resume.interruptedNodeId;
            String userInput = context.lastUserText();
            InteractiveInput resumeInput = new InteractiveInput();
            resumeInput.update(nodeId, Map.of("answer", userInput));
            inputs = resumeInput;
            LOGGER.info("Workflow resume sessionId={} nodeId={}", sessionId, nodeId);
        } else {
            sessionId = context.getAgentStateKey() + "-" + UUID.randomUUID();
            inputs = Map.of("query", context.lastUserText());
            LOGGER.info("Workflow start sessionId={}", sessionId);
        }

        Workflow workflow = createOpenJiuwenWorkflow(context);
        WorkflowSessionApi session = new WorkflowSessionApi(null, sessionId, Map.of());
        Iterator<WorkflowChunk> chunkStream = workflow.stream(inputs, session, null);

        // Adapt the async WorkflowChunk iterator into a Java Stream.
        // Key insight: GraphInterrupt is thrown from upstream.hasNext()/next()
        // AFTER the interaction chunk has been emitted. We emit each chunk as
        // OUTPUT as it arrives; when GraphInterrupt surfaces we extract the
        // interrupt metadata and emit an INPUT_REQUIRED terminal.
        Iterator<Object> adapted = new Iterator<>() {
            private Object nextItem;
            private boolean done;
            private OutputSchema pendingInteraction;
            private final StringBuilder accumulatedText = new StringBuilder();

            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (nextItem != null) {
                    return true;
                }
                if (pendingInteraction != null) {
                    // We've already seen the interaction chunk; the next upstream
                    // call will throw GraphInterrupt. Convert to terminal.
                    InteractionOutput io = (InteractionOutput) pendingInteraction.getPayload();
                    pendingResumes.put(context.getAgentStateKey(),
                            new WorkflowResumeContext(sessionId, io.getId()));
                    LOGGER.info("Workflow interrupted (stream) sessionId={} nodeId={}",
                            sessionId, io.getId());
                    nextItem = new WorkflowOutput(
                            List.of(pendingInteraction),
                            WorkflowExecutionState.INPUT_REQUIRED);
                    pendingInteraction = null;
                    done = true;
                    return true;
                }
                try {
                    if (!upstreamHasNextSafely()) {
                        // Stream drained normally — use accumulated text as result
                        LOGGER.info("Workflow completed (stream) sessionId={} textLen={}",
                                sessionId, accumulatedText.length());
                        nextItem = new WorkflowOutput(
                                accumulatedText.toString(),
                                WorkflowExecutionState.COMPLETED);
                        done = true;
                        return true;
                    }
                    WorkflowChunk chunk = upstreamNextSafely();
                    // Accumulate text from each chunk for the final COMPLETED result
                    accumulateChunkText(chunk);
                    if (isInteractionChunk(chunk)) {
                        // Hold the interaction chunk; the next read will throw GraphInterrupt
                        pendingInteraction = (OutputSchema) chunk;
                        // But first emit this chunk as OUTPUT so user sees the prompt
                        nextItem = chunk;
                        return true;
                    }
                    nextItem = chunk;
                    return true;
                } catch (RuntimeException e) {
                    GraphInterrupt gi = unwrapGraphInterrupt(e);
                    if (gi != null && pendingInteraction != null) {
                        // GraphInterrupt after interaction: convert to INPUT_REQUIRED
                        InteractionOutput io =
                                (InteractionOutput) pendingInteraction.getPayload();
                        pendingResumes.put(context.getAgentStateKey(),
                                new WorkflowResumeContext(sessionId, io.getId()));
                        LOGGER.info("Workflow interrupted (stream) sessionId={} nodeId={}",
                                sessionId, io.getId());
                        nextItem = new WorkflowOutput(
                                List.of(pendingInteraction),
                                WorkflowExecutionState.INPUT_REQUIRED);
                        pendingInteraction = null;
                        done = true;
                        return true;
                    }
                    String errMsg = e.getMessage() != null ? e.getMessage()
                            : e.getClass().getSimpleName();
                    LOGGER.error("Workflow stream error sessionId={} msg={}", sessionId, errMsg, e);
                    nextItem = new WorkflowOutput(errMsg, WorkflowExecutionState.ERROR);
                    done = true;
                    return true;
                }
            }

            private void accumulateChunkText(WorkflowChunk chunk) {
                if (chunk instanceof OutputSchema schema) {
                    Object payload = schema.getPayload();
                    if (payload instanceof String s) {
                        accumulatedText.append(s);
                    } else if (payload instanceof Map<?, ?> m) {
                        Object out = m.get("output");
                        if (out instanceof Map<?, ?> outputMap) {
                            outputMap.values().forEach(v -> {
                                if (v instanceof String s) accumulatedText.append(s);
                            });
                        }
                    }
                }
            }

            @Override
            public Object next() {
                if (nextItem == null && !hasNext()) {
                    throw new NoSuchElementException();
                }
                Object n = nextItem;
                nextItem = null;
                return n;
            }

            private boolean upstreamHasNextSafely() {
                try {
                    return chunkStream.hasNext();
                } catch (RuntimeException e) {
                    GraphInterrupt gi = unwrapGraphInterrupt(e);
                    if (gi != null) {
                        throw e; // let outer catch handle it
                    }
                    throw e;
                }
            }

            private WorkflowChunk upstreamNextSafely() {
                return chunkStream.next();
            }
        };

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(adapted, 0), false);
    }

    private static boolean isInteractionChunk(Object chunk) {
        if (chunk instanceof OutputSchema schema) {
            String type = schema.getType();
            return Constant.INTERACTION.equals(type) || "interaction".equals(type);
        }
        return false;
    }

    private static GraphInterrupt unwrapGraphInterrupt(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof GraphInterrupt gi) {
                return gi;
            }
        }
        return null;
    }

    // ── Result mapping ────────────────────────────────────────────────

    private AgentExecutionResult mapRawResult(Object rawResult) {
        if (rawResult instanceof AgentExecutionResult result) {
            return result;
        }
        if (rawResult instanceof WorkflowOutput output) {
            return resultMapper.map(output);
        }
        // Streaming chunks from workflow.stream()
        if (rawResult instanceof OutputSchema schema) {
            Object payload = schema.getPayload();
            String text = payload instanceof String s ? s
                    : payload != null ? payload.toString() : "";
            return AgentExecutionResult.output(text);
        }
        if (rawResult instanceof WorkflowChunk chunk) {
            return AgentExecutionResult.output(chunk.toString());
        }
        if (rawResult == null) {
            return AgentExecutionResult.failed("WORKFLOW_ERROR", "workflow returned null");
        }
        return AgentExecutionResult.failed("WORKFLOW_ERROR",
                "unexpected result type: " + rawResult.getClass().getName());
    }
}

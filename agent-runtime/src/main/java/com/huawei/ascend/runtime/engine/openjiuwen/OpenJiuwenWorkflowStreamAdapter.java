package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Maps OpenJiuwen {@link WorkflowOutput} → framework-neutral
 * {@link AgentExecutionResult}.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>{@code COMPLETED}  → {@code completed(finalResult)}</li>
 *   <li>{@code INPUT_REQUIRED} → {@code interrupted(UserInputInterrupt)}</li>
 *   <li>{@code ERROR} → {@code failed(errorCode, message)}</li>
 * </ul>
 */
public class OpenJiuwenWorkflowStreamAdapter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OpenJiuwenWorkflowStreamAdapter.class);

    static final String ERROR_CODE = "OPENJIUWEN_WORKFLOW_ERROR";

    /**
     * Map a single WorkflowOutput to an AgentExecutionResult.
     */
    public AgentExecutionResult map(WorkflowOutput output) {
        if (output == null) {
            return AgentExecutionResult.failed(ERROR_CODE, "workflow returned null output");
        }

        if (output.getState() == WorkflowExecutionState.COMPLETED) {
            String text = extractResultText(output);
            LOGGER.info("Workflow COMPLETED textLength={}", text != null ? text.length() : 0);
            return AgentExecutionResult.completed(text != null ? text : "");
        }

        if (output.getState() == WorkflowExecutionState.INPUT_REQUIRED) {
            String nodeId = extractNodeId(output);
            String prompt = extractPrompt(output);
            LOGGER.info("Workflow INPUT_REQUIRED nodeId={} prompt={}", nodeId, prompt);
            return AgentExecutionResult.interrupted(prompt != null ? prompt : "");
        }

        // ERROR or unexpected
        String detail = output.getResult() != null ? output.getResult().toString() : "unknown";
        return AgentExecutionResult.failed(ERROR_CODE, detail);
    }

    // ── Extraction helpers ─────────────────────────────────────────

    /**
     * Extract the node ID from the interaction output inside a
     * {@code INPUT_REQUIRED} result.
     */
    static String extractNodeId(WorkflowOutput output) {
        InteractionOutput interaction = findInteraction(output);
        return interaction != null ? interaction.getId() : "unknown";
    }

    /**
     * Extract the human-readable prompt from the interaction output.
     */
    static String extractPrompt(WorkflowOutput output) {
        InteractionOutput interaction = findInteraction(output);
        if (interaction != null && interaction.getValue() != null) {
            return interaction.getValue().toString();
        }
        return "";
    }

    /**
     * Extract the final text from a {@code COMPLETED} result.
     */
    static String extractResultText(WorkflowOutput output) {
        Object result = output.getResult();
        if (result instanceof List<?> chunks) {
            for (Object chunk : chunks) {
                if (chunk instanceof OutputSchema schema) {
                    Object payload = schema.getPayload();
                    // End node wraps result as {output: {key: value}} or {response: ...}
                    if (payload instanceof Map<?, ?> m) {
                        Object resp = m.get("response");
                        if (resp instanceof String s) {
                            return s;
                        }
                        Object out = m.get("output");
                        if (out instanceof Map<?, ?> outputMap) {
                            Object text = outputMap.values().stream().findFirst().orElse(null);
                            if (text instanceof String s) {
                                return s;
                            }
                        }
                    }
                    if (payload instanceof String s) {
                        return s;
                    }
                }
            }
        }
        return result != null ? result.toString() : "";
    }

    // ── Internal ────────────────────────────────────────────────────

    private static InteractionOutput findInteraction(WorkflowOutput output) {
        Object result = output.getResult();
        if (result instanceof List<?> chunks) {
            for (Object chunk : chunks) {
                if (chunk instanceof OutputSchema schema
                        && ("interaction".equals(schema.getType())
                            || Constant.INTERACTION.equals(schema.getType()))) {
                    if (schema.getPayload() instanceof InteractionOutput interaction) {
                        return interaction;
                    }
                }
            }
        }
        return null;
    }
}

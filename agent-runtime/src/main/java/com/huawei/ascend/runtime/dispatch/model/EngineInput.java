package com.huawei.ascend.service.engine.model;

import com.huawei.ascend.service.schema.Message;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Input for Agent execution.
 *
 * <p>Carries Agent execution input. Prioritizes {@code messages} for
 * conversational input. {@code variables} is used for workflow variables
 * or structured task parameters. Messages use the canonical {@link Message}
 * schema shared across the service.
 *
 * @param inputType input type: USER_MESSAGE, RESUME_SIGNAL, or AGENT_CALL_RESULT.
 * @param messages  list of messages (conversational input).
 * @param variables workflow variables or structured task parameters.
 */
public record EngineInput(
        String inputType,
        List<Message> messages,
        Map<String, Object> variables) {

    public EngineInput {
        Objects.requireNonNull(inputType, "inputType");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(variables, "variables");
        messages = List.copyOf(messages);
        variables = Map.copyOf(variables);
    }
}

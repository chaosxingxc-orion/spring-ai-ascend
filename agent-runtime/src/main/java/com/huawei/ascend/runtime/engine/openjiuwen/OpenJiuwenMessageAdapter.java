package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.a2a.Messages;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import org.a2aproject.sdk.spec.Message;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenJiuwenMessageAdapter {

    public Object toOpenJiuwenInput(AgentExecutionContext context) {
        if (AgentExecutionContext.INPUT_TYPE_REMOTE_RESUME.equals(context.getInputType())) {
            return remoteResumeInput(context);
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", lastUserText(context));
        input.put("conversation_id", context.getAgentStateKey());
        return input;
    }

    private Map<String, Object> remoteResumeInput(AgentExecutionContext context) {
        Object toolCallId = context.getVariables().get(AgentExecutionContext.REMOTE_TOOL_CALL_ID_VARIABLE);
        Object toolResult = context.getVariables().get(AgentExecutionContext.REMOTE_TOOL_RESULT_VARIABLE);
        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update(String.valueOf(toolCallId), toolResult == null ? "" : String.valueOf(toolResult));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", interactiveInput);
        input.put("conversation_id", context.getAgentStateKey());
        return input;
    }

    private String lastUserText(AgentExecutionContext context) {
        List<Message> messages = context.getMessages();
        if (messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message != null && message.role() == Message.Role.ROLE_USER) {
                return messageText(message);
            }
        }
        // No user turn at all — fall back to the newest message regardless of role
        // so the agent still receives a query rather than an empty string.
        return messageText(messages.get(messages.size() - 1));
    }

    /**
     * Extracts concatenated text from A2A SDK Message parts. Replaces the former
     * {@code common.Message.text()} method that iterated Content parts.
     */
    public static String messageText(Message msg) {
        return Messages.text(msg);
    }
}

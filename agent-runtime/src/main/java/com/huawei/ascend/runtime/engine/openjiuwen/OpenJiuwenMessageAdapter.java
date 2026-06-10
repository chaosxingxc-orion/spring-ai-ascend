package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenJiuwenMessageAdapter {

    public Object toOpenJiuwenInput(AgentExecutionContext context) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", lastUserText(context));
        input.put("conversation_id", context.getAgentStateKey());
        return input;
    }

    private String lastUserText(AgentExecutionContext context) {
        List<Message> messages = context.getMessages().isEmpty() ? null : context.getMessages();
        if (messages == null || messages.isEmpty()) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message != null && message.role() == Message.Role.ROLE_USER) {
                return messageText(message);
            }
        }
        return messageText(messages.get(messages.size() - 1));
    }

    /**
     * Extracts concatenated text from A2A SDK Message parts. Replaces the former
     * {@code common.Message.text()} method that iterated Content parts.
     */
    public static String messageText(Message msg) {
        if (msg == null || msg.parts() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (var part : msg.parts()) {
            if (part instanceof TextPart tp) sb.append(tp.text());
        }
        return sb.toString();
    }
}

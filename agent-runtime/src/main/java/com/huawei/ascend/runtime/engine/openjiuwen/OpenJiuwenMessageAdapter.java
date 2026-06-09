package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.common.Role;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts an {@link AgentExecutionContext} into the input map openJiuwen's
 * {@code Runner.runAgent} expects. First version handles text messages only,
 * keying the last user message as {@code query} and the runtime state key as
 * {@code conversation_id}. This lets openJiuwen's native checkpointer restore
 * the same conversation across turns.
 */
public class OpenJiuwenMessageAdapter {

    public Object toOpenJiuwenInput(AgentExecutionContext context) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", lastUserText(context));
        input.put("conversation_id", context.getAgentStateKey());
        return input;
    }

    private String lastUserText(AgentExecutionContext context) {
        List<Message> messages = context.getInput() == null ? null : context.getInput().messages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message != null && message.role() == Role.USER) {
                return message.text();
            }
        }
        return messages.get(messages.size() - 1).text();
    }
}

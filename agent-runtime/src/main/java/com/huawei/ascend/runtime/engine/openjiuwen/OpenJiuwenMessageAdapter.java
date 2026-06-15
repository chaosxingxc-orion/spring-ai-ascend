package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenJiuwenMessageAdapter {

    public Object toOpenJiuwenInput(AgentExecutionContext context) {
        if (AgentExecutionContext.INPUT_TYPE_REMOTE_RESUME.equals(context.getInputType())) {
            return remoteResumeInput(context);
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", context.lastUserText());
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
}

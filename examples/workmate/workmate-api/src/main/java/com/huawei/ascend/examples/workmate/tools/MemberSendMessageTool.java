package com.huawei.ascend.examples.workmate.tools;

import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OpenJiuwen tool card + delegate to {@link MemberWorkerPool#invokeSendMessageTool}. */
public final class MemberSendMessageTool extends LocalFunction {

    MemberSendMessageTool(String toolId, String memberId, MemberWorkerPool pool) {
        super(buildCard(toolId), inputs -> {
            if (pool == null) {
                return Map.of("success", false, "error", "'send_message' is unavailable (no active team runtime)");
            }
            return pool.invokeSendMessageTool(memberId, inputs);
        });
    }

    private static ToolCard buildCard(String toolId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("to", stringProp("Recipient (use team-lead for handback to the team lead)"));
        properties.put("recipient", stringProp("Alias of to"));
        properties.put("content", stringProp("Full handback body (summary + sources, etc.)"));
        properties.put("message", stringProp("Alias of content"));
        properties.put("summary", stringProp("Short card title for the handback"));
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", properties);
        inputParams.put("required", List.of("content"));
        return ToolCard.builder()
                .id(toolId)
                .name("send_message")
                .description(
                        "Send structured output to the team lead or another teammate. "
                                + "When finishing a task, call with to=team-lead and the full deliverable in content.")
                .inputParams(inputParams)
                .build();
    }

    private static Map<String, Object> stringProp(String description) {
        return Map.of("type", "string", "description", description);
    }
}

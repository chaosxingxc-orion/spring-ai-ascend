package com.huawei.ascend.examples.workmate.tools;

import com.huawei.ascend.examples.workmate.question.QuestionGate;
import com.huawei.ascend.examples.workmate.question.QuestionGate.AnswerResult;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AskUserQuestionToolFactory {

    public AskUserQuestionToolSet register(UUID sessionId, String agentTag, ToolExecutionContext executionContext) {
        String toolId = WorkmateToolIds.askUserQuestion(sessionId);
        Tool tool = new AskUserQuestionTool(toolId, executionContext);
        safeRemove(toolId, agentTag);
        Runner.resourceMgr().addTool(tool, agentTag);
        return new AskUserQuestionToolSet(tool, agentTag, sessionId);
    }

    public void unregister(AskUserQuestionToolSet toolSet) {
        safeRemove(toolSet.tool().getCard().getId(), toolSet.agentTag());
    }

    private static void safeRemove(String toolId, String agentTag) {
        try {
            Runner.resourceMgr().removeTool(toolId, agentTag, TagMatchStrategy.ALL, true);
        } catch (RuntimeException ignored) {
            // first registration
        }
    }

    public record AskUserQuestionToolSet(Tool tool, String agentTag, UUID sessionId) {
    }

    private static final class AskUserQuestionTool extends LocalFunction {

        AskUserQuestionTool(String toolId, ToolExecutionContext ctx) {
            super(buildCard(toolId), inputs -> ask(ctx, toolId, inputs));
        }

        private static ToolCard buildCard(String toolId) {
            Map<String, Object> optionItem = Map.of("type", "string");
            Map<String, Object> properties = new HashMap<>();
            properties.put("question", stringProp("Question to ask the user"));
            properties.put("options", Map.of(
                    "type", "array",
                    "items", optionItem,
                    "description", "Optional choices; omit for free-text only"));
            properties.put("allowFreeText", Map.of(
                    "type", "boolean",
                    "description", "Allow additional free-text answer (default true)"));
            properties.put("multiSelect", Map.of(
                    "type", "boolean",
                    "description", "Allow multiple option selections (default false)"));
            Map<String, Object> inputParams = new HashMap<>();
            inputParams.put("type", "object");
            inputParams.put("properties", properties);
            inputParams.put("required", List.of("question"));
            return ToolCard.builder()
                    .id(toolId)
                    .name(toolId)
                    .description("Ask the user a clarifying question and wait for their answer before continuing.")
                    .inputParams(inputParams)
                    .build();
        }

        private static Map<String, Object> ask(ToolExecutionContext ctx, String toolName, Map<String, Object> inputs) {
            QuestionGate gate = ctx.questionGate();
            if (gate == null) {
                return failure("Question gate unavailable");
            }
            String question = asString(inputs.get("question"));
            if (question.isBlank()) {
                return failure("question must not be blank");
            }
            List<String> options = readOptions(inputs.get("options"));
            boolean allowFreeText = readBoolean(inputs.get("allowFreeText"), true);
            boolean multiSelect = readBoolean(inputs.get("multiSelect"), false);
            if (options.isEmpty() && !allowFreeText) {
                return failure("Provide options or allow free text");
            }

            AnswerResult answer = gate.await(
                    ctx.sessionId(), ctx.taskId(), toolName, question, options, allowFreeText, multiSelect);
            if (answer.selections().isEmpty() && (answer.text() == null || answer.text().isBlank()) && !answer.skipped()) {
                return failure("Timed out waiting for user answer");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("skipped", answer.skipped());
            if (!answer.skipped()) {
                data.put("selections", answer.selections());
                if (answer.text() != null && !answer.text().isBlank()) {
                    data.put("text", answer.text());
                }
            }
            return success(data);
        }

        private static List<String> readOptions(Object value) {
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            List<String> options = new ArrayList<>();
            for (Object entry : list) {
                if (entry == null) {
                    continue;
                }
                String text = String.valueOf(entry).trim();
                if (!text.isEmpty()) {
                    options.add(text);
                }
            }
            return options;
        }

        private static boolean readBoolean(Object value, boolean defaultValue) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text) {
                return Boolean.parseBoolean(text);
            }
            return defaultValue;
        }

        private static Map<String, Object> stringProp(String description) {
            return Map.of("type", "string", "description", description);
        }

        private static String asString(Object value) {
            return value == null ? "" : String.valueOf(value).trim();
        }

        private static Map<String, Object> success(Map<String, Object> data) {
            return Map.of("success", true, "data", data);
        }

        private static Map<String, Object> failure(String message) {
            return Map.of("success", false, "message", message);
        }
    }
}

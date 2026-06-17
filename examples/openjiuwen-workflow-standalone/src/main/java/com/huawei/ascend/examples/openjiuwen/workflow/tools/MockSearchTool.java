package com.huawei.ascend.examples.openjiuwen.workflow.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.util.Map;

/**
 * Mock search tool that returns canned results for demonstration purposes.
 * No real API call — replace with a real search implementation for production use.
 */
public final class MockSearchTool {

    public static final String TOOL_ID = "mock_search";

    private MockSearchTool() {}

    /**
     * Create a mock search tool that returns canned facts about the input topic.
     */
    public static Tool create() {
        ToolCard card = ToolCard.builder()
                .id(TOOL_ID)
                .name("mock_search")
                .description("Search for related information about a given topic (mock implementation)")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "The search query")
                        ),
                        "required", java.util.List.of("query")
                ))
                .build();

        return new LocalFunction(card, inputs -> {
            String query = (String) inputs.getOrDefault("query", "");
            return "关于「" + query + "」的搜索结果（模拟）：\n"
                    + "1. 该话题在近期被广泛讨论，相关文章数量持续增长。\n"
                    + "2. 专家指出该领域正处于快速发展阶段。\n"
                    + "3. 多个权威来源确认了相关信息的基本准确性。";
        });
    }
}

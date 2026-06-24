/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector;

import com.huawei.ascend.examples.travel.taskcollector.mcp.McpToolRegistry;
import com.huawei.ascend.examples.travel.taskcollector.prompt.SystemPromptBuilder;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TaskCollectorAgent implements AutoCloseable {

    private final LlmConfig llm;
    private final int maxIterations;
    private final String userId;
    private final String defaultCity;
    private final List<Tool> tools;

    public TaskCollectorAgent(LlmConfig llm) {
        this(llm, TaskCollectorAgentConstants.DEFAULT_MAX_ITERATIONS, "zhang3", "深圳");
    }

    public TaskCollectorAgent(LlmConfig llm, int maxIterations, String userId, String defaultCity) {
        this.llm = Objects.requireNonNull(llm, "llm");
        this.maxIterations = maxIterations;
        this.userId = Objects.requireNonNull(userId, "userId");
        this.defaultCity = Objects.requireNonNull(defaultCity, "defaultCity");
        this.tools = McpToolRegistry.mockTools();
        for (Tool tool : tools) {
            registerTool(tool);
        }
    }

    public BaseAgent newBaseAgent() {
        AgentCard card = AgentCard.builder()
                .id(TaskCollectorAgentConstants.AGENT_ID)
                .name(TaskCollectorAgentConstants.AGENT_ID)
                .description("差旅事项收集子智能体")
                .build();
        ReActAgent agent = new ReActAgent(card);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(maxIterations)
                .build()
                .configureModelClient(
                        llm.provider(),
                        llm.apiKey(),
                        llm.apiBase(),
                        llm.modelName(),
                        llm.sslVerify());
        agent.configure(config);
        agent.addPromptBuilderSection(
                "task_collector_business_rules",
                SystemPromptBuilder.build(userId, defaultCity),
                20);
        for (Tool tool : tools) {
            agent.getAbilityManager().add(tool.getCard());
        }
        return agent;
    }

    public String chat(String userMessage) {
        Objects.requireNonNull(userMessage, "userMessage");
        String conversationId = TaskCollectorAgentConstants.AGENT_ID + "-" + UUID.randomUUID();
        BaseAgent agent = newBaseAgent();
        try {
            Object raw = Runner.runAgent(
                    agent,
                    Map.of("query", userMessage, "conversation_id", conversationId),
                    null,
                    null);
            return extractOutput(raw);
        } finally {
            Runner.release(conversationId);
        }
    }

    public int toolCount() {
        return tools.size();
    }

    @Override
    public void close() {
        for (Tool tool : tools) {
            try {
                Runner.resourceMgr().removeTool(
                        tool.getCard().getId(),
                        TaskCollectorAgentConstants.AGENT_ID,
                        TagMatchStrategy.ALL,
                        true);
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void registerTool(Tool tool) {
        try {
            Runner.resourceMgr().removeTool(
                    tool.getCard().getId(),
                    TaskCollectorAgentConstants.AGENT_ID,
                    TagMatchStrategy.ALL,
                    true);
        } catch (RuntimeException ignored) {
            // expected on first registration
        }
        Runner.resourceMgr().addTool(tool, TaskCollectorAgentConstants.AGENT_ID);
    }

    @SuppressWarnings("unchecked")
    private static String extractOutput(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Object output = ((Map<String, Object>) map).get("output");
            if (output != null) {
                return String.valueOf(output);
            }
        }
        return raw == null ? "" : String.valueOf(raw);
    }
}

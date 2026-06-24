/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector;

import com.huawei.ascend.examples.travel.taskcollector.mcp.McpToolRegistry;
import com.huawei.ascend.examples.travel.taskcollector.prompt.SystemPromptBuilder;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

public final class TaskCollectorAgentBuilder {

    private String provider;
    private String apiKey;
    private String apiBase;
    private String modelName;
    private boolean verifySsl;
    private int maxIterations = TaskCollectorAgentConstants.DEFAULT_MAX_ITERATIONS;
    private String userId = "zhang3";
    private String defaultCity = "深圳";

    private TaskCollectorAgentBuilder() {
    }

    public static TaskCollectorAgentBuilder builder() {
        return new TaskCollectorAgentBuilder();
    }

    public TaskCollectorAgentBuilder modelClient(
            String provider,
            String apiKey,
            String apiBase,
            String modelName,
            boolean verifySsl) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        this.verifySsl = verifySsl;
        return this;
    }

    public TaskCollectorAgentBuilder maxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    public TaskCollectorAgentBuilder userId(String userId) {
        this.userId = userId;
        return this;
    }

    public TaskCollectorAgentBuilder defaultCity(String defaultCity) {
        this.defaultCity = defaultCity;
        return this;
    }

    public ReActAgent build() {
        AgentCard card = AgentCard.builder()
                .id(TaskCollectorAgentConstants.AGENT_ID)
                .name(TaskCollectorAgentConstants.AGENT_ID)
                .description("差旅事项收集子智能体")
                .build();
        ReActAgent agent = new ReActAgent(card);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(maxIterations)
                .build()
                .configureModelClient(provider, apiKey, apiBase, modelName, verifySsl);
        agent.configure(config);
        agent.addPromptBuilderSection(
                "task_collector_business_rules",
                SystemPromptBuilder.build(userId, defaultCity),
                20);
        for (Tool tool : McpToolRegistry.mockTools()) {
            Runner.resourceMgr().addTool(tool, TaskCollectorAgentConstants.AGENT_ID);
            agent.getAbilityManager().add(tool.getCard());
        }
        return agent;
    }
}

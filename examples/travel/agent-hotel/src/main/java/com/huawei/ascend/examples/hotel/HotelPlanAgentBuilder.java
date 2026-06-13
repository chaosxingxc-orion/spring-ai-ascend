/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel;

import com.huawei.ascend.examples.hotel.mock.MockHotelInventory;
import com.huawei.ascend.examples.hotel.prompt.SystemPromptBuilder;
import com.huawei.ascend.examples.hotel.tool.HotelDetailTool;
import com.huawei.ascend.examples.hotel.tool.HotelSearchTool;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 反射友好的酒店子智能体入口：只用基本类型参数装配、返回 openJiuwen {@link ReActAgent}。
 *
 * <p>目的：让上游编排方（如 agent-trip / 主规划智能体）可以用反射加载并构建酒店子智能体，
 * 而<b>不引入 agent-hotel 的任何编译期依赖</b>——跨反射边界只暴露 {@code String/boolean}
 * 基本类型与 agent-core-java 的共享类型 {@code ReActAgent}。
 *
 * <p>用法（直接）：
 * <pre>
 *   ReActAgent hotel = HotelPlanAgentBuilder.builder()
 *           .modelClient(provider, apiKey, apiBase, modelName, verifySsl)
 *           .build();
 *   Object out = Runner.runAgent(hotel, Map.of("query", nl, "conversation_id", id), null, null);
 * </pre>
 *
 * <p>用法（反射）：参见 agent-trip 的 {@code ReflectiveHotelPlannerClient}。
 *
 * <p>与 {@link HotelPlanningAgent} 的关系：本类是「构建后由调用方驱动」的入口，返回裸 ReActAgent；
 * {@link HotelPlanningAgent} 则封装了 {@code chat(String)} 与每轮会话清理，二者复用同一套工具/提示词。
 */
public final class HotelPlanAgentBuilder {

    private static final int MAX_ITERATIONS = 6;
    private static final String AGENT_ID_PREFIX = "hotel-planning-agent-";
    private static final AtomicLong INSTANCE_COUNTER = new AtomicLong();

    private String provider;
    private String apiKey;
    private String apiBase;
    private String modelName;
    private boolean verifySsl;

    private HotelPlanAgentBuilder() {
    }

    public static HotelPlanAgentBuilder builder() {
        return new HotelPlanAgentBuilder();
    }

    public HotelPlanAgentBuilder modelClient(String provider, String apiKey, String apiBase,
                                             String modelName, boolean verifySsl) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        this.verifySsl = verifySsl;
        return this;
    }

    /**
     * 构建并返回已注册酒店检索工具的 ReActAgent。
     * <p>每次调用生成独立 agentId，避免多个酒店 agent（或 hotel+flight+train）在同一进程内
     * 争抢全局 Runner 的 tag→tool 索引。
     */
    public ReActAgent build() {
        String agentId = AGENT_ID_PREFIX + INSTANCE_COUNTER.incrementAndGet();

        AgentCard card = AgentCard.builder()
                .id(agentId)
                .name(agentId)
                .description("差旅多智能体系统中的酒店规划子智能体（ReAct + 内存 mock 数据）")
                .build();
        ReActAgent agent = new ReActAgent(card);

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", SystemPromptBuilder.build())))
                .maxIterations(MAX_ITERATIONS)
                .build()
                .configureModelClient(provider, apiKey, apiBase, modelName, verifySsl);
        agent.configure(config);

        MockHotelInventory inventory = new MockHotelInventory();
        registerTool(agent, agentId, new HotelSearchTool(inventory));
        registerTool(agent, agentId, new HotelDetailTool(inventory));
        return agent;
    }

    private static void registerTool(ReActAgent agent, String agentId, Tool tool) {
        Runner.resourceMgr().addTool(tool, agentId);
        agent.getAbilityManager().add(tool.getCard());
    }
}

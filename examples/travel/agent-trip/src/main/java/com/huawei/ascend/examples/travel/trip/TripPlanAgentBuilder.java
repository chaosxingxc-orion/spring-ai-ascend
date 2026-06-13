/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.trip;

import com.huawei.ascend.examples.trip.TripPlanningAgent;
import com.huawei.ascend.examples.trip.hotel.HotelPlannerClient;
import com.huawei.ascend.examples.trip.hotel.ReflectiveHotelPlannerClient;
import com.huawei.ascend.examples.trip.prompt.SystemPromptBuilder;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.List;
import java.util.Map;

/**
 * 反射友好的行程规划智能体入口。
 *
 * <p>包名/类名<b>对齐 travel-assistant-mainplan-agent 的反射期望</b>
 * （{@code Main.TRIP_BUILDER_CLASS = "com.huawei.ascend.examples.travel.trip.TripPlanAgentBuilder"}），
 * 因此主规划智能体可在无编译期依赖的情况下用反射装配并驱动本智能体。
 *
 * <p>{@code build()} 返回已注册 {@code plan_hotel} 工具的 {@link ReActAgent}；该工具通过
 * {@link ReflectiveHotelPlannerClient} 反射委托 agent-hotel，全程不引入 agent-hotel 编译期依赖。
 *
 * <pre>
 *   ReActAgent trip = TripPlanAgentBuilder.builder()
 *           .modelClient(provider, apiKey, apiBase, modelName, verifySsl)
 *           .build();
 *   Object out = trip.invoke(Map.of("query", nl, "conversation_id", id), session);
 * </pre>
 */
public final class TripPlanAgentBuilder {

    private static final String AGENT_ID = "trip-planning-agent";
    private static final int MAX_ITERATIONS = 5;

    private String provider;
    private String apiKey;
    private String apiBase;
    private String modelName;
    private boolean verifySsl;

    private TripPlanAgentBuilder() {
    }

    public static TripPlanAgentBuilder builder() {
        return new TripPlanAgentBuilder();
    }

    public TripPlanAgentBuilder modelClient(String provider, String apiKey, String apiBase,
                                            String modelName, boolean verifySsl) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        this.verifySsl = verifySsl;
        return this;
    }

    /**
     * 构建并返回行程规划 ReActAgent（已注册 plan_hotel 工具）。
     * <p>酒店子智能体在此反射装配：上游（主规划智能体）只给 LLM 配置，酒店复用同一份配置。
     */
    public ReActAgent build() {
        HotelPlannerClient hotelClient =
                new ReflectiveHotelPlannerClient(provider, apiKey, apiBase, modelName, verifySsl);

        AgentCard card = AgentCard.builder()
                .id(AGENT_ID)
                .name(AGENT_ID)
                .description("差旅行程规划智能体")
                .build();
        ReActAgent agent = new ReActAgent(card);

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", SystemPromptBuilder.build())))
                .maxIterations(MAX_ITERATIONS)
                .build()
                .configureModelClient(provider, apiKey, apiBase, modelName, verifySsl);
        agent.configure(config);

        Tool planHotel = TripPlanningAgent.buildPlanHotelTool(hotelClient);
        Runner.resourceMgr().addTool(planHotel, AGENT_ID);
        agent.getAbilityManager().add(planHotel.getCard());
        return agent;
    }
}

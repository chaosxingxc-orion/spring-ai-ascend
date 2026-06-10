/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip;

import com.huawei.ascend.examples.trip.hotel.HotelPlannerClient;
import com.huawei.ascend.examples.trip.prompt.SystemPromptBuilder;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 行程规划智能体（ReAct 单体）。
 * <p>用 agent-core-java 的 ReActAgent，让 LLM 在「思考→调工具→观察→整合」循环里
 * 理解差旅诉求、调用唯一工具 plan_hotel，最终产出以住宿为核心的 markdown 行程方案。
 */
public class TripPlanningAgent {

    private static final String AGENT_ID = "trip-planning-agent";

    private final LlmConfig llm;
    private final HotelPlannerClient hotelClient;

    public TripPlanningAgent(LlmConfig llm, HotelPlannerClient hotelClient) {
        this.llm = llm;
        this.hotelClient = hotelClient;
    }

    /**
     * 同步入口：传入自然语言差旅诉求，返回 markdown 行程方案。
     */
    public String chat(String userMessage) {
        String conversationId = UUID.randomUUID().toString().substring(0, 8);
        Tool planHotel = buildPlanHotelTool();
        try {
            ReActAgent agent = buildAgent();
            registerTool(agent, planHotel);

            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) Runner.runAgent(
                    agent,
                    Map.of("query", userMessage, "conversation_id", conversationId),
                    null,
                    null);
            return (String) out.get("output");
        } finally {
            Runner.resourceMgr().removeTool(planHotel.getCard().getId(), AGENT_ID, TagMatchStrategy.ALL, true);
            Runner.release(conversationId);
        }
    }

    private ReActAgent buildAgent() {
        AgentCard card = AgentCard.builder()
                .id(AGENT_ID)
                .name(AGENT_ID)
                .description("差旅行程规划智能体")
                .build();
        ReActAgent agent = new ReActAgent(card);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", SystemPromptBuilder.build())))
                .maxIterations(5)
                .build()
                .configureModelClient(llm.provider(), llm.apiKey(), llm.apiBase(),
                        llm.modelName(), llm.sslVerify());
        agent.configure(config);
        return agent;
    }

    private void registerTool(ReActAgent agent, Tool tool) {
        Runner.resourceMgr().removeTool(tool.getCard().getId(), AGENT_ID, TagMatchStrategy.ALL, true);
        Runner.resourceMgr().addTool(tool, AGENT_ID);
        agent.getAbilityManager().add(tool.getCard());
    }

    /** 唯一工具：酒店规划（调下游酒店子智能体）。包级可见，便于单测。 */
    Tool buildPlanHotelTool() {
        return buildPlanHotelTool(hotelClient);
    }

    /**
     * 唯一工具的静态构造版：把 {@code plan_hotel} 工具与酒店出站客户端解耦，
     * 供 {@code TripPlanAgentBuilder} 等外部装配点复用（注册到自己构建的 ReActAgent 上）。
     */
    public static Tool buildPlanHotelTool(HotelPlannerClient hotelClient) {
        ToolCard card = ToolCard.builder()
                .id("plan_hotel")
                .name("plan_hotel")
                .description("根据城市/入离日期/差标/偏好查询并推荐差旅酒店，返回 markdown")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", Map.of("type", "string", "description", "目的城市中文名"),
                                "checkIn", Map.of("type", "string", "description", "入住 yyyy-MM-dd"),
                                "checkOut", Map.of("type", "string", "description", "离店 yyyy-MM-dd"),
                                "policyText", Map.of("type", "string", "description", "差标与酒店偏好原文：价格上限/最低星级/协议品牌/商圈/设施"),
                                "preferences", Map.of("type", "string", "description", "其他偏好")),
                        "required", List.of("city", "checkIn", "checkOut")))
                .build();
        return new LocalFunction(card, inputs -> hotelClient.plan(buildHotelNl(inputs)));
    }

    /** 拼酒店子智能体 NL（纯自然语言 + 中文品牌名）。public static，便于单测与外部复用。 */
    public static String buildHotelNl(Map<String, Object> in) {
        return """
               出差到 %s，%s 至 %s。
               %s
               偏好：%s。
               """.formatted(in.get("city"), in.get("checkIn"), in.get("checkOut"),
                in.getOrDefault("policyText", ""), in.getOrDefault("preferences", ""));
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.hotel.a2a;

import com.huawei.ascend.examples.agentscope.hotel.HotelPlanningAgent;
import com.huawei.ascend.examples.agentscope.hotel.LlmConfig;
import com.huawei.ascend.runtime.engine.a2a.AgentCards;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;

import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentSkill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class HotelAgentConfiguration {

    static final String AGENT_ID = "hotel-planning-agentscope";

    @Bean
    LlmConfig hotelAgentscopeLlmConfig(
            @Value("${hotel-agent.llm.api-key}") String apiKey,
            @Value("${hotel-agent.llm.api-base}") String baseUrl,
            @Value("${hotel-agent.llm.endpoint-path}") String endpointPath,
            @Value("${hotel-agent.llm.model-name}") String modelName) {
        return new LlmConfig(apiKey, baseUrl, endpointPath, modelName);
    }

    @Bean
    HotelPlanningAgent hotelPlanningAgent(LlmConfig llm) {
        return new HotelPlanningAgent(AGENT_ID, llm);
    }

    @Bean
    AgentRuntimeHandler hotelAgentscopeHandler(HotelPlanningAgent agent) {
        return new AgentScopeAgentRuntimeHandler(
                AGENT_ID,
                "Hotel Planning Agent (AgentScope)",
                "Corporate-travel hotel planning sub-agent built with AgentScope core "
                        + "and hosted by agent-runtime.",
                agent);
    }

    /**
     * Code-built A2A agent card. Takes precedence over both {@code AgentCardProvider} and the
     * YAML-driven path in {@code RuntimeAutoConfiguration#a2aAgentCard}, so the card surface
     * is entirely under this configuration's control and does not depend on
     * {@code AgentCardProperties} (whose YAML schema currently lacks
     * skills / capabilities / defaultOutputModes — see runtime issue #315).
     *
     * <p>Built off {@link AgentCards#create(String, String, String, String)} as a base so the
     * required SDK fields (provider, supportedInterfaces, preferredTransport) come from the
     * canonical runtime factory rather than being hand-set here. We only overlay what is
     * actually distinctive for this hotel agent: capabilities, default modes, and the two
     * skills.
     */
    @Bean
    AgentCard hotelAgentscopeAgentCard() {
        AgentCard base = AgentCards.create(
                AGENT_ID,
                "Corporate-travel hotel planning sub-agent (AgentScope flavor). "
                        + "Given a destination city, check-in / check-out dates and optional star / "
                        + "price / brand filters, returns a markdown comparison of recommended hotels "
                        + "with rooms and prices.",
                "0.1.0",
                "/a2a");
        return AgentCard.builder(base)
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .extendedAgentCard(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(
                        AgentSkill.builder()
                                .id("hotel_search")
                                .name("Search hotels in a city")
                                .description("Search hotels by city, check-in / check-out dates, "
                                        + "optional max price per night, minimum star, brand whitelist "
                                        + "and keywords (district / facility). Returns a ranked, paginated list.")
                                .tags(List.of("hotel", "search", "travel", "corporate-travel", "agentscope"))
                                .examples(List.of(
                                        "帮我找北京 6 月 18 日入住、6 月 20 日离店、四星以上、预算 800 元以内的酒店",
                                        "Find a 5-star hotel near Shanghai Hongqiao for tomorrow night"))
                                .inputModes(List.of("text"))
                                .outputModes(List.of("text"))
                                .build(),
                        AgentSkill.builder()
                                .id("hotel_detail")
                                .name("Get hotel details by id")
                                .description("Given a hotelId from a prior search call, return the hotel "
                                        + "header fields plus the room offer list (room name, bed type, "
                                        + "area, breakfast, cancellation, RMB price).")
                                .tags(List.of("hotel", "detail", "travel", "agentscope"))
                                .examples(List.of(
                                        "查一下 hotel id BJ-001 的详细信息",
                                        "Show room offers for hotel id SHA-042"))
                                .inputModes(List.of("text"))
                                .outputModes(List.of("text"))
                                .build()))
                .build();
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.trip.a2a;

import com.huawei.ascend.examples.agentscope.trip.LlmConfig;
import com.huawei.ascend.examples.agentscope.trip.TripPlanningAgent;
import com.huawei.ascend.examples.agentscope.trip.a2a.tool.A2aHotelPlannerClient;
import com.huawei.ascend.examples.agentscope.trip.tool.HotelPlannerClient;
import com.huawei.ascend.examples.agentscope.trip.tool.TripSkills;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TripAgentConfiguration {

    static final String AGENT_ID = "trip-planning-agentscope";

    @Bean
    LlmConfig tripAgentscopeLlmConfig(
            @Value("${trip-agent.llm.api-key}") String apiKey,
            @Value("${trip-agent.llm.api-base}") String baseUrl,
            @Value("${trip-agent.llm.endpoint-path}") String endpointPath,
            @Value("${trip-agent.llm.model-name}") String modelName) {
        return new LlmConfig(apiKey, baseUrl, endpointPath, modelName);
    }

    /**
     * Production binding for {@link HotelPlannerClient}: calls the downstream
     * hotel agent over A2A JSON-RPC. {@code destroyMethod="close"} lets Spring
     * close the cached transport at context shutdown.
     */
    @Bean(destroyMethod = "close")
    HotelPlannerClient tripAgentscopeHotelPlannerClient(
            @Value("${trip-agent.hotel.base-url}") String hotelBaseUrl) {
        return new A2aHotelPlannerClient(hotelBaseUrl);
    }

    @Bean
    TripSkills tripAgentscopeSkills(HotelPlannerClient hotelPlannerClient) {
        return new TripSkills(hotelPlannerClient);
    }

    @Bean
    TripPlanningAgent tripPlanningAgent(LlmConfig llm, TripSkills skills) {
        return new TripPlanningAgent(AGENT_ID, llm, skills);
    }

    @Bean
    AgentRuntimeHandler tripAgentscopeHandler(TripPlanningAgent agent) {
        return new AgentScopeAgentRuntimeHandler(
                AGENT_ID,
                "Trip Planning Agent (AgentScope)",
                "Corporate-travel trip planning agent (AgentScope flavor). Calls the hotel "
                        + "planning sub-agent over A2A via the plan_hotel tool.",
                new TripPlanningRuntimeAdapter(agent));
    }
}
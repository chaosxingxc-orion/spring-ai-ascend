/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.hotel.a2a;

import com.huawei.ascend.examples.agentscope.hotel.HotelPlanningAgent;
import com.huawei.ascend.examples.agentscope.hotel.LlmConfig;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;

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
                new HotelPlanningRuntimeAdapter(agent));
    }
}
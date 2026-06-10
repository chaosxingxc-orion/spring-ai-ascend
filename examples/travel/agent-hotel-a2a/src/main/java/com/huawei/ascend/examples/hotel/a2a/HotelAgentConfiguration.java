/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import com.huawei.ascend.examples.hotel.HotelPlanningAgent;
import com.huawei.ascend.examples.hotel.LlmConfig;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentCards;
import org.a2aproject.sdk.spec.AgentCard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class HotelAgentConfiguration {

    static final String AGENT_ID = "hotel-planning-agent";

    @Bean
    LlmConfig hotelAgentLlmConfig(
            @Value("${hotel-agent.llm.provider}") String provider,
            @Value("${hotel-agent.llm.api-key}") String apiKey,
            @Value("${hotel-agent.llm.api-base}") String apiBase,
            @Value("${hotel-agent.llm.model-name}") String modelName,
            @Value("${hotel-agent.llm.ssl-verify}") boolean sslVerify) {
        return new LlmConfig(provider, apiKey, apiBase, modelName, sslVerify);
    }

    /**
     * Long-lived singleton — tool registration happens once at construction;
     * close() unregisters from Runner.resourceMgr() at context shutdown.
     */
    @Bean(destroyMethod = "close")
    HotelPlanningAgent hotelPlanningAgent(LlmConfig llmConfig) {
        return new HotelPlanningAgent(llmConfig);
    }

    @Bean
    OpenJiuwenAgentRuntimeHandler hotelAgentHandler(HotelPlanningAgent agent) {
        return new HotelAgentHandler(AGENT_ID, agent);
    }

    @Bean
    AgentCard hotelAgentCard() {
        return AgentCards.create(AGENT_ID, "Hotel planning sub-agent for the corporate travel multi-agent system.");
    }
}

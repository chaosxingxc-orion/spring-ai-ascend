/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.trip;

import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.service.adapters.agentcore.agentfw.JiuwenCoreAgentHandler;
import com.openjiuwen.service.spec.spi.AgentHandler;
import com.openjiuwen.service.travel.trip.prompt.SystemPromptBuilder;
import com.openjiuwen.service.travel.trip.rails.RemoteHotelRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

/**
 * Travel trip agent — ReActAgent with a {@link RemoteHotelRail} that defers hotel queries to the
 * remote hotel agent via the A2A interrupt chain.
 */
@SpringBootApplication(scanBasePackages = "com.openjiuwen.service.app")
@EnableConfigurationProperties(TravelTripLlmProperties.class)
public class TravelTripApplication {

    private static final Logger log = LoggerFactory.getLogger(TravelTripApplication.class);
    private static final String AGENT_ID = "agent-openjiuwen-travel-trip";

    public static void main(String[] args) {
        SpringApplication.run(TravelTripApplication.class, args);
    }

    @Bean
    AgentHandler travelTripHandler(TravelTripLlmProperties props) {
        if (!props.isConfigured()) {
            log.warn("LLM not configured (openjiuwen.travel.trip.llm.*); agent boots but cannot serve real queries");
        }
        return new JiuwenCoreAgentHandler(buildTripAgent(props));
    }

    static ReActAgent buildTripAgent(TravelTripLlmProperties props) {
        AgentCard card = AgentCard.builder()
                .id(AGENT_ID).name(AGENT_ID)
                .description("差旅行程规划智能体（委托远端酒店智能体）")
                .build();
        ReActAgent agent = new ReActAgent(card);
        String systemPrompt = SystemPromptBuilder.build("hotel");
        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(props.getMaxIterations())
                .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
                .build()
                .configureModelClient(props.getProvider(), props.getApiKey(), props.getApiBase(),
                        props.getModelName(), props.isSslVerify());
        agent.configure(config);
        agent.registerRail(new RemoteHotelRail());
        return agent;
    }
}

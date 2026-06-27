/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.hotel;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.service.adapters.agentcore.agentfw.JiuwenCoreAgentHandler;
import com.openjiuwen.service.spec.spi.AgentHandler;
import com.openjiuwen.service.travel.hotel.mock.MockHotelInventory;
import com.openjiuwen.service.travel.hotel.prompt.SystemPromptBuilder;
import com.openjiuwen.service.travel.hotel.tool.HotelDetailTool;
import com.openjiuwen.service.travel.hotel.tool.HotelSearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

/**
 * Travel hotel leaf agent — local ReActAgent wired with the real hotel_search / hotel_detail
 * tools backed by {@link MockHotelInventory}, and a server-side system prompt built by
 * {@link SystemPromptBuilder} ({today} injected in Asia/Shanghai).
 * scanBasePackages picks up agent-service-app controllers/autoconfig (REST + A2A endpoints).
 */
@SpringBootApplication(scanBasePackages = "com.openjiuwen.service.app")
@EnableConfigurationProperties(TravelHotelLlmProperties.class)
public class TravelHotelApplication {

    private static final Logger log = LoggerFactory.getLogger(TravelHotelApplication.class);
    private static final String AGENT_ID = "agent-openjiuwen-travel-hotel";

    public static void main(String[] args) {
        SpringApplication.run(TravelHotelApplication.class, args);
    }

    @Bean
    AgentHandler travelHotelHandler(TravelHotelLlmProperties props) {
        if (!props.isConfigured()) {
            log.warn("LLM not configured (openjiuwen.travel.hotel.llm.*); agent boots but cannot serve real queries");
        }
        return new JiuwenCoreAgentHandler(buildHotelAgent(props));
    }

    static ReActAgent buildHotelAgent(TravelHotelLlmProperties props) {
        // Inventory loads mock/hotels.json from the classpath at construction; throws at boot
        // if the resource is missing.
        MockHotelInventory inventory = new MockHotelInventory();
        log.info("Loaded hotel inventory: {} hotels across {} cities",
                inventory.totalHotels(), inventory.totalCities());

        HotelSearchTool search = new HotelSearchTool(inventory);
        HotelDetailTool detail = new HotelDetailTool(inventory);

        AgentCard card = AgentCard.builder()
                .id(AGENT_ID).name(AGENT_ID)
                .description("差旅酒店规划智能体（叶子，本地 mock 工具）")
                .build();
        ReActAgent agent = new ReActAgent(card);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(props.getMaxIterations())
                .promptTemplate(List.of(Map.of("role", "system", "content", SystemPromptBuilder.build())))
                .build()
                .configureModelClient(props.getProvider(), props.getApiKey(), props.getApiBase(),
                        props.getModelName(), props.isSslVerify());
        agent.configure(config);
        registerTool(agent, search);
        registerTool(agent, detail);
        return agent;
    }

    /**
     * Register a real {@link Tool} so it is both executable (ResourceMgr) and visible to the
     * LLM (AbilityManager card). AbilityManager.add(card) alone only stores the schema; the
     * executor must also be registered via Runner.resourceMgr().addTool(...), or execution
     * throws "Tool instance not found in resource_mgr". Mirrors the ascend helper: defensive
     * removeTool first (clears any leftover instance), then addTool + card add.
     */
    private static void registerTool(ReActAgent agent, Tool tool) {
        String agentId = agent.getCard().getId();
        try {
            Runner.resourceMgr().removeTool(tool.getCard().getId(), agentId, TagMatchStrategy.ALL, true);
        } catch (RuntimeException ignored) {
            // expected on first registration — nothing to remove yet
        }
        Runner.resourceMgr().addTool(tool, agentId);
        agent.getAbilityManager().add(tool.getCard());
    }
}

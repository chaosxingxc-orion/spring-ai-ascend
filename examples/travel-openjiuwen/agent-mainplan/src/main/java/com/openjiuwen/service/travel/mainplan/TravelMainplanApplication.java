/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.mainplan;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.service.adapters.agentcore.agentfw.JiuwenCoreAgentHandler;
import com.openjiuwen.service.spec.spi.AgentHandler;
import com.openjiuwen.service.travel.mainplan.prompt.MainPlanPromptBuilder;
import com.openjiuwen.service.travel.mainplan.rails.RemoteTripRail;
import com.openjiuwen.service.travel.mainplan.rails.UserInputInterruptRail;
import com.openjiuwen.service.travel.mainplan.tools.RequestUserInputTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

/**
 * Travel mainplan agent — ReActAgent that loads the real main-plan system prompt and wires:
 * <ul>
 *   <li>{@link RequestUserInputTool} + {@link UserInputInterruptRail} — the user-input追问 chain</li>
 *   <li>{@link RemoteTripRail} — A2A delegation to the remote trip agent</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = "com.openjiuwen.service.app")
@EnableConfigurationProperties(TravelMainplanLlmProperties.class)
public class TravelMainplanApplication {

    private static final Logger log = LoggerFactory.getLogger(TravelMainplanApplication.class);
    private static final String AGENT_ID = "agent-openjiuwen-travel-mainplan";

    public static void main(String[] args) {
        SpringApplication.run(TravelMainplanApplication.class, args);
    }

    @Bean
    AgentHandler travelMainplanHandler(TravelMainplanLlmProperties props) {
        if (!props.isConfigured()) {
            log.warn("LLM not configured (openjiuwen.travel.mainplan.llm.*); agent boots but cannot serve real queries");
        }
        return new JiuwenCoreAgentHandler(buildMainplanAgent(props));
    }

    static ReActAgent buildMainplanAgent(TravelMainplanLlmProperties props) {
        AgentCard card = AgentCard.builder()
                .id(AGENT_ID).name(AGENT_ID)
                .description("差旅助手主规划智能体（委托远端行程规划智能体）")
                .build();
        ReActAgent agent = new ReActAgent(card);

        String systemPrompt = MainPlanPromptBuilder.build(props.getDefaultCity(), props.getTravelerName());
        TravelMainplanLlmProperties.Llm llm = props.getLlm();
        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(llm.getMaxIterations())
                .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
                .build()
                .configureModelClient(llm.getProvider(), llm.getApiKey(), llm.getApiBase(),
                        llm.getModelName(), llm.isSslVerify());
        agent.configure(config);

        registerTool(agent, new RequestUserInputTool());

        agent.registerRail(new RemoteTripRail());
        agent.registerRail(new UserInputInterruptRail());
        return agent;
    }

    /**
     * Register a real {@link Tool} so it is both executable (ResourceMgr) and visible to the
     * LLM (AbilityManager card). AbilityManager.add(card) alone only stores the schema; the
     * executor must also be registered via Runner.resourceMgr().addTool(...), or resume-time
     * execution throws "Tool instance not found in resource_mgr". Mirrors the ascend helper:
     * defensive removeTool first (clears any leftover instance), then addTool + card add.
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

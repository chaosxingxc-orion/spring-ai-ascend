/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan;

import com.huawei.ascend.examples.travel.mainplan.constant.AgentConstants;
import com.huawei.ascend.examples.travel.mainplan.rails.UserInputInterruptRail;
import com.huawei.ascend.examples.travel.mainplan.tools.DispatchTravelPlanTool;
import com.huawei.ascend.examples.travel.mainplan.tools.RequestUserInputTool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builder that constructs a fully configured {@link ReActAgent} for the Main Plan Agent.
 * <p>
 * The returned ReActAgent has:
 * <ul>
 *   <li>A structured System Prompt loaded from resources (with template variable substitution)</li>
 *   <li>Two tools: {@code dispatch_travel_plan} and {@code request_user_input}</li>
 *   <li>A {@link UserInputInterruptRail} for multi-turn info collection</li>
 * </ul>
 */
public class MainPlanAgentBuilder {

    private String modelProvider;
    private String apiKey;
    private String apiBase;
    private String modelName;
    private boolean verifySsl;
    private Function<String, String> travelPlanDispatcher;
    private int maxIterations = 10;
    private String systemPromptOverride;
    private String defaultCity = "深圳";
    private String travelerName = "";

    private MainPlanAgentBuilder() {
    }

    public static MainPlanAgentBuilderBuilder builder() {
        return new MainPlanAgentBuilderBuilder();
    }

    /**
     * Build and return a fully configured ReActAgent.
     *
     * @return configured ReActAgent instance
     * @throws IllegalArgumentException if required parameters are missing
     */
    public ReActAgent build() {
        validate();

        // 1. Create ReActAgent
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(AgentConstants.AGENT_ID)
                .name(AgentConstants.AGENT_ID)
                .description("差旅助手主规划智能体")
                .build());

        // 2. Load and prepare System Prompt
        String systemPrompt = loadSystemPrompt();

        // 3. Configure ReActAgent
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
                .maxIterations(maxIterations)
                .build()
                .configureModelClient(modelProvider, apiKey, apiBase, modelName, verifySsl);
        agent.configure(config);

        // 4. Register tools
        DispatchTravelPlanTool dispatchTool = new DispatchTravelPlanTool(travelPlanDispatcher);
        RequestUserInputTool inputTool = new RequestUserInputTool();
        Runner.resourceMgr().addTool(dispatchTool, agent.getCard().getId());
        Runner.resourceMgr().addTool(inputTool, agent.getCard().getId());
        agent.getAbilityManager().add(dispatchTool.getCard());
        agent.getAbilityManager().add(inputTool.getCard());

        // 5. Register Rail
        agent.registerRail(new UserInputInterruptRail());

        return agent;
    }

    private void validate() {
        if (modelProvider == null || modelProvider.isBlank()) {
            throw new IllegalArgumentException("modelProvider is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName is required");
        }
        if (travelPlanDispatcher == null) {
            throw new IllegalArgumentException("travelPlanDispatcher is required");
        }
    }

    private String loadSystemPrompt() {
        String prompt;
        if (systemPromptOverride != null && !systemPromptOverride.isBlank()) {
            prompt = systemPromptOverride;
        } else {
            prompt = loadResource(AgentConstants.PROMPT_RESOURCE_PATH);
        }
        return applyTemplateVariables(prompt);
    }

    private String applyTemplateVariables(String prompt) {
        String currentDatetime = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"));
        return prompt
                .replace(AgentConstants.VAR_CURRENT_DATETIME, currentDatetime)
                .replace(AgentConstants.VAR_DEFAULT_CITY, defaultCity)
                .replace(AgentConstants.VAR_TRAVELER_NAME, travelerName);
    }

    private static String loadResource(String path) {
        try (InputStream is = MainPlanAgentBuilder.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource: " + path, e);
        }
    }

    /**
     * Builder for {@link MainPlanAgentBuilder}.
     */
    public static class MainPlanAgentBuilderBuilder {
        private final MainPlanAgentBuilder instance = new MainPlanAgentBuilder();

        public MainPlanAgentBuilderBuilder modelClient(String provider, String apiKey,
                                                        String apiBase, String modelName,
                                                        boolean verifySsl) {
            instance.modelProvider = provider;
            instance.apiKey = apiKey;
            instance.apiBase = apiBase;
            instance.modelName = modelName;
            instance.verifySsl = verifySsl;
            return this;
        }

        public MainPlanAgentBuilderBuilder travelPlanDispatcher(
                Function<String, String> dispatcher) {
            instance.travelPlanDispatcher = dispatcher;
            return this;
        }

        public MainPlanAgentBuilderBuilder maxIterations(int maxIterations) {
            instance.maxIterations = maxIterations;
            return this;
        }

        public MainPlanAgentBuilderBuilder systemPromptOverride(String override) {
            instance.systemPromptOverride = override;
            return this;
        }

        public MainPlanAgentBuilderBuilder defaultCity(String city) {
            instance.defaultCity = city;
            return this;
        }

        public MainPlanAgentBuilderBuilder travelerName(String name) {
            instance.travelerName = name;
            return this;
        }

        public ReActAgent build() {
            return instance.build();
        }
    }
}

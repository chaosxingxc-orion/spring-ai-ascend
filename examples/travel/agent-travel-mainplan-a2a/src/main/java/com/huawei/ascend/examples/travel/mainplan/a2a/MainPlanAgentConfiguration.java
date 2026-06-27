/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan.a2a;

import com.huawei.ascend.examples.travel.mainplan.a2a.constant.AgentConstants;
import com.huawei.ascend.examples.travel.mainplan.a2a.rails.UserInputInterruptRail;
import com.huawei.ascend.examples.travel.mainplan.a2a.tools.RequestUserInputTool;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.a2a.AgentCards;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MainPlanAgentConfiguration {

    static final String AGENT_ID = AgentConstants.AGENT_ID;

    @Bean
    Checkpointer mainPlanCheckpointer(
            @Value("${main-plan-agent.checkpointer:in-memory}") String checkpointerType,
            @Value("${main-plan-agent.redis-url:redis://localhost:6379}") String redisUrl) {
        Checkpointer inMemoryCheckpointer = new InMemoryCheckpointer();
        Checkpointer redisCheckpointer = new RedisCheckpointer.Provider()
                .create(Map.of("connection", Map.of("url", redisUrl)));
        Checkpointer selected = isRedisCheckpointer(checkpointerType) ? redisCheckpointer : inMemoryCheckpointer;
        CheckpointerFactory.setDefaultCheckpointer(selected);
        return selected;
    }

    private static boolean isRedisCheckpointer(String checkpointerType) {
        return "redis".equals(String.valueOf(checkpointerType).trim().toLowerCase(Locale.ROOT));
    }

    @Bean
    OpenJiuwenAgentRuntimeHandler mainPlanAgentHandler(
            @Value("${main-plan-agent.model-provider:dashscope}") String modelProvider,
            @Value("${main-plan-agent.api-key:}") String apiKey,
            @Value("${main-plan-agent.api-base:}") String apiBase,
            @Value("${main-plan-agent.model-name:qwen-max}") String modelName,
            @Value("${main-plan-agent.ssl-verify:false}") boolean sslVerify,
            @Value("${main-plan-agent.max-iterations:10}") int maxIterations,
            @Value("${main-plan-agent.default-city:深圳}") String defaultCity,
            @Value("${main-plan-agent.traveler-name:}") String travelerName) {
        return new MainPlanAgentHandler(
                modelProvider, apiKey, apiBase, modelName, sslVerify,
                maxIterations, defaultCity, travelerName);
    }

    @Bean
    org.a2aproject.sdk.spec.AgentCard mainPlanAgentCard(
            @Value("${main-plan-agent.agent-card-endpoint:#{null}}") String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            return AgentCards.create(AGENT_ID, "差旅助手主规划智能体，通过 A2A 协议对外提供服务。",
                    "0.1.0", endpoint);
        }
        return AgentCards.create(AGENT_ID, "差旅助手主规划智能体，通过 A2A 协议对外提供服务。");
    }

    static final class MainPlanAgentHandler extends OpenJiuwenAgentRuntimeHandler {

        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;
        private final int maxIterations;
        private final String defaultCity;
        private final String travelerName;

        MainPlanAgentHandler(
                String modelProvider, String apiKey, String apiBase,
                String modelName, boolean sslVerify, int maxIterations,
                String defaultCity, String travelerName) {
            super(AGENT_ID);
            this.modelProvider = modelProvider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.modelName = modelName;
            this.sslVerify = sslVerify;
            this.maxIterations = maxIterations;
            this.defaultCity = defaultCity;
            this.travelerName = travelerName;
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("差旅助手主规划智能体")
                    .build();

            ReActAgent agent = new ReActAgent(card);

            String systemPrompt = loadAndPreparePrompt();
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
                    .maxIterations(maxIterations)
                    .build()
                    .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
            agent.configure(config);

            RequestUserInputTool inputTool = new RequestUserInputTool();
            Runner.resourceMgr().addTool(inputTool, agent.getCard().getId());
            agent.getAbilityManager().add(inputTool.getCard());

            agent.registerRail(new UserInputInterruptRail());

            return agent;
        }

        private String loadAndPreparePrompt() {
            String prompt = loadResource(AgentConstants.PROMPT_RESOURCE_PATH);
            String currentDatetime = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"));
            return prompt
                    .replace(AgentConstants.VAR_CURRENT_DATETIME, currentDatetime)
                    .replace(AgentConstants.VAR_DEFAULT_CITY, defaultCity)
                    .replace(AgentConstants.VAR_TRAVELER_NAME, travelerName)
                    .replace(AgentConstants.VAR_DISPATCH_TOOL_NAME, AgentConstants.REMOTE_TRIP_AGENT_ID);
        }

        private static String loadResource(String path) {
            try (InputStream is = MainPlanAgentHandler.class.getResourceAsStream(path)) {
                if (is == null) {
                    throw new IllegalStateException("Resource not found: " + path);
                }
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load resource: " + path, e);
            }
        }
    }
}

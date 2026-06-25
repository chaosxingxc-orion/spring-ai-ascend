/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip.a2a;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenCheckpointerConfigurer;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TripAgentConfiguration {

    @Bean
    Checkpointer tripCheckpointer() {
        return OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
    }

    @Bean
    OpenJiuwenAgentRuntimeHandler tripAgentHandler(
            @Value("${trip-agent.llm.model-provider}") String modelProvider,
            @Value("${trip-agent.llm.api-key}") String apiKey,
            @Value("${trip-agent.llm.api-base}") String apiBase,
            @Value("${trip-agent.llm.model-name}") String modelName,
            @Value("${trip-agent.llm.ssl-verify}") boolean sslVerify,
            @Value("${trip-agent.llm.max-iterations:" + TripAgentConstants.DEFAULT_MAX_ITERATIONS + "}")
            int maxIterations) {
        return new TripAgentHandler(modelProvider, apiKey, apiBase, modelName, sslVerify, maxIterations);
    }

    @Bean
    org.a2aproject.sdk.spec.AgentCard tripAgentCard() {
        return org.a2aproject.sdk.spec.AgentCard.builder()
                .name(TripAgentConstants.AGENT_ID)
                .description("Corporate-travel trip planning agent. Given a natural-language "
                        + "business-trip request (destination, dates, hotel budget / star rating / "
                        + "brand preferences), returns a markdown itinerary with hotel recommendations.")
                .version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", ""))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .extendedAgentCard(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text", "artifact"))
                .skills(List.of(
                        AgentSkill.builder()
                                .id("trip-planning")
                                .name("Plan corporate travel itinerary")
                                .description("Plan a corporate business trip. Pass a natural-language "
                                        + "message describing destination city, check-in / check-out dates "
                                        + "(yyyy-MM-dd), nightly budget, minimum star rating, district "
                                        + "preferences and preferred hotel brands. The agent calls the "
                                        + "remote hotel planner when needed and returns a markdown summary "
                                        + "with an itinerary overview and up to three recommended hotels.")
                                .tags(List.of("trip", "travel", "corporate-travel", "hotel"))
                                .examples(List.of(
                                        "我 6 月 16 日到 18 日去北京出差，差标每晚 800 元、4 星，偏好国贸附近，协议品牌全季/亚朵",
                                        "Plan a 2-night business trip to Shanghai from 2026-06-20, 4-star, max 900 CNY/night"))
                                .inputModes(List.of("text"))
                                .outputModes(List.of("text"))
                                .build()))
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();
    }

    static final class TripAgentHandler extends OpenJiuwenAgentRuntimeHandler {

        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;
        private final int maxIterations;

        TripAgentHandler(
                String modelProvider,
                String apiKey,
                String apiBase,
                String modelName,
                boolean sslVerify,
                int maxIterations) {
            super(TripAgentConstants.AGENT_ID);
            this.modelProvider = modelProvider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.modelName = modelName;
            this.sslVerify = sslVerify;
            this.maxIterations = maxIterations;
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            AgentCard card = AgentCard.builder()
                    .id(TripAgentConstants.AGENT_ID)
                    .name(TripAgentConstants.AGENT_ID)
                    .description("差旅行程规划智能体")
                    .build();

            ReActAgent agent = new ReActAgent(card);
            String systemPrompt = SystemPromptBuilder.build(
                    TripAgentConstants.remoteHotelToolName(),
                    TripAgentConstants.remoteTaskCollectorToolName());
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
                    .maxIterations(maxIterations)
                    .build()
                    .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
            agent.configure(config);
            return agent;
        }
    }
}

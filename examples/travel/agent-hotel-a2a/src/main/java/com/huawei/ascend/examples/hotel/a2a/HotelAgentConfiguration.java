/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import com.huawei.ascend.examples.hotel.HotelPlanningAgent;
import com.huawei.ascend.examples.hotel.LlmConfig;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    /**
     * Process-local memory backend, active when {@code hotel-agent.memory.provider}
     * is unset or set to {@code in-memory}. Mutual exclusivity with the mem0
     * backend is enforced by both beans gating on the same property — relying on
     * {@code @ConditionalOnMissingBean} inside the same {@code @Configuration}
     * class is order-sensitive and registered both beans when the property was
     * set to {@code mem0}. The concrete return type is preserved so
     * {@link HotelMemoryDebugController} can inject the same bean for inspection.
     */
    @Bean
    @ConditionalOnProperty(name = "hotel-agent.memory.provider", havingValue = "in-memory", matchIfMissing = true)
    HotelInMemoryMemoryProvider hotelInMemoryMemoryProvider() {
        return new HotelInMemoryMemoryProvider();
    }

    /**
     * Mem0 OSS REST backend, opt-in via YAML:
     * <pre>
     * hotel-agent:
     *   memory:
     *     provider: mem0
     *     mem0:
     *       base-url: http://mem0-server:8000
     *       api-key: ${MEM0_API_KEY:}
     *       infer-on-save: false
     * </pre>
     * Only the OSS contract is supported here; Mem0 Platform deployments must
     * supply their own {@link MemoryProvider} bean.
     */
    @Bean
    @ConditionalOnProperty(name = "hotel-agent.memory.provider", havingValue = "mem0")
    MemoryProvider hotelMem0MemoryProvider(
            @Value("${hotel-agent.memory.mem0.base-url:http://localhost:8000}") String baseUrl,
            @Value("${hotel-agent.memory.mem0.api-key:}") String apiKey,
            @Value("${hotel-agent.memory.mem0.infer-on-save:false}") boolean inferOnSave) {
        return new HotelMem0MemoryProvider(baseUrl, apiKey, inferOnSave);
    }

    @Bean
    AgentRuntimeHandler hotelAgentHandler(HotelPlanningAgent agent, ObjectProvider<MemoryProvider> memoryProvider) {
        return new HotelAgentHandler(AGENT_ID, agent, memoryProvider.getIfAvailable());
    }

    @Bean
    AgentCard hotelAgentCard() {
        // Hotel handler completes one ReAct loop and returns the final markdown as a
        // single AgentExecutionResult, so streaming would be a protocol lie; push
        // notifications are not wired by this host. Output is markdown text only —
        // the handler never emits artifacts. Provider URL is left blank so the
        // AgentCardController rewrites it from public-base-url (or the request) at
        // serve time and the published card never leaks a hardcoded host.
        return AgentCard.builder()
                .name(AGENT_ID)
                .description("Corporate-travel hotel planning sub-agent. Given a destination city, "
                        + "check-in / check-out dates and optional star / price / brand filters, "
                        + "returns a markdown comparison of recommended hotels with rooms and prices.")
                .version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", ""))
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
                                .tags(List.of("hotel", "search", "travel", "corporate-travel"))
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
                                .tags(List.of("hotel", "detail", "travel"))
                                .examples(List.of(
                                        "查一下 hotel id BJ-001 的详细信息",
                                        "Show room offers for hotel id SHA-042"))
                                .inputModes(List.of("text"))
                                .outputModes(List.of("text"))
                                .build()))
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();
    }
}

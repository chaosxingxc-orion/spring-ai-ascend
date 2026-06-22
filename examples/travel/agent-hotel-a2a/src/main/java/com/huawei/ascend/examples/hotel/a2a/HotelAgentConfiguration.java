/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import com.huawei.ascend.examples.hotel.HotelPlanningAgent;
import com.huawei.ascend.examples.hotel.LlmConfig;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
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
}
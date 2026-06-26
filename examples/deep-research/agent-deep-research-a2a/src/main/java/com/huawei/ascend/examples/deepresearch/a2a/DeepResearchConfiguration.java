/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import com.huawei.ascend.agentsdk.factory.AgentFactory;
import com.huawei.ascend.examples.deepresearch.DeepResearchAgentSpecMaterializer;
import com.huawei.ascend.examples.deepresearch.DeepResearchConstants;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenCheckpointerConfigurer;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenDeepAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DeepResearchConfiguration {

    @Bean
    Checkpointer deepResearchCheckpointer() {
        return OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
    }

    @Bean
    @ConditionalOnProperty(name = "deep-research.memory.provider", havingValue = "in-memory")
    MemoryProvider deepResearchInMemoryMemoryProvider() {
        return new DeepResearchInMemoryMemoryProvider();
    }

    @Bean
    @ConditionalOnProperty(name = "deep-research.memory.provider", havingValue = "mem0")
    MemoryProvider deepResearchMem0MemoryProvider(
            @Value("${deep-research.memory.mem0.base-url:http://7.209.189.82:8000}") String baseUrl,
            @Value("${deep-research.memory.mem0.api-key:}") String apiKey,
            @Value("${deep-research.memory.mem0.infer-on-save:false}") boolean inferOnSave) {
        return new DeepResearchMem0MemoryProvider(baseUrl, apiKey, inferOnSave);
    }

    @Bean
    AgentRuntimeHandler deepResearchAgentHandler(ObjectProvider<MemoryProvider> memoryProvider) {
        Path yamlPath = DeepResearchAgentSpecMaterializer.materializeProdYaml();
        return new DeepResearchHandler(yamlPath, memoryProvider.getIfAvailable());
    }

    static class DeepResearchHandler extends OpenJiuwenDeepAgentRuntimeHandler {

        private final Path yamlPath;
        private final MemoryProvider memoryProvider;

        DeepResearchHandler(Path yamlPath, MemoryProvider memoryProvider) {
            super(DeepResearchConstants.AGENT_ID);
            this.yamlPath = Objects.requireNonNull(yamlPath, "yamlPath");
            this.memoryProvider = memoryProvider;
        }

        @Override
        protected DeepAgent createOpenJiuwenDeepAgent(AgentExecutionContext context) {
            return AgentFactory.toDeepAgent(yamlPath);
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            if (memoryProvider == null) {
                return List.of();
            }
            return List.of(openJiuwenExternalMemoryRail(context, memoryProvider));
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.a2a;

import com.huawei.ascend.examples.deepresearch.verify.client.TavilySearchClient;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenCheckpointerConfigurer;
import com.openjiuwen.core.session.checkpointer.Checkpointer;

import java.nio.file.Path;
import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class VerifyAgentConfiguration {

    @Bean
    Checkpointer verifyCheckpointer() {
        return OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
    }

    @Bean
    Path verifyAgentYamlPath(@Value("${verify-agent.yaml-path}") String yamlClasspath) {
        return ClasspathYamlExtractor.extract(yamlClasspath);
    }

    @Bean
    OpenJiuwenAgentRuntimeHandler verifyAgentHandler(Path verifyAgentYamlPath,
            @Value("${verify-agent.tavily-api-key:}") String tavilyApiKey) {
        // Inject Tavily API key into the tool's static holder
        if (tavilyApiKey != null && !tavilyApiKey.isBlank()) {
            TavilySearchClient.setApiKey(tavilyApiKey);
        }
        return new VerifyAgentHandler(verifyAgentYamlPath);
    }

    @Bean
    org.a2aproject.sdk.spec.AgentCard verifyAgentCard() {
        return org.a2aproject.sdk.spec.AgentCard.builder()
                .name("verify-agent")
                .description("Deep research claim verification agent — "
                        + "cross-source fact checking with anti-confirmation-bias methodology. "
                        + "Supports numeric, categorical, temporal, and qualitative claims.")
                .version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", ""))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .extendedAgentCard(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(
                        AgentSkill.builder()
                                .id("verify_claim")
                                .name("Verify a claim against sources")
                                .description("Cross-reference a factual claim against provided "
                                        + "and searched sources. Supports numeric (pricing, "
                                        + "context-length, QPS), categorical (features), temporal "
                                        + "(dates, versions), and qualitative (performance, "
                                        + "capability descriptions) claim types. Returns a verdict "
                                        + "(support/contradict/insufficient/partial) with "
                                        + "confidence score and cited excerpts.")
                                .tags(List.of("verify", "fact-check", "research", "deep-research"))
                                .examples(List.of(
                                        "Verify: 火山方舟豆包 Pro 4K 输入价格为 0.0008 元/千 token",
                                        "Check if Qwen-Max context window is 32K tokens",
                                        "Fact-check: DeepSeek V3 supports Function Calling"))
                                .inputModes(List.of("text"))
                                .outputModes(List.of("text"))
                                .build()))
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();
    }
}

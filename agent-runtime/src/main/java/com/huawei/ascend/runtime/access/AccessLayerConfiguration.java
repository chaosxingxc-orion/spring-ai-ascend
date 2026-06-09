package com.huawei.ascend.runtime.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huawei.ascend.runtime.access.api.NotificationPort;
import com.huawei.ascend.runtime.access.a2a.A2aAccessProperties;
import com.huawei.ascend.runtime.access.a2a.A2aWellKnownAgentCardController;
import com.huawei.ascend.runtime.access.a2a.A2aOutputMapper;
import com.huawei.ascend.runtime.access.a2a.A2aOutputRegistry;
import com.huawei.ascend.runtime.access.a2a.DefaultNotificationPort;
import com.huawei.ascend.runtime.access.a2a.A2aJsonRpcController;
import com.huawei.ascend.runtime.access.a2a.A2aJsonRpcHandler;
import com.huawei.ascend.runtime.engine.spi.AgentCardProvider;
import java.util.List;
import java.util.Optional;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(A2aAccessProperties.class)
public class AccessLayerConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ObjectMapper accessObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    @ConditionalOnMissingBean(AgentCard.class)
    AgentCard a2aAgentCard(Optional<AgentCardProvider> agentCardProvider) {
        if (agentCardProvider.isPresent()) {
            return agentCardProvider.get().agentCard();
        }
        AgentCapabilities capabilities = AgentCapabilities.builder()
                .streaming(true)
                .pushNotifications(true)
                .extendedAgentCard(false)
                .build();
        return AgentCard.builder()
                .name("spring-ai-ascend-agent")
                .description("A2A access layer for spring-ai-ascend agent runtime.")
                .url("/a2a")
                .version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:8080"))
                .capabilities(capabilities)
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text", "artifact"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    A2aWellKnownAgentCardController a2aWellKnownAgentCardController(
            AgentCard agentCard,
            A2aAccessProperties properties) {
        return new A2aWellKnownAgentCardController(agentCard, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    A2aOutputRegistry a2aOutputRegistry() {
        return new A2aOutputRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    A2aOutputMapper a2aOutputMapper() {
        return new A2aOutputMapper();
    }

    @Bean
    @ConditionalOnMissingBean(NotificationPort.class)
    NotificationPort notificationPort(A2aOutputMapper outputMapper, A2aOutputRegistry outputRegistry) {
        return new DefaultNotificationPort(outputMapper, outputRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    A2aJsonRpcHandler a2aJsonRpcHandler(
            AccessSubmissionService submissionService,
            A2aOutputRegistry outputRegistry,
            ObjectMapper objectMapper,
            A2aAccessProperties properties) {
        return new A2aJsonRpcHandler(submissionService, outputRegistry, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    A2aJsonRpcController a2aJsonRpcController(
            A2aJsonRpcHandler handler,
            A2aOutputRegistry outputRegistry) {
        return new A2aJsonRpcController(handler, outputRegistry);
    }
}

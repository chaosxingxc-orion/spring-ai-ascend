package com.huawei.ascend.runtime.access.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huawei.ascend.runtime.access.api.NotificationPort;
import com.huawei.ascend.runtime.access.core.AccessSubmissionService;
import com.huawei.ascend.runtime.access.protocol.a2a.A2aAccessProperties;
import com.huawei.ascend.runtime.access.protocol.a2a.A2aWellKnownAgentCardController;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.A2aOutputMapper;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.A2aOutputRegistry;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.DefaultNotificationPort;
import com.huawei.ascend.runtime.access.protocol.a2a.ingress.A2aJsonRpcController;
import com.huawei.ascend.runtime.access.protocol.a2a.jsonrpc.A2aJsonRpcHandler;
import com.huawei.ascend.runtime.access.protocol.async.AsyncQueueIngressAdapter;
import com.huawei.ascend.runtime.access.protocol.async.AsyncQueueIngressPort;
import com.huawei.ascend.runtime.access.protocol.async.AsyncQueueReplySink;
import com.huawei.ascend.runtime.access.protocol.async.DefaultAsyncQueueReplySink;
import com.huawei.ascend.runtime.bootstrap.AbstractRuntimeAgentHandler;
import com.huawei.ascend.runtime.queue.QueueManager;
import java.util.List;
import java.util.Optional;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
    AgentCard a2aAgentCard(Optional<AbstractRuntimeAgentHandler> runtimeAgent) {
        if (runtimeAgent.isPresent()) {
            return runtimeAgent.get().agentCard();
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
    A2aWellKnownAgentCardController a2aWellKnownAgentCardController(AgentCard agentCard) {
        return new A2aWellKnownAgentCardController(agentCard);
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

    @Bean
    @ConditionalOnMissingBean(AsyncQueueIngressPort.class)
    AsyncQueueIngressPort asyncQueueIngressPort(
            A2aJsonRpcHandler handler,
            Optional<AsyncQueueReplySink> replySink) {
        return new AsyncQueueIngressAdapter(handler, replySink);
    }

    @Bean
    @ConditionalOnBean(QueueManager.class)
    @ConditionalOnMissingBean(AsyncQueueReplySink.class)
    AsyncQueueReplySink asyncQueueReplySink(QueueManager queueManager) {
        return new DefaultAsyncQueueReplySink(queueManager);
    }
}

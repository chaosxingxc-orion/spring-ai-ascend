package com.huawei.ascend.service.access.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huawei.ascend.service.access.protocol.a2a.A2aAccessService;
import com.huawei.ascend.service.access.protocol.a2a.A2aEgressAdapter;
import com.huawei.ascend.service.access.protocol.a2a.A2aOutputRegistry;
import com.huawei.ascend.service.access.protocol.a2a.A2aIngressAdapter;
import com.huawei.ascend.service.access.protocol.a2a.A2aOutputSink;
import com.huawei.ascend.service.access.protocol.a2a.DefaultA2aOutputSink;
import com.huawei.ascend.service.access.egress.DefaultEgressQueueRegistry;
import com.huawei.ascend.service.access.egress.DefaultNotificationPort;
import com.huawei.ascend.service.access.egress.EgressAdapter;
import com.huawei.ascend.service.access.egress.EgressDispatcher;
import com.huawei.ascend.service.access.egress.EgressQueueRegistry;
import com.huawei.ascend.service.access.core.AccessGateway;
import com.huawei.ascend.service.access.protocol.async.AsyncEgressAdapter;
import com.huawei.ascend.service.access.protocol.async.AsyncIngressAdapter;
import com.huawei.ascend.service.access.protocol.async.AsyncIngressPort;
import com.huawei.ascend.service.access.protocol.async.AsyncOutputSink;
import com.huawei.ascend.service.access.api.NotificationPort;
import com.huawei.ascend.service.access.core.TaskHandler;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AccessLayerConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ObjectMapper accessObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    @ConditionalOnMissingBean(AgentCard.class)
    AgentCard a2aAgentCard() {
        AgentCapabilities capabilities = AgentCapabilities.builder()
                .streaming(true)
                .pushNotifications(true)
                .extendedAgentCard(false)
                .build();
        return AgentCard.builder()
                .name("spring-ai-ascend-agent")
                .description("A2A access layer for spring-ai-ascend agent service.")
                .url("/a2a/")
                .version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:8080"))
                .capabilities(capabilities)
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text", "artifact"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a/")))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    A2aOutputRegistry a2aOutputRegistry() {
        return new A2aOutputRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(A2aOutputSink.class)
    A2aOutputSink a2aOutputSink(A2aOutputRegistry outputRegistry) {
        return new DefaultA2aOutputSink(outputRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    EgressQueueRegistry egressQueueRegistry() {
        return new DefaultEgressQueueRegistry();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "accessEgressExecutor")
    ExecutorService accessEgressExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    @ConditionalOnMissingBean
    EgressDispatcher egressDispatcher(
            EgressQueueRegistry egressQueueRegistry,
            Collection<EgressAdapter> egressAdapters,
            Executor accessEgressExecutor) {
        return new EgressDispatcher(egressQueueRegistry, egressAdapters, accessEgressExecutor);
    }

    @Bean
    @ConditionalOnMissingBean(NotificationPort.class)
    NotificationPort notificationPort(EgressQueueRegistry egressQueueRegistry) {
        return new DefaultNotificationPort(egressQueueRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(A2aEgressAdapter.class)
    A2aEgressAdapter a2aEgressAdapter(A2aOutputSink outputSink) {
        return new A2aEgressAdapter(outputSink);
    }

    @Bean
    @ConditionalOnBean(AsyncOutputSink.class)
    @ConditionalOnMissingBean(AsyncEgressAdapter.class)
    AsyncEgressAdapter asyncEgressAdapter(AsyncOutputSink outputSink) {
        return new AsyncEgressAdapter(outputSink);
    }

    @Bean
    @ConditionalOnBean(TaskHandler.class)
    @ConditionalOnMissingBean
    AccessGateway accessGateway(TaskHandler taskHandler) {
        return new AccessGateway(taskHandler);
    }

    @Bean
    @ConditionalOnBean(AccessGateway.class)
    @ConditionalOnMissingBean(A2aAccessService.class)
    A2aAccessService a2aAccessService(AccessGateway accessGateway) {
        return new A2aIngressAdapter(accessGateway);
    }

    @Bean
    @ConditionalOnBean(AccessGateway.class)
    @ConditionalOnMissingBean(AsyncIngressPort.class)
    AsyncIngressPort asyncIngressPort(AccessGateway accessGateway) {
        return new AsyncIngressAdapter(accessGateway);
    }

}


package com.huawei.ascend.service.engine.config;

import com.huawei.ascend.service.engine.api.DefaultEngineDispatchApi;
import com.huawei.ascend.service.engine.api.EngineDispatchApi;
import com.huawei.ascend.service.engine.dispatch.AgentHandlerRegistry;
import com.huawei.ascend.service.engine.dispatch.DefaultAgentHandlerRegistry;
import com.huawei.ascend.service.engine.dispatch.EngineDispatcher;
import com.huawei.ascend.service.engine.queue.EngineCommandEventFactory;
import com.huawei.ascend.service.engine.queue.EngineCommandSubscriber;
import com.huawei.ascend.service.engine.queue.InMemoryEngineQueueGateway;
import com.huawei.ascend.service.engine.spi.AccessLayerClient;
import com.huawei.ascend.service.engine.spi.EngineQueueGateway;
import com.huawei.ascend.service.engine.spi.TaskControlClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the engine's core collaborators as beans (design §15). The dispatcher
 * and subscriber are only created once the outbound clients
 * ({@link TaskControlClient}, {@link AccessLayerClient}) are available — those
 * are provided by the task-control and access-layer modules, not the engine
 * itself, so the engine staying dormant when they are absent is intentional.
 */
@Configuration
@EnableConfigurationProperties(EngineProperties.class)
@ConditionalOnProperty(prefix = "agent-service.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentHandlerRegistry agentHandlerRegistry(
            org.springframework.beans.factory.ObjectProvider<com.huawei.ascend.service.engine.spi.AgentHandler> handlers) {
        DefaultAgentHandlerRegistry registry = new DefaultAgentHandlerRegistry();
        // Auto-register every AgentHandler bean by its agentId so framework
        // integrators only need to publish a handler bean to plug in an agent.
        handlers.orderedStream().forEach(handler -> registry.register(handler.agentId(), handler));
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineQueueGateway engineQueueGateway() {
        return new InMemoryEngineQueueGateway();
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineCommandEventFactory engineCommandEventFactory() {
        return new EngineCommandEventFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineDispatchApi engineDispatchApi(EngineCommandEventFactory commandEventFactory,
                                               EngineQueueGateway engineQueueGateway) {
        return new DefaultEngineDispatchApi(commandEventFactory, engineQueueGateway);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({TaskControlClient.class, AccessLayerClient.class})
    public EngineDispatcher engineDispatcher(AgentHandlerRegistry registry,
                                             TaskControlClient taskControlClient,
                                             AccessLayerClient accessLayerClient) {
        return new EngineDispatcher(registry, taskControlClient, accessLayerClient);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EngineDispatcher.class)
    public EngineCommandSubscriber engineCommandSubscriber(EngineQueueGateway gateway, EngineDispatcher dispatcher) {
        EngineCommandSubscriber subscriber = new EngineCommandSubscriber(gateway, dispatcher);
        subscriber.start();
        return subscriber;
    }
}

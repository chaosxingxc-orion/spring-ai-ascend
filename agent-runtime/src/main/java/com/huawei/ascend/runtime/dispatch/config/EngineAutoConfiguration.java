package com.huawei.ascend.runtime.dispatch.config;

import com.huawei.ascend.runtime.dispatch.api.DefaultEngineDispatchApi;
import com.huawei.ascend.runtime.dispatch.api.EngineDispatchApi;
import com.huawei.ascend.runtime.dispatch.command.EngineCommandEventFactory;
import com.huawei.ascend.runtime.dispatch.command.EngineCommandGateway;
import com.huawei.ascend.runtime.dispatch.command.EngineCommandProcessor;
import com.huawei.ascend.runtime.dispatch.command.InternalEngineCommandGateway;
import com.huawei.ascend.runtime.dispatch.dispatch.AgentHandlerRegistry;
import com.huawei.ascend.runtime.dispatch.dispatch.DefaultAgentHandlerRegistry;
import com.huawei.ascend.runtime.dispatch.dispatch.EngineDispatcher;
import com.huawei.ascend.runtime.dispatch.port.AccessLayerClient;
import com.huawei.ascend.runtime.dispatch.port.TaskControlClient;
import com.huawei.ascend.runtime.queue.QueueManager;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the engine's core collaborators as Spring beans. Task-control and
 * access-layer clients are provided by the service bootstrap configuration so
 * the full agent-service runtime starts through one auto-configuration path.
 */
@Configuration
@EnableConfigurationProperties(EngineProperties.class)
@ConditionalOnProperty(prefix = "agent-service.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentHandlerRegistry agentHandlerRegistry(
            org.springframework.beans.factory.ObjectProvider<com.huawei.ascend.runtime.dispatch.spi.AgentHandler> handlers) {
        DefaultAgentHandlerRegistry registry = new DefaultAgentHandlerRegistry();
        // Auto-register every AgentHandler bean by its agentId so framework
        // integrators only need to publish a handler bean to plug in an agent.
        handlers.orderedStream().forEach(handler -> registry.register(handler.agentId(), handler));
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineCommandGateway engineCommandGateway(QueueManager queueManager) {
        return new InternalEngineCommandGateway(queueManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineCommandEventFactory engineCommandEventFactory() {
        return new EngineCommandEventFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineDispatchApi engineDispatchApi(EngineCommandEventFactory commandEventFactory,
                                               EngineCommandGateway engineCommandGateway) {
        return new DefaultEngineDispatchApi(commandEventFactory, engineCommandGateway);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "engineExecutionExecutor")
    public ExecutorService engineExecutionExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineDispatcher engineDispatcher(AgentHandlerRegistry registry,
                                             TaskControlClient taskControlClient,
                                             AccessLayerClient accessLayerClient) {
        return new EngineDispatcher(registry, taskControlClient, accessLayerClient);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    public EngineCommandProcessor engineCommandProcessor(
            EngineCommandGateway gateway,
            EngineDispatcher dispatcher,
            @Qualifier("engineExecutionExecutor") Executor engineExecutionExecutor) {
        return new EngineCommandProcessor(gateway, dispatcher, engineExecutionExecutor);
    }
}

package com.huawei.ascend.runtime.dispatch.config;

import com.huawei.ascend.runtime.dispatch.api.DefaultEngineDispatchApi;
import com.huawei.ascend.runtime.dispatch.api.EngineDispatchApi;
import com.huawei.ascend.runtime.dispatch.command.EngineCommandEventFactory;
import com.huawei.ascend.runtime.dispatch.command.EngineCommandGateway;
import com.huawei.ascend.runtime.dispatch.command.EngineCommandProcessor;
import com.huawei.ascend.runtime.dispatch.command.InternalEngineCommandGateway;
import com.huawei.ascend.runtime.dispatch.dispatch.EngineDispatcher;
import com.huawei.ascend.runtime.engine.registry.AgentDriverRegistry;
import com.huawei.ascend.runtime.engine.registry.DefaultAgentDriverRegistry;
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
    public AgentDriverRegistry agentDriverRegistry(
            org.springframework.beans.factory.ObjectProvider<com.huawei.ascend.runtime.engine.spi.AgentDriver> drivers) {
        DefaultAgentDriverRegistry registry = new DefaultAgentDriverRegistry();
        // Auto-register every AgentDriver bean so framework integrators only need to
        // publish a driver bean to plug an agent into the neutral execution core.
        drivers.orderedStream().forEach(registry::register);
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
    public EngineDispatcher engineDispatcher(AgentDriverRegistry registry,
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

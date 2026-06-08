package com.huawei.ascend.runtime.engine;

import com.huawei.ascend.runtime.engine.api.DefaultEngineExecutionApi;
import com.huawei.ascend.runtime.engine.api.EngineExecutionApi;
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
    public AgentRuntimeHandlerRegistry agentHandlerRegistry(
            org.springframework.beans.factory.ObjectProvider<com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler> handlers) {
        DefaultAgentRuntimeHandlerRegistry registry = new DefaultAgentRuntimeHandlerRegistry();
        // Auto-register every AgentRuntimeHandler bean by its agentId so framework
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
    public EngineExecutionApi engineDispatchApi(EngineCommandEventFactory commandEventFactory,
                                               EngineCommandGateway engineCommandGateway) {
        return new DefaultEngineExecutionApi(commandEventFactory, engineCommandGateway);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "engineExecutionExecutor")
    public ExecutorService engineExecutionExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineDispatcher engineDispatcher(AgentRuntimeHandlerRegistry registry,
                                             TaskControlClient taskControlClient) {
        return new EngineDispatcher(registry, taskControlClient);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    public EngineWorker engineCommandProcessor(
            EngineCommandGateway gateway,
            EngineDispatcher dispatcher,
            @Qualifier("engineExecutionExecutor") Executor engineExecutionExecutor) {
        return new EngineWorker(gateway, dispatcher, engineExecutionExecutor);
    }
}

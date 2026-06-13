package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.a2a.RemoteAgentCardCache;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the runtime lifecycle: server executor pool, readiness gate, handler
 * lifecycle orchestration, and the optional actuator health contribution.
 */
@Configuration(proxyBeanMethods = false)
class RuntimeLifecycleConfiguration {

    @Bean @ConditionalOnMissingBean
    public RuntimeAutoConfiguration.A2aServerExecutor a2aServerExecutor() {
        return new RuntimeAutoConfiguration.A2aServerExecutor();
    }

    @Bean @ConditionalOnMissingBean
    public RuntimeReadiness runtimeReadiness() { return new RuntimeReadiness(); }

    @Bean @ConditionalOnMissingBean
    public AgentRuntimeLifecycle agentRuntimeLifecycle(ObjectProvider<AgentRuntimeHandler> handlers,
            RuntimeReadiness readiness) {
        return new AgentRuntimeLifecycle(handlers.orderedStream().toList(), readiness);
    }

    /**
     * Isolated in a nested class because actuator is an optional dependency: a bean
     * method on the outer class whose signature mentions {@link AgentRuntimeHealthIndicator}
     * (which implements HealthIndicator) makes reflective introspection of the whole
     * auto-configuration throw NoClassDefFoundError in hosts without actuator. The
     * condition is evaluated from class metadata, so the nested class is never loaded
     * unless HealthIndicator is present.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    static class HealthIndicatorConfiguration {

        @Bean @ConditionalOnMissingBean
        AgentRuntimeHealthIndicator agentRuntimeHealthIndicator(ObjectProvider<AgentRuntimeHandler> handlers,
                RuntimeReadiness readiness, ObjectProvider<RemoteAgentCardCache> remoteAgentCardCache) {
            return new AgentRuntimeHealthIndicator(handlers.orderedStream().toList(), readiness,
                    remoteAgentCardCache.getIfAvailable());
        }
    }
}

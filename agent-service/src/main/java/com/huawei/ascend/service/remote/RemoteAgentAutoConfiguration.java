package com.huawei.ascend.service.remote;

import com.huawei.ascend.runtime.boot.RuntimeAutoConfiguration.A2aServerExecutor;
import com.huawei.ascend.runtime.engine.a2a.A2aAgentExecutor;
import com.huawei.ascend.runtime.engine.a2a.A2aRemoteAgentOutboundAdapter;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenRemoteToolInstaller;
import com.huawei.ascend.runtime.engine.service.RemoteAgentInvocationService;
import com.huawei.ascend.runtime.engine.spi.RemoteAgentCatalogPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Service-plane wiring of the remote-agent topology: discovery (the catalog implementing the
 * runtime's {@link RemoteAgentCatalogPort}), its refresh loop, and the runtime-side execution
 * beans that consume the resolved view. Active only when at least one remote agent URL is
 * configured; grouping everything under one guarded configuration keeps the
 * {@code @ConditionalOnProperty} guard in a single place.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "agent-runtime.remote-agents.0", name = "url")
@EnableConfigurationProperties(RemoteAgentProperties.class)
public class RemoteAgentAutoConfiguration {

    @Bean @ConditionalOnMissingBean
    public RemoteAgentCatalogPort remoteAgentCatalog(RemoteAgentProperties properties) {
        return new RemoteAgentCatalog(properties.urls());
    }

    @Bean @ConditionalOnMissingBean
    public A2aRemoteAgentOutboundAdapter a2aRemoteAgentOutboundAdapter(RemoteAgentCatalogPort catalog) {
        return new A2aRemoteAgentOutboundAdapter(catalog);
    }

    @Bean @ConditionalOnMissingBean
    public RemoteAgentInvocationService remoteAgentInvocationService(A2aRemoteAgentOutboundAdapter outboundAdapter) {
        return new RemoteAgentInvocationService(outboundAdapter);
    }

    @Bean @ConditionalOnMissingBean
    public A2aAgentExecutor.RemoteSupport a2aRemoteSupport(RemoteAgentInvocationService invocationService) {
        return new A2aAgentExecutor.RemoteSupport(invocationService);
    }

    @Bean @ConditionalOnMissingBean
    public RemoteAgentCatalogRefresher remoteAgentCatalogRefresher(RemoteAgentCatalogPort catalog,
            A2aServerExecutor executor) {
        return new RemoteAgentCatalogRefresher(catalog, executor.executor());
    }

    /**
     * Isolated in a nested class because the openJiuwen framework is an optional dependency of
     * agent-runtime: a bean method on the outer class whose signature mentions openJiuwen-typed
     * classes makes reflective introspection of the whole auto-configuration throw
     * NoClassDefFoundError in hosts without the framework. The condition is evaluated from class
     * metadata, so the nested class is never loaded unless openJiuwen is present.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.openjiuwen.core.singleagent.BaseAgent")
    static class OpenJiuwenRemoteToolConfiguration {

        @Bean @ConditionalOnMissingBean
        OpenJiuwenRemoteToolInstaller openJiuwenRemoteToolInstaller(RemoteAgentCatalogPort catalog,
                ObjectProvider<OpenJiuwenAgentRuntimeHandler> handlers) {
            OpenJiuwenRemoteToolInstaller installer =
                    new OpenJiuwenRemoteToolInstaller(catalog::availableToolSpecs);
            handlers.orderedStream().forEach(handler -> handler.setRuntimeToolInstaller(installer));
            return installer;
        }
    }
}

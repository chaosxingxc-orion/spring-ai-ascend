package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.a2a.client.RemoteAgentCardCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenJiuwen-specific remote-tool wiring, kept separate from the generic
 * remote-agent auto-configuration so the runtime auto-configuration does
 * not depend on OpenJiuwen types.
 *
 * <p>Activated only when both remote agents are configured and OpenJiuwen
 * is on the classpath. Creates a single {@link OpenJiuwenRemoteToolInstaller}
 * backed by the {@link RemoteAgentCardCache} and injects it into every
 * {@link OpenJiuwenAgentRuntimeHandler} bean.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "agent-runtime.remote-agents.0", name = "url")
@ConditionalOnClass(name = "com.openjiuwen.core.singleagent.BaseAgent")
public class OpenJiuwenRemoteToolAutoConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(OpenJiuwenRemoteToolAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OpenJiuwenRemoteToolInstaller openJiuwenRemoteToolInstaller(
            RemoteAgentCardCache cardCache,
            ObjectProvider<OpenJiuwenAgentRuntimeHandler> handlers) {
        OpenJiuwenRemoteToolInstaller installer =
                new OpenJiuwenRemoteToolInstaller(cardCache::availableToolSpecs);
        int count = 0;
        for (OpenJiuwenAgentRuntimeHandler handler : handlers.orderedStream().toList()) {
            handler.setRuntimeToolInstaller(installer);
            count++;
            LOG.info("installed remote tool installer into openjiuwen handler agentId={}", handler.agentId());
        }
        if (count == 0) {
            LOG.warn("remote tool installer created but no OpenJiuwenAgentRuntimeHandler beans found");
        }
        return installer;
    }
}

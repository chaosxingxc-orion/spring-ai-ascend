package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.AgentCardProvider;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import org.a2aproject.sdk.spec.AgentCard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A2A agent-card auto-configuration — isolated in the {@code engine.a2a}
 * package so the boot wiring layer does not depend on A2A properties types.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentCardProperties.class)
public class A2aCardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentCard a2aAgentCard(ObjectProvider<AgentCardProvider> cardProviders,
                                   ObjectProvider<AgentRuntimeHandler> handlers,
                                   AgentCardProperties cardProperties) {
        var cp = cardProviders.getIfAvailable();
        if (cp != null) {
            return cp.agentCard();
        }
        String name = handlers.orderedStream().map(AgentRuntimeHandler::agentId).findFirst().orElse("agent");
        if (cardProperties.hasExplicitName()) {
            name = cardProperties.getName();
        }
        return cardProperties.createAgentCard(name);
    }
}

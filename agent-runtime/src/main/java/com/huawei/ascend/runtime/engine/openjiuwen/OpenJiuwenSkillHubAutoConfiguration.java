package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.spi.SkillHubProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-wires runtime-neutral SkillHub providers into OpenJiuwen handlers when
 * both are present.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.openjiuwen.core.singleagent.BaseAgent")
@ConditionalOnBean(SkillHubProvider.class)
public class OpenJiuwenSkillHubAutoConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(OpenJiuwenSkillHubAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OpenJiuwenSkillHubInstaller openJiuwenSkillHubInstaller(
            SkillHubProvider skillHubProvider,
            ObjectProvider<OpenJiuwenAgentRuntimeHandler> handlers) {
        OpenJiuwenSkillHubInstaller installer = new OpenJiuwenSkillHubInstaller(skillHubProvider);
        int count = 0;
        for (OpenJiuwenAgentRuntimeHandler handler : handlers.orderedStream().toList()) {
            handler.setSkillHubInstaller(installer);
            count++;
            LOG.info("installed skillhub installer into openjiuwen handler agentId={}", handler.agentId());
        }
        if (count == 0) {
            LOG.warn("skillhub installer created but no OpenJiuwenAgentRuntimeHandler beans found");
        }
        return installer;
    }
}

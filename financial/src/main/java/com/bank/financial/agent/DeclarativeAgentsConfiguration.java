package com.bank.financial.agent;

import com.bank.financial.kit.DeclarativeFinancialAgentHandler;
import com.bank.financial.kit.spec.AgentDefinition;
import com.bank.financial.kit.spec.AgentDefinitionLoader;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Discovers every {@code agents/*.yaml} on the classpath and registers one
 * served A2A handler bean per file. This is what makes "write a YAML → get a
 * running agent" true end to end: the business developer never writes a Spring
 * bean or a handler class.
 *
 * <p>Runs as a {@link BeanDefinitionRegistryPostProcessor} so the handler beans
 * exist before the runtime's auto-configuration scans for them.
 */
@Configuration(proxyBeanMethods = false)
public class DeclarativeAgentsConfiguration implements BeanDefinitionRegistryPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DeclarativeAgentsConfiguration.class);
    private static final String AGENTS_GLOB = "classpath*:agents/*.yaml";

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(AGENTS_GLOB);
        } catch (IOException e) {
            LOG.warn("[financial] could not scan {} — {}", AGENTS_GLOB, e.getMessage());
            return;
        }
        int count = 0;
        for (Resource r : resources) {
            try {
                AgentDefinition def = AgentDefinitionLoader.loadStream(r.getInputStream());
                AbstractBeanDefinition bd = BeanDefinitionBuilder
                        .genericBeanDefinition(OpenJiuwenAgentRuntimeHandler.class,
                                () -> new DeclarativeFinancialAgentHandler(def))
                        .getBeanDefinition();
                registry.registerBeanDefinition("declarativeAgent_" + def.id(), bd);
                LOG.info("[financial] registered declarative agent '{}' from {}", def.id(), r.getFilename());
                count++;
            } catch (Exception e) {
                LOG.error("[financial] failed to register agent from {} — {}", r.getFilename(), e.getMessage());
            }
        }
        LOG.info("[financial] {} declarative agent(s) registered", count);
    }
}

package com.bank.financial.agent;

import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hosts exactly ONE financial agent in this runtime instance — the platform
 * requires a single {@code AgentRuntimeHandler} bean. Which agent is served is
 * chosen by {@code financial.agent} (env {@code FINANCIAL_AGENT}); run multiple
 * agents by deploying multiple instances, each with a different value.
 *
 * <p>Resolves the id through {@link FinancialAgentRegistry} (Java agents or a
 * {@code agents/<id>.yaml}).
 */
@Configuration(proxyBeanMethods = false)
public class FinancialAgentServerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialAgentServerConfiguration.class);

    @Bean
    OpenJiuwenAgentRuntimeHandler hostedAgent(
            @Value("${financial.agent:retail-wealth-advisor}") String agentId) {
        LOG.info("[financial] hosting agent '{}' (override via financial.agent / FINANCIAL_AGENT)", agentId);
        return FinancialAgentRegistry.create(agentId);
    }
}

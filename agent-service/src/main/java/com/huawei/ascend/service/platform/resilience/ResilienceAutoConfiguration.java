package com.huawei.ascend.service.platform.resilience;

import com.huawei.ascend.service.runtime.resilience.DefaultSkillResilienceContract;
import com.huawei.ascend.service.runtime.resilience.YamlSkillCapacityRegistry;
import com.huawei.ascend.service.runtime.resilience.spi.ResilienceContract;
import com.huawei.ascend.service.runtime.resilience.spi.SkillCapacityRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the W1.x Phase 9 ResilienceContract surface (Rule R-K (legacy 41.b) / ADR-0070):
 * {@link YamlSkillCapacityRegistry} loads {@code docs/governance/skill-capacity.yaml},
 * and {@link DefaultSkillResilienceContract} returns {@code SkillResolution} via the
 * registry.
 *
 * <p>The path is configurable via {@code app.resilience.skill-capacity-path}; the
 * default is correct for unit-test launches from the repo root and for the packaged
 * jar (the YAML is shipped under the same path).
 */
@Configuration(proxyBeanMethods = false)
public class ResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SkillCapacityRegistry.class)
    public SkillCapacityRegistry skillCapacityRegistry(
            @Value("${app.resilience.skill-capacity-path:docs/governance/skill-capacity.yaml}")
                    String yamlPath) {
        return new YamlSkillCapacityRegistry(yamlPath);
    }

    @Bean
    @ConditionalOnMissingBean(ResilienceContract.class)
    public ResilienceContract defaultSkillResilienceContract(SkillCapacityRegistry registry) {
        return new DefaultSkillResilienceContract(registry);
    }
}

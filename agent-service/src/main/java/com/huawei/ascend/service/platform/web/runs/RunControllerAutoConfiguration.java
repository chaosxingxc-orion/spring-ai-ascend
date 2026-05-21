package com.huawei.ascend.service.platform.web.runs;

import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires a default {@link RunRepository} bean for the L1 HTTP path. In
 * {@code dev} posture, {@link InMemoryRunRegistry} is registered when no other
 * {@link RunRepository} bean exists. {@code research}/{@code prod} require a
 * durable repository bean (W2 — {@code PostgresRunRepository}); until that
 * lands, {@code PostureBootGuard} aborts startup in those postures.
 *
 * <p>Note: {@link InMemoryRunRegistry} itself calls
 * {@code AppPostureGate.requireDevForInMemoryComponent} in its constructor, so
 * a misconfigured non-dev posture also fails at bean creation time — Rule 6
 * defence-in-depth.
 */
@Configuration(proxyBeanMethods = false)
public class RunControllerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RunRepository.class)
    @ConditionalOnProperty(prefix = "app", name = "posture", havingValue = "dev", matchIfMissing = true)
    public RunRepository inMemoryRunRepository() {
        return new InMemoryRunRegistry();
    }
}

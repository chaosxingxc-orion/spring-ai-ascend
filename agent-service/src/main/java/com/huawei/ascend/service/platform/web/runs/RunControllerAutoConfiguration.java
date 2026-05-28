package com.huawei.ascend.service.platform.web.runs;

import com.huawei.ascend.engine.orchestration.spi.Orchestrator;
import com.huawei.ascend.service.runtime.capability.CapabilityRegistry;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the default {@link RunRepository} and the {@link AsyncRunDispatcher}
 * for the L1 HTTP path.
 *
 * <p>In {@code dev} posture, {@link InMemoryRunRegistry} is registered when no
 * other {@link RunRepository} bean exists. {@code research}/{@code prod} require
 * a durable repository bean (W2 — {@code PostgresRunRepository}); until that
 * lands, {@code PostureBootGuard} aborts startup in those postures.
 *
 * <p>Dispatcher selection (declared in this single class so
 * {@link ConditionalOnMissingBean} evaluates deterministically against the
 * earlier {@code @Bean} method — the order-independent form the rc4 regression
 * established over a {@code @Component}-level conditional): {@code dev} posture
 * gets {@link OrchestratingAsyncRunDispatcher} (real execution via the
 * in-memory orchestrator); any posture with no dispatcher otherwise registered
 * falls back to {@link NoOpAsyncRunDispatcher}. A test may still override either
 * by declaring its own {@code @Primary AsyncRunDispatcher}.
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

    @Bean
    @ConditionalOnProperty(prefix = "app", name = "posture", havingValue = "dev", matchIfMissing = true)
    public AsyncRunDispatcher orchestratingAsyncRunDispatcher(RunRepository runRepository,
                                                              Orchestrator orchestrator,
                                                              CapabilityRegistry capabilityRegistry) {
        return new OrchestratingAsyncRunDispatcher(runRepository, orchestrator, capabilityRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(AsyncRunDispatcher.class)
    public AsyncRunDispatcher noOpAsyncRunDispatcher() {
        return new NoOpAsyncRunDispatcher();
    }
}

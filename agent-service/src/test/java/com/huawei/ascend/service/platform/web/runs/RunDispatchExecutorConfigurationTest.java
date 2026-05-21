package com.huawei.ascend.service.platform.web.runs;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class RunDispatchExecutorConfigurationTest {

    @Test
    void registersExecutorMetricsAndRejectionCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RunDispatchProperties properties = new RunDispatchProperties();
        RunDispatchExecutorConfiguration configuration = new RunDispatchExecutorConfiguration(registry, properties);

        Executor executor = configuration.runDispatchExecutor();
        assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);

        assertThat(registry.find("springai_ascend_run_dispatch_rejected_total").counter()).isNotNull();
        assertThat(registry.find("springai_ascend_run_dispatch_rejected_total")
                .tag("policy", "CALLER_RUNS").counter()).isNotNull();
        assertThat(registry.find("springai_ascend_run_dispatch_executor_active_threads").gauge()).isNotNull();
        assertThat(registry.find("springai_ascend_run_dispatch_executor_queue_depth").gauge()).isNotNull();

        ((ThreadPoolExecutor) executor).shutdownNow();
    }

    @Test
    void abortPolicyRejectsAndIncrementsCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RunDispatchProperties properties = new RunDispatchProperties();
        properties.setRejectionPolicy(RunDispatchProperties.RejectionPolicy.ABORT);
        RunDispatchExecutorConfiguration configuration = new RunDispatchExecutorConfiguration(registry, properties);

        ThreadPoolExecutor executor = (ThreadPoolExecutor) configuration.runDispatchExecutor();
        executor.shutdown();

        try {
            executor.execute(() -> {
            });
        } catch (RejectedExecutionException expected) {
            // expected for ABORT policy
        }

        assertThat(registry.get("springai_ascend_run_dispatch_rejected_total").counter().count())
                .isEqualTo(1.0d);
        assertThat(registry.find("springai_ascend_run_dispatch_rejected_total")
                .tag("policy", "ABORT").counter()).isNotNull();
        executor.shutdownNow();
    }

    @Test
    void threadPoolSizesRespectConfiguredBounds() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RunDispatchProperties properties = new RunDispatchProperties();
        properties.setCoreThreads(8);
        properties.setMaxThreads(4);
        properties.setQueueCapacity(0);

        ThreadPoolExecutor executor = (ThreadPoolExecutor) new RunDispatchExecutorConfiguration(registry, properties)
                .runDispatchExecutor();

        assertThat(executor.getCorePoolSize()).isEqualTo(8);
        assertThat(executor.getMaximumPoolSize()).isEqualTo(8);
        assertThat(executor.getQueue().remainingCapacity() + executor.getQueue().size()).isEqualTo(1);
        executor.shutdownNow();
    }
}

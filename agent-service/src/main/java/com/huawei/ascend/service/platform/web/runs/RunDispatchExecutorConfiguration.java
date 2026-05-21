package com.huawei.ascend.service.platform.web.runs;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Isolated executor for run dispatch fan-out from {@link RunController}.
 *
 * <p>Why not ForkJoin common pool? Under bursty POST /v1/runs traffic, sharing
 * the global async pool can amplify tail latency for unrelated tasks. This pool
 * keeps run-dispatch workload bounded and visible.
 */
@Configuration
@EnableConfigurationProperties(RunDispatchProperties.class)
public class RunDispatchExecutorConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(RunDispatchExecutorConfiguration.class);

    private final MeterRegistry meterRegistry;
    private final RunDispatchProperties properties;

    public RunDispatchExecutorConfiguration(MeterRegistry meterRegistry,
                                            RunDispatchProperties properties) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    @Bean(name = RunController.RUN_DISPATCH_EXECUTOR_BEAN, destroyMethod = "shutdown")
    public Executor runDispatchExecutor() {
        RunDispatchProperties.RejectionPolicy policy = properties.getRejectionPolicy();
        int coreThreads = Math.max(1, properties.getCoreThreads());
        int maxThreads = Math.max(coreThreads, properties.getMaxThreads());
        int queueCapacity = Math.max(1, properties.getQueueCapacity());
        Counter rejectedCounter = Counter.builder("springai_ascend_run_dispatch_rejected_total")
                .description("Total run-dispatch tasks rejected by the dedicated executor")
                .tag("policy", policy.name())
                .register(meterRegistry);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreThreads,
                maxThreads,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread t = new Thread(runnable);
                    t.setName("run-dispatch-" + t.threadId());
                    t.setDaemon(true);
                    return t;
                },
                rejectionHandler(policy, rejectedCounter));

        Gauge.builder("springai_ascend_run_dispatch_executor_active_threads", executor, ThreadPoolExecutor::getActiveCount)
                .description("Active threads in run-dispatch executor")
                .tag("policy", policy.name())
                .register(meterRegistry);
        Gauge.builder("springai_ascend_run_dispatch_executor_queue_depth", executor,
                        e -> e.getQueue().size())
                .description("Queue depth of run-dispatch executor")
                .tag("policy", policy.name())
                .register(meterRegistry);
        LOG.info("Run dispatch executor initialized: coreThreads={} maxThreads={} queueCapacity={} rejectionPolicy={}",
                coreThreads, maxThreads, queueCapacity, policy);
        return executor;
    }

    private RejectedExecutionHandler rejectionHandler(RunDispatchProperties.RejectionPolicy policy,
                                                      Counter rejectedCounter) {
        RejectedExecutionHandler fallback = switch (policy) {
            case ABORT -> new ThreadPoolExecutor.AbortPolicy();
            case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
        };
        return (runnable, executor) -> {
            rejectedCounter.increment();
            if (policy == RunDispatchProperties.RejectionPolicy.ABORT) {
                LOG.warn("Run dispatch task rejected (policy=ABORT): activeThreads={} queueDepth={} queueRemainingCapacity={}",
                        executor.getActiveCount(),
                        executor.getQueue().size(),
                        executor.getQueue().remainingCapacity());
            }
            fallback.rejectedExecution(runnable, executor);
        };
    }
}

package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import java.util.concurrent.Executor;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Task;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Covers the auto-configuration's bean-backoff contracts (durable TaskStore replacement, daemon
 * event-bus thread, no broad Executor bean) and the config→settings mapping that decides whether
 * (and how) trajectory is enabled in prod.
 */
class RuntimeAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    /** A consumer-supplied durable TaskStore must replace the in-memory default, not coexist with it. */
    @Test
    void customTaskStoreSuppressesInMemoryDefault() {
        runner.withUserConfiguration(CustomStoreConfiguration.class, RuntimeAutoConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).getBeans(TaskStore.class).hasSize(1);
                    assertThat(ctx.getBean(TaskStore.class)).isInstanceOf(RecordingTaskStore.class);
                });
    }

    /** The event-bus loop must run on the SDK's own daemon thread so a hosting JVM can exit. */
    @Test
    void eventBusProcessorRunsOnDaemonThread() {
        runner.withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(MainEventBusProcessor.class);
                    Thread processorThread = Thread.getAllStackTraces().keySet().stream()
                            .filter(t -> t.getName().contains("MainEventBusProcessor"))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("processor thread not started"));
                    assertThat(processorThread.isDaemon())
                            .as("processor thread must be daemon or it blocks JVM exit")
                            .isTrue();
                });
    }

    /**
     * Actuator is an optional dependency: the auto-configuration must stay loadable
     * (skipping the health contribution) in hosts without HealthIndicator on the
     * classpath — a bean-method signature mentioning the indicator on the outer
     * configuration class makes context startup throw NoClassDefFoundError there.
     */
    @Test
    void autoConfigurationLoadsWithoutActuatorOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader(HealthIndicator.class))
                .withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean("agentRuntimeHealthIndicator");
                });
    }

    /**
     * No bean assignable to java.util.concurrent.Executor may be exposed: Spring Boot's
     * applicationTaskExecutor backs off when one exists, silently disabling the
     * application's default (virtual-thread) task executor.
     */
    @Test
    void noBroadExecutorBeanExposed() {
        runner.withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> assertThat(ctx.getBeanNamesForType(Executor.class)).isEmpty());
    }

    @Test
    void disabledYieldsOff() {
        TrajectoryProperties properties = new TrajectoryProperties();
        properties.setEnabled(false);
        assertThat(RuntimeAutoConfiguration.toTrajectorySettings(properties).enabled()).isFalse();
    }

    @Test
    void enabledCarriesMaskAndTruncate() {
        TrajectoryProperties properties = new TrajectoryProperties();
        TrajectorySettings settings = RuntimeAutoConfiguration.toTrajectorySettings(properties);
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.truncateChars()).isEqualTo(256);
        assertThat(settings.maskKeyPattern()).isNotNull();
    }

    @Test
    void invalidMaskPatternFailsSafeToTheDefaultNotABootCrash() {
        TrajectoryProperties properties = new TrajectoryProperties();
        properties.getMask().setKeyPattern("(unbalanced");
        TrajectorySettings settings = RuntimeAutoConfiguration.toTrajectorySettings(properties);
        // Never crashes, never degrades to a null pattern (which would silently disable redaction).
        assertThat(settings.maskKeyPattern().pattern()).isEqualTo(TrajectoryMasking.DEFAULT_KEY_PATTERN);
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStoreConfiguration {
        @Bean
        TaskStore durableTaskStore() { return new RecordingTaskStore(); }

        // InMemoryQueueManager needs a TaskStateProvider; a durable store would implement both.
        @Bean
        TaskStateProvider durableTaskStateProvider() {
            return new TaskStateProvider() {
                @Override
                public boolean isTaskActive(String taskId) { return false; }

                @Override
                public boolean isTaskFinalized(String taskId) { return true; }
            };
        }
    }

    static final class RecordingTaskStore implements TaskStore {
        @Override
        public void save(Task task, boolean overwrite) { }

        @Override
        public Task get(String taskId) { return null; }

        @Override
        public void delete(String taskId) { }

        @Override
        public ListTasksResult list(ListTasksParams params) { return new ListTasksResult(java.util.List.of()); }
    }
}

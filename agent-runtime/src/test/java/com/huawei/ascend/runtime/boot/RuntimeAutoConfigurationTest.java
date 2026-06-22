package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Covers the auto-configuration's bean-backoff contracts (durable TaskStore replacement, daemon
 * event-bus thread, no broad Executor bean) and the config→settings mapping that decides whether
 * (and how) trajectory is enabled in prod.
 */
@ExtendWith(OutputCaptureExtension.class)
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

    @Test
    void agentRuntimeRemoteInvocationMaxLegsDefaultsToFive() {
        runner.withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> assertThat(ctx.getBean(AgentRuntimeProperties.class)
                        .getRemoteInvocation().getMaxLegs()).isEqualTo(5));
    }

    @Test
    void agentRuntimeRemoteInvocationMaxLegsBindsAndClamps() {
        runner.withPropertyValues("agent-runtime.remote-invocation.max-legs=200")
                .withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> assertThat(ctx.getBean(AgentRuntimeProperties.class)
                        .getRemoteInvocation().getMaxLegs()).isEqualTo(100));

        runner.withPropertyValues("agent-runtime.remote-invocation.max-legs=0")
                .withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> assertThat(ctx.getBean(AgentRuntimeProperties.class)
                        .getRemoteInvocation().getMaxLegs()).isEqualTo(1));
    }

    @Test
    void agentRuntimePropertiesDoNotPreventExecutorAutoConfiguration() {
        runner.withBean("handlerA", AgentRuntimeHandler.class, () -> new NamedHandler("agent-a"))
                .withPropertyValues("agent-runtime.remote-invocation.max-legs=2")
                .withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AgentRuntimeProperties.class);
                    assertThat(ctx).hasSingleBean(AgentExecutor.class);
                });
    }

    /**
     * The configured default-agent-id pins the served card to the hosted handler.
     * The runtime hosts exactly one agent (the executor rejects multiple handlers
     * at boot), so the key's job is validate-and-name, not multi-handler routing.
     */
    @Test
    void defaultAgentIdMatchingTheHostedHandlerNamesTheCard() {
        runner.withBean("handlerA", AgentRuntimeHandler.class, () -> new NamedHandler("agent-a"))
                .withPropertyValues("agent-runtime.access.a2a.default-agent-id=agent-a")
                .withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> assertThat(ctx.getBean(AgentCard.class).name()).isEqualTo("agent-a"));
    }

    /** Unset default-agent-id keeps the existing behavior: the hosted handler names the card. */
    @Test
    void unsetDefaultAgentIdFallsBackToTheHostedHandler() {
        runner.withBean("handlerA", AgentRuntimeHandler.class, () -> new NamedHandler("agent-a"))
                .withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> assertThat(ctx.getBean(AgentCard.class).name()).isEqualTo("agent-a"));
    }

    /** A typo'd id must not silently serve an arbitrary card: WARN names the configured value and the candidates. */
    @Test
    void mismatchedDefaultAgentIdWarnsWithConfiguredValueAndAvailableIds(CapturedOutput output) {
        runner.withBean("handlerA", AgentRuntimeHandler.class, () -> new NamedHandler("agent-a"))
                .withPropertyValues("agent-runtime.access.a2a.default-agent-id=agent-typo")
                .withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx.getBean(AgentCard.class).name()).isEqualTo("agent-a");
                    assertThat(output).contains("default-agent-id 'agent-typo' matches no registered handler");
                    assertThat(output).contains("agent-a");
                });
    }

    /**
     * A host that only depends on the jar (no component scan of runtime.boot) must
     * still get the northbound controllers from the auto-configuration; otherwise
     * the engine boots healthy while every northbound route 404s.
     */
    @Test
    void servletHostsGetNorthboundControllersWithoutComponentScanning() {
        new WebApplicationContextRunner().withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(A2aJsonRpcController.class);
                    assertThat(ctx).hasSingleBean(AgentCardController.class);
                });
    }

    /** Hosts that DO component-scan runtime.boot keep exactly one controller of each type. */
    @Test
    void scannedControllerBeansSuppressTheAutoConfiguredOnes() {
        new WebApplicationContextRunner()
                .withUserConfiguration(ScannedControllersConfiguration.class, RuntimeAutoConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).getBeans(A2aJsonRpcController.class).hasSize(1);
                    assertThat(ctx).getBeans(AgentCardController.class).hasSize(1);
                });
    }

    /** Non-web hosts (pure engine embedding) must not fail on servlet-only controller beans. */
    @Test
    void nonWebHostsSkipTheControllerRegistration() {
        runner.withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(A2aJsonRpcController.class);
                    assertThat(ctx).doesNotHaveBean(AgentCardController.class);
                });
    }

    /** Stand-in for the component-scan path: user-declared controller beans of the same types. */
    @Configuration(proxyBeanMethods = false)
    static class ScannedControllersConfiguration {
        @Bean
        A2aJsonRpcController scannedJsonRpcController() {
            return new A2aJsonRpcController(null, new RuntimeAccessProperties());
        }

        @Bean
        AgentCardController scannedCardController() {
            return new AgentCardController(null, new RuntimeAccessProperties());
        }
    }

    static final class NamedHandler implements AgentRuntimeHandler {
        private final String agentId;

        NamedHandler(String agentId) {
            this.agentId = agentId;
        }

        @Override
        public String agentId() { return agentId; }

        @Override
        public boolean isHealthy() { return true; }

        @Override
        public Stream<?> execute(AgentExecutionContext context) { return Stream.empty(); }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> rawResults.map(raw -> AgentExecutionResult.completed("ok"));
        }
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

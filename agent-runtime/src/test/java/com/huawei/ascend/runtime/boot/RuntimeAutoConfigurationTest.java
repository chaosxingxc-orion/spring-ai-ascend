package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.ascend.bus.knowledge.KnowledgeRegistry;
import com.huawei.ascend.bus.memory.SessionMemoryStore;
import com.huawei.ascend.bus.messaging.AgentMessage;
import com.huawei.ascend.bus.messaging.AgentMessageBus;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
     * No bean assignable to java.util.concurrent.Executor may be exposed: Spring Boot's
     * applicationTaskExecutor backs off when one exists, silently disabling the
     * application's default (virtual-thread) task executor.
     */
    @Test
    void noBroadExecutorBeanExposed() {
        runner.withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> assertThat(ctx.getBeanNamesForType(Executor.class)).isEmpty());
    }

    /** The dispatcher-built execution context must carry the capability beans to the handler. */
    @Test
    void executionContextCarriesBusCapabilityBeans() {
        AtomicReference<AgentExecutionContext> seen = new AtomicReference<>();
        runner.withUserConfiguration(RuntimeAutoConfiguration.class)
                .withBean(AgentRuntimeHandler.class, () -> recordingHandler(seen))
                .run(ctx -> {
                    ctx.getBean(AgentExecutor.class).execute(requestContext(), emitter());

                    AgentExecutionContext context = seen.get();
                    assertThat(context).as("handler must have been dispatched").isNotNull();
                    assertThat(context.getSessionMemory()).containsSame(ctx.getBean(SessionMemoryStore.class));
                    assertThat(context.getKnowledge()).containsSame(ctx.getBean(KnowledgeRegistry.class));
                    assertThat(context.getMessageBus()).containsSame(ctx.getBean(AgentMessageBus.class));
                });
    }

    /** Facade-only deployments (agent-runtime.enabled=false) must not host bus capability beans. */
    @Test
    void disabledRuntimeExposesNoBusCapabilityBeans() {
        runner.withUserConfiguration(RuntimeAutoConfiguration.class)
                .withPropertyValues("agent-runtime.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(SessionMemoryStore.class);
                    assertThat(ctx).doesNotHaveBean(KnowledgeRegistry.class);
                    assertThat(ctx).doesNotHaveBean(AgentMessageBus.class);
                });
    }

    /** Context shutdown must close the message bus through the inferred AutoCloseable destroy callback. */
    @Test
    void messageBusClosesOnContextShutdown() {
        runner.withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> {
                    AgentMessageBus bus = ctx.getBean(AgentMessageBus.class);
                    ctx.getSourceApplicationContext().close();
                    assertThatThrownBy(() -> bus.publish(
                            AgentMessage.of("tenant", "topic", "agent", Map.of())))
                            .isInstanceOf(IllegalStateException.class);
                });
    }

    private static AgentRuntimeHandler recordingHandler(AtomicReference<AgentExecutionContext> seen) {
        return new AgentRuntimeHandler() {
            @Override
            public String agentId() { return "recording-agent"; }

            @Override
            public boolean isHealthy() { return true; }

            @Override
            public Stream<?> execute(AgentExecutionContext context) {
                seen.set(context);
                return Stream.of(new Object());
            }

            @Override
            public StreamAdapter resultAdapter() {
                return raw -> raw.map(o -> AgentExecutionResult.completed("ok"));
            }
        };
    }

    private static RequestContext requestContext() {
        RequestContext ctx = mock(RequestContext.class);
        when(ctx.getTaskId()).thenReturn("task-wiring");
        when(ctx.getContextId()).thenReturn("session-wiring");
        when(ctx.getMessage()).thenReturn(Message.builder()
                .role(Message.Role.ROLE_USER).parts(List.<Part<?>>of(new TextPart("hi"))).build());
        return ctx;
    }

    private static AgentEmitter emitter() {
        AgentEmitter emitter = mock(AgentEmitter.class);
        when(emitter.newAgentMessage(anyList(), any())).thenAnswer(inv ->
                Message.builder().role(Message.Role.ROLE_AGENT).parts(inv.<List<Part<?>>>getArgument(0)).build());
        return emitter;
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

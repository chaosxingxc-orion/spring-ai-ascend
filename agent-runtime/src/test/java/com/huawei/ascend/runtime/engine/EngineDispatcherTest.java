package com.huawei.ascend.runtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AbstractStatefulAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.service.AgentStateKey;
import com.huawei.ascend.runtime.engine.service.AgentStateSnapshot;
import com.huawei.ascend.runtime.engine.service.AgentStateStore;
import com.huawei.ascend.runtime.engine.service.InMemoryAgentStateStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EngineDispatcherTest {

    private EngineExecutionScope scope() {
        return new EngineExecutionScope("t1", "u1", "s1", "task-1", "echo-agent");
    }

    private EngineCommandEvent cmd() {
        EngineInput in = new EngineInput("text", List.of(), Map.of());
        return new EngineCommandEvent("EXECUTE", scope(), in, Instant.EPOCH);
    }

    @Test
    void dispatch_completedEvent_routesMarkRunningAndSucceededToControlOnly() {
        TaskControlClient task = mock(TaskControlClient.class);
        AgentRuntimeHandlerRegistry registry = new DefaultAgentRuntimeHandlerRegistry();
        registry.register("echo-agent", new FakeAgentHandler(List.of(
                Map.of("result_type", "answer", "output", "hi"))));
        EngineDispatcher dispatcher = new EngineDispatcher(registry, task);

        dispatcher.dispatch(cmd());

        // Single outbound write: the engine reports only to the control port; egress is the
        // control plane's responsibility, so the engine never writes output directly.
        verify(task).markRunning(scope());
        verify(task).markSucceeded(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineEvent.class));
    }

    @Test
    void dispatch_outputThenFailed_routesAppendOutputAndMarkFailedToControlOnly() {
        TaskControlClient task = mock(TaskControlClient.class);
        AgentRuntimeHandlerRegistry registry = new DefaultAgentRuntimeHandlerRegistry();
        registry.register("echo-agent", new FakeAgentHandler(List.of(
                Map.of("result_type", "output", "output", "partial"),
                Map.of("result_type", "error", "error_code", "ERR", "output", "boom"))));
        EngineDispatcher dispatcher = new EngineDispatcher(registry, task);

        dispatcher.dispatch(cmd());

        verify(task).markRunning(scope());
        verify(task).appendOutput(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineEvent.class));
        verify(task).markFailed(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineEvent.class));
    }

    @Test
    void dispatch_loadsAndSavesAgentStateAcrossTheSameTask() {
        TaskControlClient task = mock(TaskControlClient.class);
        InMemoryAgentStateStore stateStore = new InMemoryAgentStateStore();
        StatefulAgentHandler handler = new StatefulAgentHandler();
        AgentRuntimeHandlerRegistry registry = new DefaultAgentRuntimeHandlerRegistry();
        registry.register("echo-agent", handler);
        EngineDispatcher dispatcher = new EngineDispatcher(registry, task, stateStore);

        dispatcher.dispatch(cmd());
        dispatcher.dispatch(cmd());

        AgentStateKey key = AgentStateKey.from(scope());
        assertThat(stateStore.load(key))
                .map(snapshot -> snapshot.values().get("phase"))
                .contains("resumed");
    }

    @Test
    void dispatch_doesNotTurnCompletedTaskIntoFailedWhenStateExportHookFails() {
        TaskControlClient task = mock(TaskControlClient.class);
        AgentRuntimeHandlerRegistry registry = new DefaultAgentRuntimeHandlerRegistry();
        registry.register("echo-agent", new FailingExportHookHandler());
        EngineDispatcher dispatcher = new EngineDispatcher(registry, task, new InMemoryAgentStateStore());

        dispatcher.dispatch(cmd());

        verify(task).markRunning(scope());
        verify(task).markSucceeded(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineEvent.class));
        verify(task, never()).markFailed(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineEvent.class));
    }

    @Test
    void dispatch_failsClosedWhenAgentStateLoadFails() {
        TaskControlClient task = mock(TaskControlClient.class);
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        AgentRuntimeHandlerRegistry registry = new DefaultAgentRuntimeHandlerRegistry();
        registry.register("echo-agent", handler);
        EngineDispatcher dispatcher = new EngineDispatcher(registry, task, new FailingLoadStateStore());

        dispatcher.dispatch(cmd());

        verify(task).markRunning(scope());
        verify(task).markFailed(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineEvent.class));
        verify(handler, never()).execute(org.mockito.ArgumentMatchers.any(AgentExecutionContext.class));
    }

    @Test
    void registryRejectsDuplicateAndBlankAgentIds() {
        DefaultAgentRuntimeHandlerRegistry registry = new DefaultAgentRuntimeHandlerRegistry();
        registry.register("echo-agent", new FakeAgentHandler(List.of()));

        assertThatThrownBy(() -> registry.register("echo-agent", new FakeAgentHandler(List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
        assertThatThrownBy(() -> registry.register(" ", new FakeAgentHandler(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
    }

    static class FakeAgentHandler implements AgentRuntimeHandler {
        private final List<Object> rawResults;

        FakeAgentHandler(List<Object> rawResults) {
            this.rawResults = rawResults;
        }

        @Override
        public String agentId() {
            return "echo-agent";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return rawResults.stream();
        }

        @Override
        public StreamAdapter resultAdapter() {
            return raw -> raw.map(FakeAgentHandler::map);
        }

        private static AgentExecutionResult map(Object raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) raw;
            String type = String.valueOf(result.get("result_type"));
            String output = String.valueOf(result.get("output"));
            if ("answer".equals(type)) {
                return AgentExecutionResult.completed(output);
            }
            if ("output".equals(type)) {
                return AgentExecutionResult.output(output);
            }
            return AgentExecutionResult.failed(String.valueOf(result.get("error_code")), output);
        }
    }

    static class StatefulAgentHandler extends AbstractStatefulAgentRuntimeHandler {
        private Optional<String> phase = Optional.empty();

        StatefulAgentHandler() {
            super("echo-agent", "Echo Agent", "Echo agent with runtime state.");
        }

        @Override
        protected void beforeExecute(AgentExecutionContext context) {
            phase = context.getAgentState()
                    .map(snapshot -> String.valueOf(snapshot.values().get("phase")));
        }

        @Override
        protected Stream<?> doExecute(AgentExecutionContext context) {
            return Stream.of(Map.of("result_type", "answer", "output", "ok"));
        }

        @Override
        protected void afterExecute(AgentExecutionContext context) {
            context.replaceAgentState(Map.of("phase", phase.isEmpty() ? "asked-location" : "resumed"));
        }

        @Override
        public StreamAdapter resultAdapter() {
            return raw -> raw.map(FakeAgentHandler::map);
        }
    }

    static class FailingExportHookHandler extends AbstractStatefulAgentRuntimeHandler {

        FailingExportHookHandler() {
            super("echo-agent", "Echo Agent", "Echo agent with failing state export.");
        }

        @Override
        protected Stream<?> doExecute(AgentExecutionContext context) {
            return Stream.of(Map.of("result_type", "answer", "output", "ok"));
        }

        @Override
        protected void afterExecute(AgentExecutionContext context) {
            throw new IllegalStateException("export failed");
        }

        @Override
        public StreamAdapter resultAdapter() {
            return raw -> raw.map(FakeAgentHandler::map);
        }
    }

    static class FailingLoadStateStore implements AgentStateStore {

        @Override
        public Optional<AgentStateSnapshot> load(AgentStateKey key) {
            throw new IllegalStateException("load failed");
        }

        @Override
        public AgentStateSnapshot save(AgentStateSnapshot snapshot) {
            return snapshot;
        }

        @Override
        public void delete(AgentStateKey key) {
        }
    }
}

package com.huawei.ascend.runtime.engine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.huawei.ascend.runtime.engine.event.EngineCommandEvent;
import com.huawei.ascend.runtime.engine.event.EngineCompletedEvent;
import com.huawei.ascend.runtime.engine.event.EngineFailedEvent;
import com.huawei.ascend.runtime.engine.event.EngineOutputEvent;
import com.huawei.ascend.runtime.engine.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.model.EngineInput;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.port.TaskControlClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        verify(task).markSucceeded(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineCompletedEvent.class));
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
        verify(task).appendOutput(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineOutputEvent.class));
        verify(task).markFailed(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineFailedEvent.class));
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
}

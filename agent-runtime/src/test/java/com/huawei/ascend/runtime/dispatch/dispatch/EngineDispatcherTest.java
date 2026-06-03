package com.huawei.ascend.service.engine.dispatch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import com.huawei.ascend.service.engine.event.EngineCompletedEvent;
import com.huawei.ascend.service.engine.event.EngineFailedEvent;
import com.huawei.ascend.service.engine.event.EngineOutputEvent;
import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineInput;
import com.huawei.ascend.service.engine.port.AccessLayerClient;
import com.huawei.ascend.service.engine.spi.AgentExecutionResult;
import com.huawei.ascend.service.engine.spi.AgentHandler;
import com.huawei.ascend.service.engine.spi.AgentResultAdapter;
import com.huawei.ascend.service.engine.port.TaskControlClient;
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
    void dispatch_completedEvent_routesToMarkRunningSucceededAndCompleteOutput() {
        TaskControlClient task = mock(TaskControlClient.class);
        AccessLayerClient access = mock(AccessLayerClient.class);
        AgentHandlerRegistry registry = new DefaultAgentHandlerRegistry();
        registry.register("echo-agent", new FakeAgentHandler(List.of(
                Map.of("result_type", "answer", "output", "hi"))));
        EngineDispatcher dispatcher = new EngineDispatcher(registry, task, access);

        dispatcher.dispatch(cmd());

        verify(task).markRunning(scope());
        verify(task).markSucceeded(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineCompletedEvent.class));
        verify(access).completeOutput(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineCompletedEvent.class));
    }

    @Test
    void dispatch_outputThenFailed_routesAppendOutputAndMarkFailed() {
        TaskControlClient task = mock(TaskControlClient.class);
        AccessLayerClient access = mock(AccessLayerClient.class);
        AgentHandlerRegistry registry = new DefaultAgentHandlerRegistry();
        registry.register("echo-agent", new FakeAgentHandler(List.of(
                Map.of("result_type", "output", "output", "partial"),
                Map.of("result_type", "error", "error_code", "ERR", "output", "boom"))));
        EngineDispatcher dispatcher = new EngineDispatcher(registry, task, access);

        dispatcher.dispatch(cmd());

        verify(task).markRunning(scope());
        verify(access).appendOutput(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineOutputEvent.class));
        verify(task).markFailed(org.mockito.ArgumentMatchers.eq(scope()), org.mockito.ArgumentMatchers.any(EngineFailedEvent.class));
    }

    @Test
    void registryRejectsDuplicateAndBlankAgentIds() {
        DefaultAgentHandlerRegistry registry = new DefaultAgentHandlerRegistry();
        registry.register("echo-agent", new FakeAgentHandler(List.of()));

        assertThatThrownBy(() -> registry.register("echo-agent", new FakeAgentHandler(List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
        assertThatThrownBy(() -> registry.register(" ", new FakeAgentHandler(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
    }

    static class FakeAgentHandler implements AgentHandler {
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
        public AgentResultAdapter resultAdapter() {
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

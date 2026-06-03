package com.huawei.ascend.service.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.service.engine.api.DefaultEngineDispatchApi;
import com.huawei.ascend.service.engine.api.EngineDispatchApi;
import com.huawei.ascend.service.engine.api.EnqueueEngineCancelRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineExecutionRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineResumeRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineStatus;
import com.huawei.ascend.service.engine.command.EngineCommandEventFactory;
import com.huawei.ascend.service.engine.command.EngineCommandProcessor;
import com.huawei.ascend.service.engine.command.InternalEngineCommandGateway;
import com.huawei.ascend.service.engine.dispatch.AgentHandlerRegistry;
import com.huawei.ascend.service.engine.dispatch.DefaultAgentHandlerRegistry;
import com.huawei.ascend.service.engine.dispatch.EngineDispatcher;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineInput;
import com.huawei.ascend.service.engine.support.FakeInterruptingAgentHandler;
import com.huawei.ascend.service.engine.support.RecordingAccessLayerClient;
import com.huawei.ascend.service.engine.support.RecordingTaskControlClient;
import com.huawei.ascend.service.queue.QueueManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end engine closed-loop test wiring the real components together —
 * inbound API → queue → subscriber → dispatcher → handler → events → outbound
 * clients — using in-memory recording clients (no sibling modules, no network).
 * Exercises EXECUTE, interrupt → RESUME, and CANCEL routing (design §7, §13).
 */
class EngineClosedLoopIntegrationTest {

    private RecordingTaskControlClient taskControl;
    private RecordingAccessLayerClient accessLayer;
    private InternalEngineCommandGateway gateway;
    private EngineDispatchApi api;

    @BeforeEach
    void setUp() {
        taskControl = new RecordingTaskControlClient();
        accessLayer = new RecordingAccessLayerClient();
        AgentHandlerRegistry registry = new DefaultAgentHandlerRegistry();
        registry.register("echo-agent", new FakeInterruptingAgentHandler("echo-agent"));

        gateway = new InternalEngineCommandGateway(new QueueManager());
        EngineDispatcher dispatcher = new EngineDispatcher(registry, taskControl, accessLayer);
        EngineCommandProcessor processor = new EngineCommandProcessor(gateway, dispatcher, Runnable::run);
        processor.start();
        api = new DefaultEngineDispatchApi(new EngineCommandEventFactory(), gateway);
    }

    private EngineExecutionScope scope() {
        return new EngineExecutionScope("t", "u", "s", "task-1", "echo-agent");
    }

    private EngineInput input() {
        return new EngineInput("text", List.of(), Map.of());
    }

    @Test
    void executeThenResume_drivesInterruptThenCompletionThroughTheWholeChain() {
        // First EXECUTE: the agent interrupts and waits for input.
        EnqueueEngineStatus first = api.enqueueExecution(new EnqueueEngineExecutionRequest(scope(), input()));
        assertThat(first).isEqualTo(EnqueueEngineStatus.SUCCESS);
        assertThat(taskControl.transitions).containsExactly("RUNNING:task-1", "WAITING:task-1");
        assertThat(accessLayer.userInputRequests).hasSize(1);
        assertThat(accessLayer.userInputRequests.get(0).getPrompt()).isEqualTo("Need your confirmation");

        // RESUME: the same agent now completes, streaming output then completion.
        api.enqueueResume(new EnqueueEngineResumeRequest(scope(), input()));
        assertThat(taskControl.transitions)
                .containsExactly("RUNNING:task-1", "WAITING:task-1", "RUNNING:task-1", "SUCCEEDED:task-1");
        assertThat(accessLayer.signals)
                .containsExactly("REQUEST_INPUT:task-1", "APPEND:task-1", "COMPLETE:task-1");
        assertThat(accessLayer.completed.get(0).getFinalOutput().getContent()).isEqualTo("final answer");
    }

    @Test
    void cancel_marksTaskCancelledWithoutRunningTheHandler() {
        api.enqueueCancel(new EnqueueEngineCancelRequest(scope()));

        assertThat(taskControl.transitions).containsExactly("CANCELLED:task-1");
        assertThat(taskControl.cancelled).hasSize(1);
        assertThat(accessLayer.signals).isEmpty();
    }
}

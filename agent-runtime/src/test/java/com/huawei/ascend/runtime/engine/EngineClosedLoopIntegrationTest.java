package com.huawei.ascend.runtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.api.DefaultEngineExecutionApi;
import com.huawei.ascend.runtime.engine.api.EngineExecutionApi;
import com.huawei.ascend.runtime.engine.api.EnqueueEngineCancelRequest;
import com.huawei.ascend.runtime.engine.api.EnqueueEngineExecutionRequest;
import com.huawei.ascend.runtime.engine.api.EnqueueEngineResumeRequest;
import com.huawei.ascend.runtime.engine.api.EnqueueEngineStatus;
import com.huawei.ascend.runtime.engine.support.FakeInterruptingAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.support.RecordingTaskControlClient;
import com.huawei.ascend.runtime.queue.QueueManager;
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
    private InternalEngineCommandGateway gateway;
    private EngineExecutionApi api;

    @BeforeEach
    void setUp() {
        taskControl = new RecordingTaskControlClient();
        AgentRuntimeHandlerRegistry registry = new DefaultAgentRuntimeHandlerRegistry();
        registry.register("echo-agent", new FakeInterruptingAgentRuntimeHandler("echo-agent"));

        gateway = new InternalEngineCommandGateway(new QueueManager());
        EngineDispatcher dispatcher = new EngineDispatcher(registry, taskControl);
        EngineWorker processor = new EngineWorker(gateway, dispatcher, Runnable::run);
        processor.start();
        api = new DefaultEngineExecutionApi(new EngineCommandEventFactory(), gateway);
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
        // The engine reports only to the single control port — no direct access write.
        assertThat(taskControl.transitions).containsExactly("RUNNING:task-1", "WAITING:task-1");
        assertThat(taskControl.waiting).hasSize(1);
        assertThat(taskControl.waiting.get(0).prompt()).isEqualTo("Need your confirmation");

        // RESUME: the same agent now streams output then completes — all reported to control only.
        api.enqueueResume(new EnqueueEngineResumeRequest(scope(), input()));
        assertThat(taskControl.transitions)
                .containsExactly("RUNNING:task-1", "WAITING:task-1",
                        "RUNNING:task-1", "APPEND:task-1", "SUCCEEDED:task-1");
        assertThat(taskControl.succeeded.get(0).output().getContent()).isEqualTo("final answer");
    }

    @Test
    void cancel_marksTaskCancelledWithoutRunningTheHandler() {
        api.enqueueCancel(new EnqueueEngineCancelRequest(scope()));

        assertThat(taskControl.transitions).containsExactly("CANCELLED:task-1");
        assertThat(taskControl.cancelled).hasSize(1);
    }

    @Test
    void unknownAgentId_convergesTheAcceptedTaskToTerminalFailureThroughControl() {
        // A request for an agentId with no registered handler is still accepted + enqueued;
        // the engine must converge it to a terminal FAILED via the single control authority
        // (not let the worker thread throw out of dispatch() and leave the task hanging).
        EngineExecutionScope unknown = new EngineExecutionScope("t", "u", "s", "task-x", "missing-agent");
        EnqueueEngineStatus status = api.enqueueExecution(new EnqueueEngineExecutionRequest(unknown, input()));

        assertThat(status).isEqualTo(EnqueueEngineStatus.SUCCESS);
        assertThat(taskControl.transitions).containsExactly("RUNNING:task-x", "FAILED:task-x");
        assertThat(taskControl.failed).hasSize(1);
        assertThat(taskControl.failed.get(0).errorCode()).isEqualTo("AGENT_ID_INVALID");
    }
}

package com.huawei.ascend.runtime.control.test;

import com.huawei.ascend.runtime.engine.api.DefaultEngineExecutionApi;
import com.huawei.ascend.runtime.engine.api.EngineExecutionApi;
import com.huawei.ascend.runtime.engine.command.EngineCommandEventFactory;
import com.huawei.ascend.runtime.engine.command.EngineWorker;
import com.huawei.ascend.runtime.engine.command.InternalEngineCommandGateway;
import com.huawei.ascend.runtime.engine.AgentRuntimeHandlerRegistry;
import com.huawei.ascend.runtime.engine.DefaultAgentRuntimeHandlerRegistry;
import com.huawei.ascend.runtime.engine.EngineDispatcher;
import com.huawei.ascend.runtime.engine.support.FakeInterruptingAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.support.RecordingAccessLayerClient;
import com.huawei.ascend.runtime.common.AgentRequest;
import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.control.EngineTaskControlAdapter;
import com.huawei.ascend.runtime.control.TaskControlService;
import com.huawei.ascend.runtime.control.TaskState;
import com.huawei.ascend.runtime.control.WaitingReason;
import com.huawei.ascend.runtime.control.api.TaskControlApi;
import com.huawei.ascend.runtime.queue.QueueManager;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskflowEngineBridgeWhiteboxTest {

    @Test
    void executeWaitingResumeCompletionLoopUpdatesTccStateWithoutEngineOwningQueue() {
        QueueManager manager = new QueueManager();
        InternalEngineCommandGateway engineQueue = new InternalEngineCommandGateway(manager);
        EngineExecutionApi dispatchApi = new DefaultEngineExecutionApi(new EngineCommandEventFactory(), engineQueue);
        TaskControlService tcc = new TaskControlService(
                manager,
                dispatchApi,
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
        RecordingAccessLayerClient access = new RecordingAccessLayerClient();
        // Single-write: the control adapter owns egress; the engine writes only to the adapter.
        EngineTaskControlAdapter adapter = new EngineTaskControlAdapter(tcc, access);
        AgentRuntimeHandlerRegistry registry = new DefaultAgentRuntimeHandlerRegistry();
        registry.register("echo-agent", new FakeInterruptingAgentRuntimeHandler("echo-agent"));
        EngineWorker processor =
                new EngineWorker(engineQueue, new EngineDispatcher(registry, adapter), Runnable::run);
        processor.start();

        TaskControlApi.TaskResult waiting = tcc.run(new TaskControlApi.RunCommand(request("hello")))
                .toCompletableFuture().join();

        assertThat(waiting.state()).isEqualTo(TaskState.WAITING);
        assertThat(tcc.findTask("tenant", "session", waiting.taskId()).orElseThrow().getWaitingReason())
                .isEqualTo(WaitingReason.USER_INPUT);

        TaskControlApi.TaskResult completed = tcc.resume(new TaskControlApi.ResumeCommand(
                        waiting.taskId(), request("yes")))
                .toCompletableFuture().join();

        assertThat(completed.taskId()).isEqualTo(waiting.taskId());
        assertThat(completed.state()).isEqualTo(TaskState.COMPLETED);
        assertThat(access.signals)
                .containsExactly("REQUEST_INPUT:" + waiting.taskId(),
                        "APPEND:" + waiting.taskId(),
                        "COMPLETE:" + waiting.taskId());
        assertThat(manager.find("task:tenant:session")).isPresent();
        assertThat(manager.find("engine:commands")).isPresent();
    }

    private AgentRequest request(String input) {
        return new AgentRequest(
                "tenant",
                "user",
                "echo-agent",
                "session",
                java.util.List.of(Message.user(input)),
                null,
                Map.of());
    }
}

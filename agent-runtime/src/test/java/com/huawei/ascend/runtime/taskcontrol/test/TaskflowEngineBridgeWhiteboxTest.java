package com.huawei.ascend.runtime.taskcontrol.test;

import com.huawei.ascend.runtime.dispatch.api.DefaultEngineDispatchApi;
import com.huawei.ascend.runtime.dispatch.api.EngineDispatchApi;
import com.huawei.ascend.runtime.dispatch.command.EngineCommandEventFactory;
import com.huawei.ascend.runtime.dispatch.command.EngineCommandProcessor;
import com.huawei.ascend.runtime.dispatch.command.InternalEngineCommandGateway;
import com.huawei.ascend.runtime.dispatch.dispatch.AgentHandlerRegistry;
import com.huawei.ascend.runtime.dispatch.dispatch.DefaultAgentHandlerRegistry;
import com.huawei.ascend.runtime.dispatch.dispatch.EngineDispatcher;
import com.huawei.ascend.runtime.dispatch.support.FakeInterruptingAgentHandler;
import com.huawei.ascend.runtime.dispatch.support.RecordingAccessLayerClient;
import com.huawei.ascend.runtime.schema.AgentRequest;
import com.huawei.ascend.runtime.schema.Message;
import com.huawei.ascend.runtime.taskcontrol.EngineTaskControlAdapter;
import com.huawei.ascend.runtime.taskcontrol.TaskControlService;
import com.huawei.ascend.runtime.taskcontrol.TaskState;
import com.huawei.ascend.runtime.taskcontrol.WaitingReason;
import com.huawei.ascend.runtime.taskcontrol.api.TaskControlClient;
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
        EngineDispatchApi dispatchApi = new DefaultEngineDispatchApi(new EngineCommandEventFactory(), engineQueue);
        TaskControlService tcc = new TaskControlService(
                manager,
                dispatchApi,
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
        EngineTaskControlAdapter adapter = new EngineTaskControlAdapter(tcc);
        RecordingAccessLayerClient access = new RecordingAccessLayerClient();
        AgentHandlerRegistry registry = new DefaultAgentHandlerRegistry();
        registry.register("echo-agent", new FakeInterruptingAgentHandler("echo-agent"));
        EngineCommandProcessor processor =
                new EngineCommandProcessor(engineQueue, new EngineDispatcher(registry, adapter, access), Runnable::run);
        processor.start();

        TaskControlClient.TaskResult waiting = tcc.run(new TaskControlClient.RunCommand(request("hello")))
                .toCompletableFuture().join();

        assertThat(waiting.state()).isEqualTo(TaskState.WAITING);
        assertThat(tcc.findTask("tenant", "session", waiting.taskId()).orElseThrow().getWaitingReason())
                .isEqualTo(WaitingReason.USER_INPUT);

        TaskControlClient.TaskResult completed = tcc.resume(new TaskControlClient.ResumeCommand(
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

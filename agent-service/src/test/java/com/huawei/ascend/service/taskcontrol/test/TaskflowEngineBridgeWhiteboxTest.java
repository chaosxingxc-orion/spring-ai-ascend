package com.huawei.ascend.service.taskcontrol.test;

import com.huawei.ascend.service.engine.api.DefaultEngineDispatchApi;
import com.huawei.ascend.service.engine.api.EngineDispatchApi;
import com.huawei.ascend.service.engine.dispatch.AgentHandlerRegistry;
import com.huawei.ascend.service.engine.dispatch.DefaultAgentHandlerRegistry;
import com.huawei.ascend.service.engine.dispatch.EngineDispatcher;
import com.huawei.ascend.service.engine.queue.EngineCommandEventFactory;
import com.huawei.ascend.service.engine.queue.EngineCommandSubscriber;
import com.huawei.ascend.service.engine.queue.InMemoryEngineQueueGateway;
import com.huawei.ascend.service.engine.support.FakeInterruptingAgentHandler;
import com.huawei.ascend.service.engine.support.RecordingAccessLayerClient;
import com.huawei.ascend.service.taskcontrol.EngineTaskControlAdapter;
import com.huawei.ascend.service.taskcontrol.TaskControlService;
import com.huawei.ascend.service.taskcontrol.TaskState;
import com.huawei.ascend.service.taskcontrol.WaitingReason;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient;
import com.huawei.ascend.service.queue.QueueManager;
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
        InMemoryEngineQueueGateway engineQueue = new InMemoryEngineQueueGateway();
        EngineDispatchApi dispatchApi = new DefaultEngineDispatchApi(new EngineCommandEventFactory(), engineQueue);
        TaskControlService tcc = new TaskControlService(
                manager,
                dispatchApi,
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
        EngineTaskControlAdapter adapter = new EngineTaskControlAdapter(tcc);
        RecordingAccessLayerClient access = new RecordingAccessLayerClient();
        AgentHandlerRegistry registry = new DefaultAgentHandlerRegistry();
        registry.register("echo-agent", new FakeInterruptingAgentHandler("echo-agent"));
        new EngineCommandSubscriber(engineQueue, new EngineDispatcher(registry, adapter, access)).start();

        TaskControlClient.TaskResult waiting = tcc.runTask(command(
                        TaskControlClient.TaskAction.RUN, null, "hello"))
                .toCompletableFuture().join();

        assertThat(waiting.state()).isEqualTo(TaskState.WAITING);
        assertThat(tcc.findTask("tenant", "session", waiting.taskId()).orElseThrow().getWaitingReason())
                .isEqualTo(WaitingReason.USER_INPUT);

        TaskControlClient.TaskResult completed = tcc.runTask(command(
                        TaskControlClient.TaskAction.RESUME_INPUT, null, "yes"))
                .toCompletableFuture().join();

        assertThat(completed.taskId()).isEqualTo(waiting.taskId());
        assertThat(completed.state()).isEqualTo(TaskState.COMPLETED);
        assertThat(access.signals)
                .containsExactly("REQUEST_INPUT:" + waiting.taskId(),
                        "APPEND:" + waiting.taskId(),
                        "COMPLETE:" + waiting.taskId());
        assertThat(manager.findBySession("tenant", "session")).isPresent();
    }

    private TaskControlClient.RunTaskCommand command(TaskControlClient.TaskAction action, String taskId, String input) {
        return new TaskControlClient.RunTaskCommand(
                "tenant",
                "session",
                taskId,
                "echo-agent",
                action,
                input,
                null,
                null,
                Map.of("userId", "user"));
    }
}

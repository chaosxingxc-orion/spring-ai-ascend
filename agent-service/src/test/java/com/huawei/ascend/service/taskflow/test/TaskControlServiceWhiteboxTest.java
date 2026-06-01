package com.huawei.ascend.service.taskflow.test;

import com.huawei.ascend.service.engine.api.EngineDispatchApi;
import com.huawei.ascend.service.engine.api.EnqueueEngineCancelRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineExecutionRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineResumeRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineStatus;
import com.huawei.ascend.service.taskflow.control.Task;
import com.huawei.ascend.service.taskflow.control.TaskControlService;
import com.huawei.ascend.service.taskflow.control.TaskFailureCode;
import com.huawei.ascend.service.taskflow.control.TaskState;
import com.huawei.ascend.service.taskflow.control.WaitingReason;
import com.huawei.ascend.service.taskflow.control.api.TaskControlClient;
import com.huawei.ascend.service.taskflow.queue.QueueManager;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskControlServiceWhiteboxTest {

    private final RecordingEngineDispatchApi engine = new RecordingEngineDispatchApi();
    private final QueueManager queueManager = new QueueManager();
    private final TaskControlService service = new TaskControlService(
            queueManager,
            engine,
            Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void runTaskCreatesSessionQueueAndDispatchesExecution() {
        TaskControlClient.TaskResult result = run(TaskControlClient.TaskAction.RUN, null, "agent", "hello", "idem-1");

        assertThat(result.accepted()).isTrue();
        assertThat(result.state()).isEqualTo(TaskState.CREATED);
        assertThat(queueManager.findBySession("tenant", "session")).isPresent();
        assertThat(engine.executions).hasSize(1);
        assertThat(engine.executions.get(0).scope().sessionId()).isEqualTo("session");
        assertThat(engine.executions.get(0).scope().agentId()).isEqualTo("agent");
        assertThat(service.tasks("tenant", "session")).hasSize(1);
    }

    @Test
    void markMethodsEnforceRevisionAndTransitionOrder() {
        TaskControlClient.TaskResult created = run(TaskControlClient.TaskAction.RUN, null, "agent", "hello", null);

        TaskControlClient.TaskResult running = service.markRunning(mark(created.taskId(), 1L, null, null, null))
                .toCompletableFuture().join();
        TaskControlClient.TaskResult stale = service.markWaiting(mark(created.taskId(), 1L,
                        WaitingReason.USER_INPUT, null, "need-city"))
                .toCompletableFuture().join();
        TaskControlClient.TaskResult waiting = service.markWaiting(mark(created.taskId(), 2L,
                        WaitingReason.USER_INPUT, null, "need-city"))
                .toCompletableFuture().join();
        TaskControlClient.TaskResult rerunning = service.markRunning(mark(created.taskId(), 3L, null, null, null))
                .toCompletableFuture().join();
        TaskControlClient.TaskResult done = service.markSucceeded(mark(created.taskId(), 4L, null, null, "done"))
                .toCompletableFuture().join();

        assertThat(running.state()).isEqualTo(TaskState.RUNNING);
        assertThat(stale.accepted()).isFalse();
        assertThat(stale.revision()).isEqualTo(2L);
        assertThat(waiting.state()).isEqualTo(TaskState.WAITING);
        assertThat(rerunning.state()).isEqualTo(TaskState.RUNNING);
        assertThat(done.state()).isEqualTo(TaskState.COMPLETED);
        assertThat(done.revision()).isEqualTo(5L);
    }

    @Test
    void resumeInputTargetsWaitingTaskAndCancelMakesNextRunCreateNewTask() {
        TaskControlClient.TaskResult created = run(TaskControlClient.TaskAction.RUN, null, "agent", "hello", null);
        service.markRunning(mark(created.taskId(), 1L, null, null, null)).toCompletableFuture().join();
        service.markWaiting(mark(created.taskId(), 2L, WaitingReason.USER_INPUT, null, "need-city"))
                .toCompletableFuture().join();

        TaskControlClient.TaskResult resumed = run(TaskControlClient.TaskAction.RESUME_INPUT, null, "agent", "beijing", null);
        TaskControlClient.TaskResult cancelling = run(TaskControlClient.TaskAction.CANCEL,
                resumed.taskId(), "agent", null, null);
        TaskControlClient.TaskResult next = run(TaskControlClient.TaskAction.RUN, null, "agent", "new intent", null);

        assertThat(engine.resumes).hasSize(1);
        assertThat(resumed.taskId()).isEqualTo(created.taskId());
        assertThat(cancelling.state()).isEqualTo(TaskState.CANCELLING);
        assertThat(engine.cancels).hasSize(1);
        assertThat(next.taskId()).isNotEqualTo(created.taskId());
        assertThat(service.tasks("tenant", "session")).hasSize(2);
    }

    @Test
    void idempotencyKeyReturnsSameTaskResultWithoutSecondDispatch() {
        TaskControlClient.TaskResult first = run(TaskControlClient.TaskAction.RUN, null, "agent", "hello", "same-key");
        TaskControlClient.TaskResult second = run(TaskControlClient.TaskAction.RUN, null, "agent", "hello", "same-key");

        assertThat(second).isEqualTo(first);
        assertThat(engine.executions).hasSize(1);
        assertThat(service.tasks("tenant", "session")).hasSize(1);
    }

    @Test
    void rejectedEngineDispatchMarksTaskFailed() {
        engine.status = EnqueueEngineStatus.FAILED;

        TaskControlClient.TaskResult result = run(TaskControlClient.TaskAction.RUN, null, "agent", "hello", null);
        Task task = service.findTask("tenant", "session", result.taskId()).orElseThrow();

        assertThat(result.accepted()).isFalse();
        assertThat(result.state()).isEqualTo(TaskState.FAILED);
        assertThat(task.getFailureCode()).isEqualTo(TaskFailureCode.ENGINE_DISPATCH_REJECTED);
    }

    private TaskControlClient.TaskResult run(TaskControlClient.TaskAction action, String taskId,
                                             String agentId, Object input, String idempotencyKey) {
        return service.runTask(new TaskControlClient.RunTaskCommand(
                "tenant",
                "session",
                taskId,
                agentId,
                action,
                input,
                "reason",
                idempotencyKey,
                Map.of("userId", "user")))
                .toCompletableFuture().join();
    }

    private TaskControlClient.MarkTaskCommand mark(String taskId, long expectedRevision,
                                                   WaitingReason waitingReason,
                                                   TaskFailureCode failureCode,
                                                   Object detail) {
        return new TaskControlClient.MarkTaskCommand(
                "tenant",
                "session",
                taskId,
                expectedRevision,
                waitingReason,
                failureCode,
                detail,
                Map.of());
    }

    private static final class RecordingEngineDispatchApi implements EngineDispatchApi {
        private final List<EnqueueEngineExecutionRequest> executions = new ArrayList<>();
        private final List<EnqueueEngineResumeRequest> resumes = new ArrayList<>();
        private final List<EnqueueEngineCancelRequest> cancels = new ArrayList<>();
        private EnqueueEngineStatus status = EnqueueEngineStatus.SUCCESS;

        @Override
        public EnqueueEngineStatus enqueueExecution(EnqueueEngineExecutionRequest request) {
            executions.add(request);
            return status;
        }

        @Override
        public EnqueueEngineStatus enqueueResume(EnqueueEngineResumeRequest request) {
            resumes.add(request);
            return status;
        }

        @Override
        public EnqueueEngineStatus enqueueCancel(EnqueueEngineCancelRequest request) {
            cancels.add(request);
            return status;
        }
    }
}

package com.huawei.ascend.service.taskcontrol.test;

import com.huawei.ascend.service.engine.api.EngineDispatchApi;
import com.huawei.ascend.service.engine.api.EnqueueEngineCancelRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineExecutionRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineResumeRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineStatus;
import com.huawei.ascend.service.schema.AgentRequest;
import com.huawei.ascend.service.schema.Message;
import com.huawei.ascend.service.taskcontrol.Task;
import com.huawei.ascend.service.taskcontrol.TaskControlService;
import com.huawei.ascend.service.taskcontrol.TaskFailureCode;
import com.huawei.ascend.service.taskcontrol.TaskState;
import com.huawei.ascend.service.taskcontrol.WaitingReason;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient;
import com.huawei.ascend.service.queue.QueueManager;
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
        TaskControlClient.TaskResult result = run("agent", "hello", "idem-1");

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
        TaskControlClient.TaskResult created = run("agent", "hello", null);

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
        TaskControlClient.TaskResult created = run("agent", "hello", null);
        service.markRunning(mark(created.taskId(), 1L, null, null, null)).toCompletableFuture().join();
        service.markWaiting(mark(created.taskId(), 2L, WaitingReason.USER_INPUT, null, "need-city"))
                .toCompletableFuture().join();

        TaskControlClient.TaskResult resumed = resume(null, "agent", "beijing", null);
        TaskControlClient.TaskResult cancelling = cancel(resumed.taskId(), "agent");
        TaskControlClient.TaskResult next = run("agent", "new intent", null);

        assertThat(engine.resumes).hasSize(1);
        assertThat(resumed.taskId()).isEqualTo(created.taskId());
        assertThat(cancelling.state()).isEqualTo(TaskState.CANCELLING);
        assertThat(engine.cancels).hasSize(1);
        assertThat(next.taskId()).isNotEqualTo(created.taskId());
        assertThat(service.tasks("tenant", "session")).hasSize(2);
    }

    @Test
    void idempotencyKeyReturnsSameTaskResultWithoutSecondDispatch() {
        TaskControlClient.TaskResult first = run("agent", "hello", "same-key");
        TaskControlClient.TaskResult second = run("agent", "hello", "same-key");

        assertThat(second).isEqualTo(first);
        assertThat(engine.executions).hasSize(1);
        assertThat(service.tasks("tenant", "session")).hasSize(1);
    }

    @Test
    void idempotencyKeyDoesNotCollapseDifferentResumeTargets() {
        TaskControlClient.TaskResult first = run("agent", "first", null);
        service.markRunning(mark(first.taskId(), 1L, null, null, null)).toCompletableFuture().join();
        service.markWaiting(mark(first.taskId(), 2L, WaitingReason.USER_INPUT, null, "need-first"))
                .toCompletableFuture().join();

        TaskControlClient.TaskResult second = run("agent", "second", null);
        service.markRunning(mark(second.taskId(), 1L, null, null, null)).toCompletableFuture().join();
        service.markWaiting(mark(second.taskId(), 2L, WaitingReason.USER_INPUT, null, "need-second"))
                .toCompletableFuture().join();

        TaskControlClient.TaskResult firstResume = resume(first.taskId(), "agent", "answer-first", "same-resume-key");
        TaskControlClient.TaskResult secondResume = resume(second.taskId(), "agent", "answer-second", "same-resume-key");

        assertThat(firstResume.taskId()).isEqualTo(first.taskId());
        assertThat(secondResume.taskId()).isEqualTo(second.taskId());
        assertThat(engine.resumes).hasSize(2);
    }

    @Test
    void rejectedEngineDispatchMarksTaskFailed() {
        engine.status = EnqueueEngineStatus.FAILED;

        TaskControlClient.TaskResult result = run("agent", "hello", null);
        Task task = service.findTask("tenant", "session", result.taskId()).orElseThrow();

        assertThat(result.accepted()).isFalse();
        assertThat(result.state()).isEqualTo(TaskState.FAILED);
        assertThat(task.getFailureCode()).isEqualTo(TaskFailureCode.ENGINE_DISPATCH_REJECTED);
    }

    private TaskControlClient.TaskResult run(String agentId, String input, String idempotencyKey) {
        return service.run(new TaskControlClient.RunCommand(request(agentId, input, idempotencyKey)))
                .toCompletableFuture().join();
    }

    private TaskControlClient.TaskResult resume(String taskId, String agentId, String input, String idempotencyKey) {
        return service.resume(new TaskControlClient.ResumeCommand(taskId,
                request(agentId, input, idempotencyKey)))
                .toCompletableFuture().join();
    }

    private TaskControlClient.TaskResult cancel(String taskId, String agentId) {
        return service.cancel(new TaskControlClient.CancelCommand(
                "tenant",
                "user",
                agentId,
                "session",
                taskId,
                "reason",
                Map.of("userId", "user")))
                .toCompletableFuture().join();
    }

    private AgentRequest request(String agentId, String input, String idempotencyKey) {
        return new AgentRequest(
                "tenant",
                "user",
                agentId,
                "session",
                List.of(Message.user(input == null ? "" : input)),
                idempotencyKey,
                Map.of("userId", "user"));
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

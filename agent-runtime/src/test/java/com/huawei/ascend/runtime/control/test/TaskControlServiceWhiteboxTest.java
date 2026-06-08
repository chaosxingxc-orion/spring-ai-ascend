package com.huawei.ascend.runtime.control.test;

import com.huawei.ascend.runtime.engine.api.EngineExecutionApi;
import com.huawei.ascend.runtime.engine.api.EnqueueEngineCancelRequest;
import com.huawei.ascend.runtime.engine.api.EnqueueEngineExecutionRequest;
import com.huawei.ascend.runtime.engine.api.EnqueueEngineResumeRequest;
import com.huawei.ascend.runtime.engine.api.EnqueueEngineStatus;
import com.huawei.ascend.runtime.schema.AgentRequest;
import com.huawei.ascend.runtime.schema.Message;
import com.huawei.ascend.runtime.control.Task;
import com.huawei.ascend.runtime.control.TaskControlService;
import com.huawei.ascend.runtime.control.TaskFailureCode;
import com.huawei.ascend.runtime.control.TaskState;
import com.huawei.ascend.runtime.control.WaitingReason;
import com.huawei.ascend.runtime.control.api.TaskControlApi;
import com.huawei.ascend.runtime.queue.QueueManager;
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
        TaskControlApi.TaskResult result = run("agent", "hello", "idem-1");

        assertThat(result.accepted()).isTrue();
        assertThat(result.state()).isEqualTo(TaskState.CREATED);
        assertThat(queueManager.find("task:tenant:session")).isPresent();
        assertThat(engine.executions).hasSize(1);
        assertThat(engine.executions.get(0).scope().sessionId()).isEqualTo("session");
        assertThat(engine.executions.get(0).scope().agentId()).isEqualTo("agent");
        assertThat(service.tasks("tenant", "session")).hasSize(1);
    }

    @Test
    void markMethodsEnforceRevisionAndTransitionOrder() {
        TaskControlApi.TaskResult created = run("agent", "hello", null);

        TaskControlApi.TaskResult running = service.markRunning(mark(created.taskId(), 1L, null, null, null))
                .toCompletableFuture().join();
        TaskControlApi.TaskResult stale = service.markWaiting(mark(created.taskId(), 1L,
                        WaitingReason.USER_INPUT, null, "need-city"))
                .toCompletableFuture().join();
        TaskControlApi.TaskResult waiting = service.markWaiting(mark(created.taskId(), 2L,
                        WaitingReason.USER_INPUT, null, "need-city"))
                .toCompletableFuture().join();
        TaskControlApi.TaskResult rerunning = service.markRunning(mark(created.taskId(), 3L, null, null, null))
                .toCompletableFuture().join();
        TaskControlApi.TaskResult done = service.markSucceeded(mark(created.taskId(), 4L, null, null, "done"))
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
        TaskControlApi.TaskResult created = run("agent", "hello", null);
        service.markRunning(mark(created.taskId(), 1L, null, null, null)).toCompletableFuture().join();
        service.markWaiting(mark(created.taskId(), 2L, WaitingReason.USER_INPUT, null, "need-city"))
                .toCompletableFuture().join();

        TaskControlApi.TaskResult resumed = resume(null, "agent", "beijing", null);
        TaskControlApi.TaskResult cancelling = cancel(resumed.taskId(), "agent");
        TaskControlApi.TaskResult next = run("agent", "new intent", null);

        assertThat(engine.resumes).hasSize(1);
        assertThat(resumed.taskId()).isEqualTo(created.taskId());
        assertThat(cancelling.state()).isEqualTo(TaskState.CANCELLING);
        assertThat(engine.cancels).hasSize(1);
        assertThat(next.taskId()).isNotEqualTo(created.taskId());
        assertThat(service.tasks("tenant", "session")).hasSize(2);
    }

    @Test
    void waitingTaskCanBeResumedAndCompleted() {
        TaskControlApi.TaskResult created = run("agent", "weather", null);
        service.markRunning(mark(created.taskId(), 1L, null, null, null)).toCompletableFuture().join();
        TaskControlApi.TaskResult waiting = service.markWaiting(mark(created.taskId(), 2L,
                        WaitingReason.USER_INPUT, null, "need-city"))
                .toCompletableFuture().join();

        TaskControlApi.TaskResult resumed = resume(null, "agent", "beijing", null);
        TaskControlApi.TaskResult running = service.markRunning(mark(created.taskId(), 3L, null, null, null))
                .toCompletableFuture().join();
        TaskControlApi.TaskResult completed = service.markSucceeded(mark(created.taskId(), 4L,
                        null, null, "sunny"))
                .toCompletableFuture().join();

        assertThat(waiting.state()).isEqualTo(TaskState.WAITING);
        assertThat(resumed.taskId()).isEqualTo(created.taskId());
        assertThat(engine.resumes).hasSize(1);
        assertThat(engine.resumes.get(0).input().inputType()).isEqualTo("RESUME_SIGNAL");
        assertThat(running.state()).isEqualTo(TaskState.RUNNING);
        assertThat(completed.state()).isEqualTo(TaskState.COMPLETED);
        assertThat(completed.revision()).isEqualTo(5L);
    }

    @Test
    void runtimeCanMarkRunningTaskFailed() {
        TaskControlApi.TaskResult created = run("agent", "fail later", null);
        service.markRunning(mark(created.taskId(), 1L, null, null, null)).toCompletableFuture().join();

        TaskControlApi.TaskResult failed = service.markFailed(mark(created.taskId(), 2L,
                        null, TaskFailureCode.RUNTIME_ERROR, "boom"))
                .toCompletableFuture().join();
        Task task = service.findTask("tenant", "session", created.taskId()).orElseThrow();

        assertThat(failed.accepted()).isTrue();
        assertThat(failed.state()).isEqualTo(TaskState.FAILED);
        assertThat(task.getFailureCode()).isEqualTo(TaskFailureCode.RUNTIME_ERROR);
        assertThat(task.getDetail()).isEqualTo("boom");
    }

    @Test
    void cancelFlowCanReachCancelledWhenRuntimeAcknowledgesIt() {
        TaskControlApi.TaskResult created = run("agent", "cancel me", null);
        service.markRunning(mark(created.taskId(), 1L, null, null, null)).toCompletableFuture().join();

        TaskControlApi.TaskResult cancelling = cancel(created.taskId(), "agent");
        TaskControlApi.TaskResult cancelled = service.markCancelled(mark(created.taskId(), 3L,
                        null, TaskFailureCode.CANCELLED_BY_RUNTIME, "runtime-stopped"))
                .toCompletableFuture().join();
        TaskControlApi.TaskResult rejected = cancel(created.taskId(), "agent");

        assertThat(cancelling.state()).isEqualTo(TaskState.CANCELLING);
        assertThat(engine.cancels).hasSize(1);
        assertThat(cancelled.accepted()).isTrue();
        assertThat(cancelled.state()).isEqualTo(TaskState.CANCELLED);
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.state()).isEqualTo(TaskState.CANCELLED);
    }

    @Test
    void idempotencyKeyReturnsSameTaskResultWithoutSecondDispatch() {
        TaskControlApi.TaskResult first = run("agent", "hello", "same-key");
        TaskControlApi.TaskResult second = run("agent", "hello", "same-key");

        assertThat(second).isEqualTo(first);
        assertThat(engine.executions).hasSize(1);
        assertThat(service.tasks("tenant", "session")).hasSize(1);
    }

    @Test
    void idempotencyKeyDoesNotCollapseDifferentResumeTargets() {
        TaskControlApi.TaskResult first = run("agent", "first", null);
        service.markRunning(mark(first.taskId(), 1L, null, null, null)).toCompletableFuture().join();
        service.markWaiting(mark(first.taskId(), 2L, WaitingReason.USER_INPUT, null, "need-first"))
                .toCompletableFuture().join();

        TaskControlApi.TaskResult second = run("agent", "second", null);
        service.markRunning(mark(second.taskId(), 1L, null, null, null)).toCompletableFuture().join();
        service.markWaiting(mark(second.taskId(), 2L, WaitingReason.USER_INPUT, null, "need-second"))
                .toCompletableFuture().join();

        TaskControlApi.TaskResult firstResume = resume(first.taskId(), "agent", "answer-first", "same-resume-key");
        TaskControlApi.TaskResult secondResume = resume(second.taskId(), "agent", "answer-second", "same-resume-key");

        assertThat(firstResume.taskId()).isEqualTo(first.taskId());
        assertThat(secondResume.taskId()).isEqualTo(second.taskId());
        assertThat(engine.resumes).hasSize(2);
    }

    @Test
    void rejectedEngineDispatchMarksTaskFailed() {
        engine.status = EnqueueEngineStatus.FAILED;

        TaskControlApi.TaskResult result = run("agent", "hello", null);
        Task task = service.findTask("tenant", "session", result.taskId()).orElseThrow();

        assertThat(result.accepted()).isFalse();
        assertThat(result.state()).isEqualTo(TaskState.FAILED);
        assertThat(task.getFailureCode()).isEqualTo(TaskFailureCode.ENGINE_DISPATCH_REJECTED);
    }

    private TaskControlApi.TaskResult run(String agentId, String input, String idempotencyKey) {
        return service.run(new TaskControlApi.RunCommand(request(agentId, input, idempotencyKey)))
                .toCompletableFuture().join();
    }

    private TaskControlApi.TaskResult resume(String taskId, String agentId, String input, String idempotencyKey) {
        return service.resume(new TaskControlApi.ResumeCommand(taskId,
                request(agentId, input, idempotencyKey)))
                .toCompletableFuture().join();
    }

    private TaskControlApi.TaskResult cancel(String taskId, String agentId) {
        return service.cancel(new TaskControlApi.CancelCommand(
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

    private TaskControlApi.MarkTaskCommand mark(String taskId, long expectedRevision,
                                                   WaitingReason waitingReason,
                                                   TaskFailureCode failureCode,
                                                   Object detail) {
        return new TaskControlApi.MarkTaskCommand(
                "tenant",
                "session",
                taskId,
                expectedRevision,
                waitingReason,
                failureCode,
                detail,
                Map.of());
    }

    private static final class RecordingEngineDispatchApi implements EngineExecutionApi {
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

package com.huawei.ascend.service.taskcontrol;

import com.huawei.ascend.service.engine.api.EngineDispatchApi;
import com.huawei.ascend.service.engine.api.EnqueueEngineCancelRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineExecutionRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineResumeRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineStatus;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineInput;
import com.huawei.ascend.service.schema.AgentRequest;
import com.huawei.ascend.service.schema.Message;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient;
import com.huawei.ascend.service.queue.QueueFactory;
import com.huawei.ascend.service.queue.QueueManager;
import com.huawei.ascend.service.queue.InternalEventQueue;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class TaskControlService implements TaskControlClient {

    private final QueueManager queueManager;
    private final Supplier<EngineDispatchApi> engineDispatchApi;
    private final Clock clock;
    private final ConcurrentMap<SessionKey, Object> sessionLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<IdempotencyKey, TaskResult> idempotencyResults = new ConcurrentHashMap<>();
    private final Object queueCreationLock = new Object();

    public TaskControlService(QueueManager queueManager, EngineDispatchApi engineDispatchApi) {
        this(queueManager, () -> engineDispatchApi, Clock.systemUTC());
    }

    public TaskControlService(QueueManager queueManager, EngineDispatchApi engineDispatchApi, Clock clock) {
        this(queueManager, () -> engineDispatchApi, clock);
    }

    public TaskControlService(QueueManager queueManager, Supplier<EngineDispatchApi> engineDispatchApi, Clock clock) {
        this.queueManager = Objects.requireNonNull(queueManager, "queueManager");
        this.engineDispatchApi = Objects.requireNonNull(engineDispatchApi, "engineDispatchApi");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletionStage<TaskResult> run(RunCommand command) {
        Objects.requireNonNull(command, "command");
        TaskResult result = submit("RUN", null, command.request(), false);
        return CompletableFuture.completedStage(result);
    }

    @Override
    public CompletionStage<TaskResult> resume(ResumeCommand command) {
        Objects.requireNonNull(command, "command");
        TaskResult result = submit("RESUME_INPUT", command.taskId(), command.request(), true);
        return CompletableFuture.completedStage(result);
    }

    @Override
    public CompletionStage<TaskResult> cancel(CancelCommand command) {
        Objects.requireNonNull(command, "command");
        TaskResult result = cancelTask(command);
        return CompletableFuture.completedStage(result);
    }

    @Override
    public CompletionStage<TaskResult> markRunning(MarkTaskCommand command) {
        return CompletableFuture.completedStage(mark(command, TaskState.RUNNING,
                null, null, command.detail(), "marked running"));
    }

    @Override
    public CompletionStage<TaskResult> markWaiting(MarkTaskCommand command) {
        WaitingReason reason = Objects.requireNonNull(command.waitingReason(), "waitingReason");
        return CompletableFuture.completedStage(mark(command, TaskState.WAITING,
                reason, null, command.detail(), "marked waiting"));
    }

    @Override
    public CompletionStage<TaskResult> markSucceeded(MarkTaskCommand command) {
        return CompletableFuture.completedStage(mark(command, TaskState.COMPLETED,
                null, null, command.detail(), "marked succeeded"));
    }

    @Override
    public CompletionStage<TaskResult> markFailed(MarkTaskCommand command) {
        TaskFailureCode code = command.failureCode() == null ? TaskFailureCode.RUNTIME_ERROR : command.failureCode();
        return CompletableFuture.completedStage(mark(command, TaskState.FAILED,
                null, code, command.detail(), "marked failed"));
    }

    @Override
    public CompletionStage<TaskResult> markCancelled(MarkTaskCommand command) {
        TaskFailureCode code = command.failureCode() == null
                ? TaskFailureCode.CANCELLED_BY_RUNTIME
                : command.failureCode();
        return CompletableFuture.completedStage(mark(command, TaskState.CANCELLED,
                null, code, command.detail(), "marked cancelled"));
    }

    public Optional<Task> findTask(String tenantId, String sessionId, String taskId) {
        return internalEventQueue(tenantId, sessionId).find(task -> taskId.equals(task.getTaskId()));
    }

    public Optional<Task> findCurrentTask(String tenantId, String sessionId) {
        return latest(internalEventQueue(tenantId, sessionId).snapshot().stream()
                .filter(this::attachable));
    }

    public List<Task> tasks(String tenantId, String sessionId) {
        return internalEventQueue(tenantId, sessionId).snapshot();
    }

    private TaskResult submit(String action, String taskId, AgentRequest request, boolean resumeOnly) {
        Optional<IdempotencyKey> key = idempotencyKey(action, taskId, request);
        if (key.isEmpty()) {
            return prepareAndDispatch(request, taskId, resumeOnly);
        }
        return idempotencyResults.computeIfAbsent(key.get(),
                ignored -> prepareAndDispatch(request, taskId, resumeOnly));
    }

    private TaskResult prepareAndDispatch(AgentRequest request, String taskId, boolean resumeOnly) {
        PreparedTaskResult prepared = prepare(request, taskId, resumeOnly);
        return dispatchPrepared(prepared.task().taskId(), request, prepared.resume());
    }

    private PreparedTaskResult prepare(AgentRequest request, String taskId, boolean resumeOnly) {
        Task task;
        boolean resume;
        synchronized (sessionLock(request.tenantId(), request.sessionId())) {
            Optional<Task> selected = resumeOnly
                    ? selectTarget(request, taskId, true)
                    : Optional.empty();
            if (selected.isEmpty() && resumeOnly) {
                task = createTask(request);
                resume = false;
            } else {
                task = selected.orElseGet(() -> createTask(request));
                resume = resumeOnly && task.getState() == TaskState.WAITING;
            }
            String message = resume ? "resume prepared" : "execution prepared";
            return new PreparedTaskResult(result(task, true, message), resume);
        }
    }

    private TaskResult dispatchPrepared(String taskId, AgentRequest request, boolean resume) {
        Task task;
        synchronized (sessionLock(request.tenantId(), request.sessionId())) {
            task = findTask(request.tenantId(), request.sessionId(), taskId)
                    .orElseThrow(() -> new IllegalArgumentException("task not found: " + taskId));
        }
        return dispatch(task, request, resume);
    }

    private TaskResult cancelTask(CancelCommand command) {
        Task task;
        synchronized (sessionLock(command.tenantId(), command.sessionId())) {
            task = findTask(command.tenantId(), command.sessionId(), command.taskId())
                    .orElseThrow(() -> new IllegalArgumentException("task not found: " + command.taskId()));
            if (task.terminal()) {
                return result(task, false, "terminal task cannot be cancelled");
            }
            if (task.getState() != TaskState.CANCELLING) {
                task.transitionTo(TaskState.CANCELLING, null, null, command.reason(), clock.instant());
            }
        }
        EnqueueEngineStatus status = engineDispatchApi().enqueueCancel(
                new EnqueueEngineCancelRequest(scopeFor(task, userId(command.userId()))));
        if (status == EnqueueEngineStatus.FAILED) {
            return failDispatch(task, TaskFailureCode.ENGINE_DISPATCH_REJECTED, "engine rejected cancel");
        }
        return currentResult(task, true, "cancel enqueued");
    }

    private TaskResult dispatch(Task task, AgentRequest request, boolean resume) {
        EnqueueEngineStatus status;
        try {
            EngineExecutionScope scope = scopeFor(task, userId(request.userId()));
            EngineInput input = engineInput(request.input(), resume ? "RESUME_SIGNAL" : "USER_MESSAGE");
            status = resume
                    ? engineDispatchApi().enqueueResume(new EnqueueEngineResumeRequest(scope, input))
                    : engineDispatchApi().enqueueExecution(new EnqueueEngineExecutionRequest(scope, input));
        } catch (RuntimeException e) {
            TaskFailureCode code = task.getAgentId() == null || task.getAgentId().isBlank()
                    ? TaskFailureCode.AGENT_ID_INVALID
                    : TaskFailureCode.ENGINE_DISPATCH_REJECTED;
            return failDispatch(task, code, e.getMessage());
        }
        if (status == EnqueueEngineStatus.FAILED) {
            return failDispatch(task, TaskFailureCode.ENGINE_DISPATCH_REJECTED, "engine rejected dispatch");
        }
        return currentResult(task, true, resume ? "resume enqueued" : "execution enqueued");
    }

    private TaskResult failDispatch(Task task, TaskFailureCode code, Object detail) {
        synchronized (sessionLock(task.getTenantId(), task.getSessionId())) {
            if (!task.terminal()) {
                task.transitionTo(TaskState.FAILED, null, code, detail, clock.instant());
            }
            return result(task, false, "engine dispatch failed");
        }
    }

    private TaskResult mark(MarkTaskCommand command, TaskState nextState, WaitingReason waitingReason,
                            TaskFailureCode failureCode, Object detail, String message) {
        synchronized (sessionLock(command.tenantId(), command.sessionId())) {
            Task task = findTask(command.tenantId(), command.sessionId(), command.taskId())
                    .orElseThrow(() -> new IllegalArgumentException("task not found: " + command.taskId()));
            if (task.getRevision() != command.expectedRevision()) {
                return result(task, false, "stale task revision");
            }
            if (!allowed(task.getState(), nextState)) {
                return result(task, false, "transition rejected");
            }
            if (task.getState() != nextState || waitingReason != task.getWaitingReason()
                    || failureCode != task.getFailureCode() || detail != task.getDetail()) {
                task.transitionTo(nextState, waitingReason, failureCode, detail, clock.instant());
            }
            return result(task, true, message);
        }
    }

    private Optional<Task> selectTarget(AgentRequest request, String taskId, boolean resumeOnly) {
        if (taskId != null && !taskId.isBlank()) {
            return findTask(request.tenantId(), request.sessionId(), taskId)
                    .filter(task -> resumeOnly ? task.getState() == TaskState.WAITING : attachable(task));
        }
        if (resumeOnly) {
            return latest(internalEventQueue(request.tenantId(), request.sessionId()).snapshot().stream()
                    .filter(task -> task.getState() == TaskState.WAITING));
        }
        return findCurrentTask(request.tenantId(), request.sessionId());
    }

    private Task createTask(AgentRequest request) {
        Task task = Task.created(request.tenantId(), request.sessionId(), request.agentId(), clock.instant());
        task.setDetail(request.input());
        internalEventQueue(request.tenantId(), request.sessionId()).offer(task);
        return task;
    }

    private boolean attachable(Task task) {
        return !task.terminal() && task.getState() != TaskState.CANCELLING;
    }

    private Optional<Task> latest(java.util.stream.Stream<Task> tasks) {
        return tasks.max(Comparator.comparing(Task::getUpdatedAt).thenComparing(Task::getTaskId));
    }

    private boolean allowed(TaskState current, TaskState next) {
        if (current == next) {
            return true;
        }
        if (current.isTerminal()) {
            return false;
        }
        return switch (current) {
            case CREATED -> next == TaskState.RUNNING || next == TaskState.CANCELLING || next == TaskState.FAILED;
            case RUNNING -> next == TaskState.WAITING || next == TaskState.COMPLETED
                    || next == TaskState.FAILED || next == TaskState.CANCELLING || next == TaskState.CANCELLED;
            case WAITING -> next == TaskState.RUNNING || next == TaskState.FAILED
                    || next == TaskState.CANCELLING || next == TaskState.CANCELLED;
            case PAUSED -> next == TaskState.RUNNING || next == TaskState.FAILED || next == TaskState.CANCELLING;
            case CANCELLING -> next == TaskState.CANCELLED || next == TaskState.FAILED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }

    @SuppressWarnings("unchecked")
    private InternalEventQueue<Task> internalEventQueue(String tenantId, String sessionId) {
        Optional<InternalEventQueue<?>> existing = queueManager.findBySession(tenantId, sessionId);
        if (existing.isPresent()) {
            return (InternalEventQueue<Task>) existing.get();
        }
        synchronized (queueCreationLock) {
            return (InternalEventQueue<Task>) queueManager.findBySession(tenantId, sessionId)
                    .orElseGet(() -> QueueFactory.inMemorySessionQueue(tenantId, sessionId, queueManager));
        }
    }

    private Object sessionLock(String tenantId, String sessionId) {
        return sessionLocks.computeIfAbsent(new SessionKey(tenantId, sessionId), ignored -> new Object());
    }

    private EngineExecutionScope scopeFor(Task task, String userId) {
        return new EngineExecutionScope(task.getTenantId(), userId, task.getSessionId(),
                task.getTaskId(), task.getAgentId() == null ? "" : task.getAgentId());
    }

    private EngineDispatchApi engineDispatchApi() {
        EngineDispatchApi api = engineDispatchApi.get();
        if (api == null) {
            throw new IllegalStateException("engine dispatch api is not available");
        }
        return api;
    }

    private String userId(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @SuppressWarnings("unchecked")
    private EngineInput engineInput(Object input, String inputType) {
        if (input instanceof EngineInput engineInput) {
            return engineInput;
        }
        if (input instanceof List<?> list && list.stream().allMatch(e -> e instanceof Message)) {
            return new EngineInput(inputType, (List<Message>) list, Map.of());
        }
        if (input instanceof Message message) {
            return new EngineInput(inputType, List.of(message), Map.of());
        }
        if (input instanceof Map<?, ?> map) {
            return new EngineInput(inputType, List.of(), Map.copyOf((Map<String, Object>) map));
        }
        return new EngineInput(inputType,
                List.of(Message.user(input == null ? "" : input.toString())),
                Map.of());
    }

    private TaskResult currentResult(Task task, boolean accepted, String message) {
        synchronized (sessionLock(task.getTenantId(), task.getSessionId())) {
            return result(task, accepted, message);
        }
    }

    private TaskResult result(Task task, boolean accepted, String message) {
        return new TaskResult(task.getTenantId(), task.getSessionId(), task.getTaskId(),
                task.getState(), task.getRevision(), accepted, message);
    }

    private Optional<IdempotencyKey> idempotencyKey(String action, String taskId, AgentRequest request) {
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new IdempotencyKey(request.tenantId(), request.sessionId(),
                taskId, request.agentId(), action, request.idempotencyKey()));
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record SessionKey(String tenantId, String sessionId) {
        private SessionKey {
            tenantId = requireNonBlank(tenantId, "tenantId");
            sessionId = requireNonBlank(sessionId, "sessionId");
        }
    }

    private record IdempotencyKey(
            String tenantId,
            String sessionId,
            String taskId,
            String agentId,
            String action,
            String idempotencyKey) {
    }

    private record PreparedTaskResult(TaskResult task, boolean resume) {
        private PreparedTaskResult {
            task = Objects.requireNonNull(task, "task");
        }
    }
}

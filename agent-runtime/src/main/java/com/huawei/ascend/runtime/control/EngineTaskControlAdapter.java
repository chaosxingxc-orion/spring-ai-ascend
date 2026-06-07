package com.huawei.ascend.runtime.control;

import com.huawei.ascend.runtime.engine.event.EngineCancelledEvent;
import com.huawei.ascend.runtime.engine.event.EngineCompletedEvent;
import com.huawei.ascend.runtime.engine.event.EngineFailedEvent;
import com.huawei.ascend.runtime.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.runtime.engine.event.EngineOutputEvent;
import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.model.InterruptType;
import com.huawei.ascend.runtime.engine.port.AccessLayerClient;
import com.huawei.ascend.runtime.control.api.TaskControlApi.MarkTaskCommand;
import com.huawei.ascend.runtime.control.api.TaskControlApi.TaskResult;

import java.util.Map;
import java.util.Objects;

/**
 * The single authority on the engine's outbound port: every engine outcome is applied
 * to {@link TaskControlService} (the authoritative task record) FIRST, and caller-facing
 * egress is driven from here only when control ACCEPTED the transition. This makes the
 * control plane the sole writer of authority and gates output on it — the engine never
 * writes authority and output independently (no double-write of authority).
 */
public class EngineTaskControlAdapter implements com.huawei.ascend.runtime.engine.port.TaskControlClient {

    private final TaskControlService taskControlService;
    private final AccessLayerClient accessLayerClient;

    public EngineTaskControlAdapter(TaskControlService taskControlService, AccessLayerClient accessLayerClient) {
        this.taskControlService = Objects.requireNonNull(taskControlService, "taskControlService");
        this.accessLayerClient = Objects.requireNonNull(accessLayerClient, "accessLayerClient");
    }

    @Override
    public void markRunning(EngineExecutionScope scope) {
        taskControlService.markRunning(command(scope, null, null, null, Map.of())).toCompletableFuture().join();
    }

    @Override
    public void appendOutput(EngineExecutionScope scope, EngineOutputEvent event) {
        // Streaming chunk: forward to egress only while control still considers the task live,
        // so no output escapes after the authoritative task has terminated or is cancelling.
        boolean live = taskControlService.findTask(scope.tenantId(), scope.sessionId(), scope.taskId())
                .map(task -> !task.terminal() && task.getState() != TaskState.CANCELLING)
                .orElse(false);
        if (live) {
            accessLayerClient.appendOutput(scope, event);
        }
    }

    @Override
    public void markWaiting(EngineExecutionScope scope, EngineInterruptedEvent event) {
        TaskResult result = taskControlService
                .markWaiting(command(scope, waitingReason(event), null, event, Map.of()))
                .toCompletableFuture().join();
        if (result.accepted()
                && (event == null || event.getInterruptType() != InterruptType.WAITING_CHILD_AGENT)) {
            accessLayerClient.requestUserInput(scope, event);
        }
    }

    @Override
    public void markSucceeded(EngineExecutionScope scope, EngineCompletedEvent event) {
        TaskResult result = taskControlService
                .markSucceeded(command(scope, null, null, event, Map.of()))
                .toCompletableFuture().join();
        if (result.accepted()) {
            accessLayerClient.completeOutput(scope, event);
        }
    }

    @Override
    public void markFailed(EngineExecutionScope scope, EngineFailedEvent event) {
        TaskResult result = taskControlService
                .markFailed(command(scope, null, failureCode(event), event, Map.of()))
                .toCompletableFuture().join();
        if (result.accepted()) {
            accessLayerClient.failOutput(scope, event);
        }
    }

    @Override
    public void markCancelled(EngineExecutionScope scope, EngineCancelledEvent event) {
        taskControlService.markCancelled(command(scope, null, TaskFailureCode.CANCELLED_BY_RUNTIME, event, Map.of()))
                .toCompletableFuture().join();
    }

    private MarkTaskCommand command(EngineExecutionScope scope, WaitingReason waitingReason,
                                    TaskFailureCode failureCode, Object detail, Map<String, Object> metadata) {
        Task task = taskControlService.findTask(scope.tenantId(), scope.sessionId(), scope.taskId())
                .orElseThrow(() -> new IllegalArgumentException("task not found: " + scope.taskId()));
        return new MarkTaskCommand(scope.tenantId(), scope.sessionId(), scope.taskId(), task.getRevision(),
                waitingReason, failureCode, detail, metadata);
    }

    private WaitingReason waitingReason(EngineInterruptedEvent event) {
        if (event == null || event.getInterruptType() == null) {
            return WaitingReason.USER_INPUT;
        }
        InterruptType type = event.getInterruptType();
        return switch (type) {
            case HUMAN_INPUT -> WaitingReason.USER_INPUT;
            case APPROVAL -> WaitingReason.USER_CONFIRMATION;
            case WAITING_CHILD_AGENT -> WaitingReason.DEPENDENCY;
        };
    }

    private TaskFailureCode failureCode(EngineFailedEvent event) {
        if (event == null || event.getErrorCode() == null || event.getErrorCode().isBlank()) {
            return TaskFailureCode.RUNTIME_ERROR;
        }
        String normalized = event.getErrorCode().trim().toUpperCase();
        return switch (normalized) {
            case "AGENT_ID_INVALID" -> TaskFailureCode.AGENT_ID_INVALID;
            case "OUT_OF_DOMAIN" -> TaskFailureCode.OUT_OF_DOMAIN;
            case "NOT_CURRENT_TASK" -> TaskFailureCode.NOT_CURRENT_TASK;
            case "ENGINE_DISPATCH_REJECTED" -> TaskFailureCode.ENGINE_DISPATCH_REJECTED;
            case "CANCELLED_BY_RUNTIME" -> TaskFailureCode.CANCELLED_BY_RUNTIME;
            default -> TaskFailureCode.RUNTIME_ERROR;
        };
    }
}

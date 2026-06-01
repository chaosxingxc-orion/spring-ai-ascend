package com.huawei.ascend.service.taskflow.control;

import com.huawei.ascend.service.engine.event.EngineCancelledEvent;
import com.huawei.ascend.service.engine.event.EngineCompletedEvent;
import com.huawei.ascend.service.engine.event.EngineFailedEvent;
import com.huawei.ascend.service.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.InterruptType;
import com.huawei.ascend.service.taskflow.control.api.TaskControlClient.MarkTaskCommand;

import java.util.Map;
import java.util.Objects;

public class EngineTaskControlAdapter implements com.huawei.ascend.service.engine.spi.TaskControlClient {

    private final TaskControlService taskControlService;

    public EngineTaskControlAdapter(TaskControlService taskControlService) {
        this.taskControlService = Objects.requireNonNull(taskControlService, "taskControlService");
    }

    @Override
    public void markRunning(EngineExecutionScope scope) {
        taskControlService.markRunning(command(scope, null, null, null, Map.of())).toCompletableFuture().join();
    }

    @Override
    public void markWaiting(EngineExecutionScope scope, EngineInterruptedEvent event) {
        taskControlService.markWaiting(command(scope, waitingReason(event), null, event, Map.of())).toCompletableFuture().join();
    }

    @Override
    public void markSucceeded(EngineExecutionScope scope, EngineCompletedEvent event) {
        taskControlService.markSucceeded(command(scope, null, null, event, Map.of())).toCompletableFuture().join();
    }

    @Override
    public void markFailed(EngineExecutionScope scope, EngineFailedEvent event) {
        taskControlService.markFailed(command(scope, null, failureCode(event), event, Map.of())).toCompletableFuture().join();
    }

    @Override
    public void markCancelled(EngineExecutionScope scope, EngineCancelledEvent event) {
        taskControlService.markCancelled(command(scope, null, TaskFailureCode.CANCELLED_BY_RUNTIME, event, Map.of()))
                .toCompletableFuture().join();
    }

    @Override
    public EngineExecutionScope createChildTask(EngineExecutionScope parentScope, String targetAgentId, String input) {
        return taskControlService.createChildTask(parentScope, targetAgentId, input);
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

package com.huawei.ascend.runtime.access;

import com.huawei.ascend.runtime.common.AgentRequest;
import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.common.Role;
import com.huawei.ascend.runtime.common.Timing;
import com.huawei.ascend.runtime.session.api.SessionManager;
import com.huawei.ascend.runtime.session.Session;
import com.huawei.ascend.runtime.control.api.TaskControlApi;
import com.huawei.ascend.runtime.control.api.TaskControlApi.CancelCommand;
import com.huawei.ascend.runtime.control.api.TaskControlApi.ResumeCommand;
import com.huawei.ascend.runtime.control.api.TaskControlApi.RunCommand;
import com.huawei.ascend.runtime.control.api.TaskControlApi.TaskResult;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves access sessions before submitting normalized requests into task control.
 */
public final class AccessSubmissionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessSubmissionService.class);

    private final TaskControlApi taskControlClient;
    private final SessionManager sessionManager;

    public AccessSubmissionService(
            TaskControlApi taskControlClient,
            SessionManager sessionManager) {
        this.taskControlClient = Objects.requireNonNull(taskControlClient, "taskControlClient");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
    }

    public CompletionStage<AccessAcceptedResponse> run(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        long startedNanos = System.nanoTime();
        AgentRequest resolved = resolveSession(request);
        LOGGER.info("access resolved session tenantId={} userId={} agentId={} requestedSessionId={} resolvedSessionId={}",
                request.tenantId(),
                request.userId(),
                request.agentId(),
                request.sessionId(),
                resolved.sessionId());
        return taskControlClient.run(new RunCommand(resolved))
                .thenApply(result -> {
                    LOGGER.info("trace stage=access-run tenantId={} userId={} agentId={} sessionId={} taskId={} accepted={} durationMs={}",
                            resolved.tenantId(),
                            resolved.userId(),
                            resolved.agentId(),
                            resolved.sessionId(),
                            result.taskId(),
                            result.accepted(),
                            Timing.elapsedMs(startedNanos));
                    return toAccepted(resolved, result);
                });
    }

    public CompletionStage<AccessAcceptedResponse> resume(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        long startedNanos = System.nanoTime();
        AgentRequest resolved = resolveSession(request);
        return taskControlClient.resume(new ResumeCommand(null, resolved))
                .thenApply(result -> {
                    LOGGER.info("trace stage=access-resume tenantId={} userId={} agentId={} sessionId={} taskId={} accepted={} durationMs={}",
                            resolved.tenantId(),
                            resolved.userId(),
                            resolved.agentId(),
                            resolved.sessionId(),
                            result.taskId(),
                            result.accepted(),
                            Timing.elapsedMs(startedNanos));
                    return toAccepted(resolved, result);
                });
    }

    public CompletionStage<AccessAcceptedResponse> cancel(AccessCancelCommand command) {
        Objects.requireNonNull(command, "command");
        long startedNanos = System.nanoTime();
        CancelCommand cancelCommand = new CancelCommand(
                command.tenantId(),
                command.userId(),
                command.agentId(),
                command.sessionId(),
                command.taskId(),
                command.reason(),
                command.metadata());
        return taskControlClient.cancel(cancelCommand).thenApply(result -> {
            LOGGER.info("trace stage=access-cancel tenantId={} userId={} agentId={} sessionId={} taskId={} accepted={} durationMs={}",
                    command.tenantId(),
                    command.userId(),
                    command.agentId(),
                    command.sessionId(),
                    result.taskId(),
                    result.accepted(),
                    Timing.elapsedMs(startedNanos));
            return toAccepted(command, result);
        });
    }

    private AgentRequest resolveSession(AgentRequest request) {
        Session session = sessionManager.loadOrCreate(
                request.tenantId(),
                request.userId(),
                request.agentId(),
                request.sessionId(),
                currentUserInput(request));
        return new AgentRequest(
                request.tenantId(),
                request.userId(),
                request.agentId(),
                session.sessionId(),
                request.input(),
                request.idempotencyKey(),
                request.metadata());
    }

    private List<Message> currentUserInput(AgentRequest request) {
        return request.input().stream()
                .filter(message -> message.role() == Role.USER)
                .toList();
    }

    private AccessAcceptedResponse toAccepted(AgentRequest request, TaskResult result) {
        return new AccessAcceptedResponse(
                result.tenantId(),
                request.userId(),
                request.agentId(),
                result.sessionId(),
                result.taskId(),
                result.accepted(),
                result.message());
    }

    private AccessAcceptedResponse toAccepted(AccessCancelCommand command, TaskResult result) {
        return new AccessAcceptedResponse(
                result.tenantId(),
                command.userId(),
                command.agentId(),
                result.sessionId(),
                result.taskId(),
                result.accepted(),
                result.message());
    }
}

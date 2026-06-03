package com.huawei.ascend.service.access.core;

import com.huawei.ascend.service.access.model.AccessAcceptedResponse;
import com.huawei.ascend.service.access.model.AccessCancelCommand;
import com.huawei.ascend.service.schema.AgentRequest;
import com.huawei.ascend.service.schema.Message;
import com.huawei.ascend.service.schema.Role;
import com.huawei.ascend.service.session.api.SessionManager;
import com.huawei.ascend.service.session.model.Session;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient.CancelCommand;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient.ResumeCommand;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient.RunCommand;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient.TaskResult;
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

    private final TaskControlClient taskControlClient;
    private final SessionManager sessionManager;

    public AccessSubmissionService(
            TaskControlClient taskControlClient,
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
                            elapsedMs(startedNanos));
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
                            elapsedMs(startedNanos));
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
                    elapsedMs(startedNanos));
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

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}

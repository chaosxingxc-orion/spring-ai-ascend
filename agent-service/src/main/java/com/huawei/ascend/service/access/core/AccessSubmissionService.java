package com.huawei.ascend.service.access.core;

import com.huawei.ascend.service.access.egress.EgressBindingFactory;
import com.huawei.ascend.service.access.egress.EgressDispatcher;
import com.huawei.ascend.service.access.egress.EgressQueueRegistry;
import com.huawei.ascend.service.access.model.AccessAcceptedResponse;
import com.huawei.ascend.service.access.model.AccessCancelCommand;
import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.ReplyContext;
import com.huawei.ascend.service.schema.AgentRequest;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient.CancelCommand;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient.ResumeCommand;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient.RunCommand;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient.TaskResult;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Submits normalized access requests into task control and binds the session
 * reply channel before dispatch.
 */
public final class AccessSubmissionService {

    private final TaskControlClient taskControlClient;
    private final EgressQueueRegistry egressQueueRegistry;
    private final EgressDispatcher egressDispatcher;

    public AccessSubmissionService(
            TaskControlClient taskControlClient,
            EgressQueueRegistry egressQueueRegistry,
            EgressDispatcher egressDispatcher) {
        this.taskControlClient = Objects.requireNonNull(taskControlClient, "taskControlClient");
        this.egressQueueRegistry = Objects.requireNonNull(egressQueueRegistry, "egressQueueRegistry");
        this.egressDispatcher = Objects.requireNonNull(egressDispatcher, "egressDispatcher");
    }

    public CompletionStage<AccessAcceptedResponse> run(AgentRequest request, ReplyContext reply) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reply, "reply");
        bindEgress(request, reply);
        return taskControlClient.run(new RunCommand(request))
                .thenApply(result -> toAccepted(request, result));
    }

    public CompletionStage<AccessAcceptedResponse> resume(AgentRequest request, ReplyContext reply) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reply, "reply");
        bindEgress(request, reply);
        return taskControlClient.resume(new ResumeCommand(null, request))
                .thenApply(result -> toAccepted(request, result));
    }

    public CompletionStage<AccessAcceptedResponse> cancel(AccessCancelCommand command) {
        Objects.requireNonNull(command, "command");
        CancelCommand cancelCommand = new CancelCommand(
                command.tenantId(),
                command.userId(),
                command.agentId(),
                command.sessionId(),
                command.taskId(),
                command.reason(),
                command.metadata());
        return taskControlClient.cancel(cancelCommand).thenApply(result -> toAccepted(command, result));
    }

    private void bindEgress(AgentRequest request, ReplyContext reply) {
        EgressBinding binding = EgressBindingFactory.from(request, reply);
        egressQueueRegistry.getOrCreate(binding);
        egressDispatcher.start(binding);
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

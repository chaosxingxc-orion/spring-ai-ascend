package com.huawei.ascend.service.access.core;

import com.huawei.ascend.service.access.model.AccessAcceptedResponse;
import com.huawei.ascend.service.access.model.AccessCancelCommand;
import com.huawei.ascend.service.access.model.ReplyContext;
import com.huawei.ascend.service.access.protocol.a2a.A2aEnvelope;
import com.huawei.ascend.service.access.protocol.async.AsyncEnvelope;
import com.huawei.ascend.service.schema.AgentRequest;
import com.huawei.ascend.service.schema.Message;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class AccessGateway {

    private final AccessSubmissionService submissionService;

    public AccessGateway(AccessSubmissionService submissionService) {
        this.submissionService = Objects.requireNonNull(submissionService, "submissionService");
    }

    public CompletionStage<AccessAcceptedResponse> submitA2a(A2aEnvelope envelope) {
        return submitA2a(envelope, false);
    }

    public CompletionStage<AccessAcceptedResponse> submitA2a(A2aEnvelope envelope, boolean streaming) {
        Objects.requireNonNull(envelope, "envelope");
        AgentRequest request = toA2aAgentRequest(envelope);
        ReplyContext reply = a2aReplyContext(envelope, streaming);
        return run(request, reply);
    }

    public CompletionStage<AccessAcceptedResponse> submitAsync(AsyncEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        AgentRequest request = toAsyncAgentRequest(envelope);
        ReplyContext reply = ReplyContext.async(
                envelope.headers().replyTopic(),
                envelope.headers().correlationId(),
                Map.of("payload", envelope.body().payload()));
        return run(request, reply);
    }

    public CompletionStage<AccessAcceptedResponse> cancelA2a(A2aEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        return submissionService.cancel(toA2aCancelCommand(envelope));
    }

    private CompletionStage<AccessAcceptedResponse> run(AgentRequest request, ReplyContext reply) {
        return submissionService.run(request, reply);
    }

    private AgentRequest toA2aAgentRequest(A2aEnvelope envelope) {
        A2aEnvelope.A2aContext context = envelope.context();
        A2aEnvelope.A2aMessage message = envelope.message();
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("parts", message == null ? List.of() : message.parts());
        metadata.put("metadata", message == null ? Map.of() : message.metadata());
        metadata.put("contextId", context.contextId());
        metadata.put("correlationId", context.correlationId());
        if (message != null) {
            metadata.putAll(message.metadata());
        }
        return new AgentRequest(
                context.tenantId(),
                context.userId(),
                context.agentId(),
                normalizeSessionId(context.sessionId(), context.contextId()),
                List.of(Message.user(message == null || message.text() == null ? "" : message.text())),
                context.idempotencyKey(),
                metadata);
    }

    private AgentRequest toAsyncAgentRequest(AsyncEnvelope envelope) {
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("payload", envelope.body().payload());
        metadata.put("replyTopic", envelope.headers().replyTopic());
        metadata.put("correlationId", envelope.headers().correlationId());
        return new AgentRequest(
                envelope.headers().tenantId(),
                envelope.headers().userId(),
                envelope.headers().agentId(),
                normalizeSessionId(envelope.headers().sessionId(), envelope.headers().correlationId()),
                List.of(Message.user(envelope.body().query() == null ? "" : envelope.body().query())),
                envelope.headers().idempotencyKey(),
                metadata);
    }

    private AccessCancelCommand toA2aCancelCommand(A2aEnvelope envelope) {
        A2aEnvelope.A2aContext context = envelope.context();
        Map<String, Object> metadata = envelope.message() == null ? Map.of() : envelope.message().metadata();
        return new AccessCancelCommand(
                context.tenantId(),
                context.userId(),
                context.agentId(),
                normalizeSessionId(context.sessionId(), context.contextId()),
                taskId(envelope),
                null,
                metadata);
    }

    private ReplyContext a2aReplyContext(A2aEnvelope envelope, boolean streaming) {
        HashMap<String, Object> attributes = new HashMap<>();
        A2aEnvelope.A2aMessage message = envelope.message();
        attributes.put("parts", message == null ? List.of() : message.parts());
        attributes.put("metadata", message == null ? Map.of() : message.metadata());
        attributes.put("contextId", envelope.context().contextId());
        return ReplyContext.a2a(
                streaming,
                envelope.context().correlationId(),
                envelope.pushNotificationConfig(),
                attributes);
    }

    private static String taskId(A2aEnvelope envelope) {
        if (envelope.message() == null || envelope.message().metadata() == null) {
            return null;
        }
        Object taskId = envelope.message().metadata().get("taskId");
        return taskId == null ? null : taskId.toString();
    }

    private static String normalizeSessionId(String sessionId, String fallback) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return java.util.UUID.randomUUID().toString();
    }
}

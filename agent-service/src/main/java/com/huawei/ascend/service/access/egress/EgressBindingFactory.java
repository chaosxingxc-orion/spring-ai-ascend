package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.ReplyChannel;
import com.huawei.ascend.service.access.model.ReplyContext;
import com.huawei.ascend.service.access.protocol.a2a.A2aEnvelope;
import com.huawei.ascend.service.schema.AgentRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class EgressBindingFactory {

    private EgressBindingFactory() {
    }

    public static EgressBinding from(AgentRequest request, ReplyContext reply) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reply, "reply");
        ReplyChannel replyChannel = resolveReplyChannel(reply);
        String deliveryMode = resolveDeliveryMode(reply, replyChannel);
        return new EgressBinding(
                request.tenantId(),
                request.sessionId(),
                reply.replyId(),
                replyChannel,
                deliveryMode,
                resolveTargetRef(reply, replyChannel),
                reply.correlationId(),
                resolveAttributes(reply, deliveryMode));
    }

    private static ReplyChannel resolveReplyChannel(ReplyContext reply) {
        return reply.replyTopic() == null || reply.replyTopic().isBlank()
                ? ReplyChannel.A2A
                : ReplyChannel.ASYNC;
    }

    private static String resolveTargetRef(ReplyContext reply, ReplyChannel replyChannel) {
        if (replyChannel == ReplyChannel.ASYNC) {
            return reply.replyTopic();
        }
        A2aEnvelope.A2aPushNotificationConfig pushConfig = reply.a2aPushNotificationConfig();
        if (pushConfig != null && pushConfig.url() != null && !pushConfig.url().isBlank()) {
            return pushConfig.url();
        }
        return reply.a2aStreaming() ? "sse" : null;
    }

    private static String resolveDeliveryMode(ReplyContext reply, ReplyChannel replyChannel) {
        if (replyChannel == ReplyChannel.ASYNC) {
            return ReplyChannel.ASYNC.name();
        }
        A2aEnvelope.A2aPushNotificationConfig pushConfig = reply.a2aPushNotificationConfig();
        if (pushConfig != null && pushConfig.url() != null && !pushConfig.url().isBlank()) {
            return "PUSH_NOTIFICATION";
        }
        return reply.a2aStreaming() ? "STREAM" : "SYNC";
    }

    private static Map<String, Object> resolveAttributes(ReplyContext reply, String deliveryMode) {
        if (!"PUSH_NOTIFICATION".equals(deliveryMode)) {
            return Map.of();
        }
        A2aEnvelope.A2aPushNotificationConfig pushConfig = reply.a2aPushNotificationConfig();
        if (pushConfig == null) {
            return Map.of();
        }
        HashMap<String, Object> attributes = new HashMap<>();
        putIfPresent(attributes, "pushNotificationConfigId", pushConfig.id());
        putIfPresent(attributes, "pushNotificationTaskId", pushConfig.taskId());
        putIfPresent(attributes, "pushNotificationToken", pushConfig.token());
        putIfPresent(attributes, "pushNotificationAuthScheme", pushConfig.authScheme());
        putIfPresent(attributes, "pushNotificationAuthCredentials", pushConfig.authCredentials());
        putIfPresent(attributes, "pushNotificationTenant", pushConfig.tenant());
        return Collections.unmodifiableMap(attributes);
    }

    private static void putIfPresent(HashMap<String, Object> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}

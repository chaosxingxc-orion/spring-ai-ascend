package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.AccessIntent;
import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.ReplyChannel;
import com.huawei.ascend.service.access.protocol.a2a.A2aEnvelope;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the {@link EgressBinding} (reply channel, delivery mode, target ref,
 * correlation, push-notification attributes) for a request from its
 * {@link AccessIntent} payload.
 *
 * <p>Extracted so both {@code AccessGateway} and the task handler can build a
 * binding for the same task — the handler binds the reply queue <em>before</em>
 * dispatching, which the synchronous in-memory engine requires, and the gateway
 * re-binding afterwards is then idempotent.
 */
public final class EgressBindingFactory {

    private EgressBindingFactory() {
    }

    /** Builds the egress binding for {@code taskId} from the intent payload. */
    public static EgressBinding from(AccessIntent intent, String taskId) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(taskId, "taskId");
        ReplyChannel replyChannel = resolveReplyChannel(intent);
        String deliveryMode = resolveDeliveryMode(intent, replyChannel);
        String targetRef = resolveTargetRef(intent, replyChannel);
        String correlationId = resolveCorrelationId(intent);
        return new EgressBinding(
                intent.tenantId(),
                intent.sessionId(),
                taskId,
                replyChannel,
                deliveryMode,
                targetRef,
                correlationId,
                resolveAttributes(intent, deliveryMode));
    }

    private static ReplyChannel resolveReplyChannel(AccessIntent intent) {
        if (intent.payload() instanceof Map<?, ?> payload && payload.containsKey("replyTopic")) {
            return ReplyChannel.ASYNC;
        }
        return ReplyChannel.A2A;
    }

    private static String resolveTargetRef(AccessIntent intent, ReplyChannel replyChannel) {
        if (replyChannel == ReplyChannel.ASYNC && intent.payload() instanceof Map<?, ?> payload) {
            Object replyTopic = payload.get("replyTopic");
            return replyTopic == null ? null : replyTopic.toString();
        }
        if (intent.payload() instanceof Map<?, ?> payload) {
            A2aEnvelope.A2aPushNotificationConfig pushConfig = pushConfig(payload);
            if (pushConfig != null && pushConfig.url() != null && !pushConfig.url().isBlank()) {
                return pushConfig.url();
            }
            Object stream = payload.get("a2aStreaming");
            return Boolean.TRUE.equals(stream) ? "sse" : null;
        }
        return null;
    }

    private static String resolveDeliveryMode(AccessIntent intent, ReplyChannel replyChannel) {
        if (replyChannel == ReplyChannel.ASYNC) {
            return ReplyChannel.ASYNC.name();
        }
        if (intent.payload() instanceof Map<?, ?> payload) {
            A2aEnvelope.A2aPushNotificationConfig pushConfig = pushConfig(payload);
            if (pushConfig != null && pushConfig.url() != null && !pushConfig.url().isBlank()) {
                return "PUSH_NOTIFICATION";
            }
            Object stream = payload.get("a2aStreaming");
            return Boolean.TRUE.equals(stream) ? "STREAM" : "SYNC";
        }
        return "SYNC";
    }

    private static String resolveCorrelationId(AccessIntent intent) {
        if (intent.payload() instanceof Map<?, ?> payload) {
            Object correlationId = payload.get("correlationId");
            return correlationId == null ? null : correlationId.toString();
        }
        return null;
    }

    private static Map<String, Object> resolveAttributes(AccessIntent intent, String deliveryMode) {
        if (!"PUSH_NOTIFICATION".equals(deliveryMode) || !(intent.payload() instanceof Map<?, ?> payload)) {
            return Map.of();
        }
        A2aEnvelope.A2aPushNotificationConfig pushConfig = pushConfig(payload);
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

    private static A2aEnvelope.A2aPushNotificationConfig pushConfig(Map<?, ?> payload) {
        Object value = payload.get("a2aPushNotificationConfig");
        return value instanceof A2aEnvelope.A2aPushNotificationConfig config ? config : null;
    }

    private static void putIfPresent(HashMap<String, Object> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}

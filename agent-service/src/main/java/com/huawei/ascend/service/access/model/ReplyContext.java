package com.huawei.ascend.service.access.model;

import com.huawei.ascend.service.access.protocol.a2a.A2aEnvelope;
import java.util.Map;
import java.util.UUID;

public record ReplyContext(
        String replyId,
        String replyTopic,
        String correlationId,
        boolean a2aStreaming,
        A2aEnvelope.A2aPushNotificationConfig a2aPushNotificationConfig,
        Map<String, Object> attributes) {

    public ReplyContext {
        replyId = requireReplyId(replyId, correlationId);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ReplyContext a2a(
            boolean streaming,
            String correlationId,
            A2aEnvelope.A2aPushNotificationConfig pushNotificationConfig,
            Map<String, Object> attributes) {
        return new ReplyContext(correlationId, null, correlationId, streaming, pushNotificationConfig, attributes);
    }

    public static ReplyContext async(String replyTopic, String correlationId, Map<String, Object> attributes) {
        return new ReplyContext(correlationId, replyTopic, correlationId, false, null, attributes);
    }

    private static String requireReplyId(String replyId, String correlationId) {
        String value = replyId == null || replyId.isBlank() ? correlationId : replyId;
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value;
    }
}

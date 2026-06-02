package com.huawei.ascend.service.access.model;

import com.huawei.ascend.service.access.protocol.a2a.A2aEnvelope;
import java.util.Map;

public record ReplyContext(
        String replyTopic,
        String correlationId,
        boolean a2aStreaming,
        A2aEnvelope.A2aPushNotificationConfig a2aPushNotificationConfig,
        Map<String, Object> attributes) {

    public ReplyContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ReplyContext a2a(
            boolean streaming,
            String correlationId,
            A2aEnvelope.A2aPushNotificationConfig pushNotificationConfig,
            Map<String, Object> attributes) {
        return new ReplyContext(null, correlationId, streaming, pushNotificationConfig, attributes);
    }

    public static ReplyContext async(String replyTopic, String correlationId, Map<String, Object> attributes) {
        return new ReplyContext(replyTopic, correlationId, false, null, attributes);
    }
}

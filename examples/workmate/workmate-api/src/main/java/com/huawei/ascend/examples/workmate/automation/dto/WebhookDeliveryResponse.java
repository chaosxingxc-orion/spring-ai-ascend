package com.huawei.ascend.examples.workmate.automation.dto;

import com.huawei.ascend.examples.workmate.automation.WebhookDelivery;
import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(
        UUID id,
        String channel,
        String outcome,
        UUID sessionId,
        String message,
        Instant createdAt) {

    public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getChannel(),
                delivery.getOutcome(),
                delivery.getSessionId(),
                delivery.getMessage(),
                delivery.getCreatedAt());
    }
}

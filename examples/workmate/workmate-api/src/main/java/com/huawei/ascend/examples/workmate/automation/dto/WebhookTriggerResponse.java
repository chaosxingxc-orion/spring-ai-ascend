package com.huawei.ascend.examples.workmate.automation.dto;

import java.util.UUID;

public record WebhookTriggerResponse(UUID sessionId, String status, String message) {}

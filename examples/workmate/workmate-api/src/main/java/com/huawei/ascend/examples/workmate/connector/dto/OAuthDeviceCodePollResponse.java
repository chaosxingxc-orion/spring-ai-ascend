package com.huawei.ascend.examples.workmate.connector.dto;

import java.util.Map;

public record OAuthDeviceCodePollResponse(String status, String connectorId, Map<String, String> headers) {}

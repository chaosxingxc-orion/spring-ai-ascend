package com.huawei.ascend.examples.workmate.connector.dto;

import java.util.Map;

public record ConnectConnectorRequest(String apiKey, Map<String, String> headers) {

    public ConnectConnectorRequest {
        if (headers == null) {
            headers = Map.of();
        }
    }
}

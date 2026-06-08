package com.huawei.ascend.agentsdk.spec.model;

import java.util.Map;

public record ModelSpec(
        String provider,
        String name,
        String baseUrl,
        String apiKey,
        boolean sslVerify,
        Map<String, String> headers) {

    public ModelSpec {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}


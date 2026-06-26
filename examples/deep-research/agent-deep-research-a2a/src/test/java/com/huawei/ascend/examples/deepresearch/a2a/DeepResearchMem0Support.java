/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class DeepResearchMem0Support {

    private DeepResearchMem0Support() {
    }

    static boolean isReachable(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank()
                ? "http://7.209.189.82:8000"
                : baseUrl.trim().replaceAll("/+$", "");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalized + "/search"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"query\":\"ping\",\"top_k\":1,\"user_id\":\"probe\",\"agent_id\":\"probe\"}",
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() >= 200 && response.statusCode() < 500;
        } catch (Exception ignored) {
            return false;
        }
    }
}

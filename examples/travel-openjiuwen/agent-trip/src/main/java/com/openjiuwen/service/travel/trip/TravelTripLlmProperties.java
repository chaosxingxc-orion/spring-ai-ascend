/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.trip;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "openjiuwen.travel.trip.llm")
public class TravelTripLlmProperties {

    private String provider = "OpenAI";
    private String apiKey = "";
    private String apiBase = "";
    private String modelName = "";
    private boolean sslVerify = true;
    private int maxIterations = 5;

    boolean isConfigured() {
        return hasText(apiKey) && hasText(apiBase) && hasText(modelName);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.hotel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "openjiuwen.travel.hotel.llm")
public class TravelHotelLlmProperties {

    private String provider = "OpenAI";
    private String apiKey = "";
    private String apiBase = "";
    private String modelName = "";
    private boolean sslVerify = true;
    private int maxIterations = 6;

    /** Package-private: the bean logs a warning when false but still boots (spec §7 — 缺 key 时仍能启动). */
    boolean isConfigured() {
        return hasText(apiKey) && hasText(apiBase) && hasText(modelName);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.mainplan;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Travel mainplan agent configuration, bound under {@code openjiuwen.travel.mainplan}.
 *
 * <p>The LLM client fields live under the {@code llm} sub-block; the prompt-template
 * variables {@code defaultCity} and {@code travelerName} are siblings of {@code llm:}.
 */
@Data
@ConfigurationProperties(prefix = "openjiuwen.travel.mainplan")
public class TravelMainplanLlmProperties {

    /** Default departure city (source default: 深圳). */
    private String defaultCity = "深圳";

    /** Traveler name; empty string when unset (source default). */
    private String travelerName = "";

    /** LLM client configuration block. */
    private Llm llm = new Llm();

    @Data
    public static class Llm {
        private String provider = "OpenAI";
        private String apiKey = "";
        private String apiBase = "";
        private String modelName = "";
        private boolean sslVerify = true;
        private int maxIterations = 10;

        /** Package-private: the bean logs a warning when false but still boots. */
        boolean isConfigured() {
            return hasText(apiKey) && hasText(apiBase) && hasText(modelName);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    /** Convenience passthrough so callers can ask the outer bean whether the LLM is configured. */
    boolean isConfigured() {
        return llm.isConfigured();
    }
}

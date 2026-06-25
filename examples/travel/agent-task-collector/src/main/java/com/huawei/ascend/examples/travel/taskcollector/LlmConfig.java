/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector;

public record LlmConfig(
        String provider,
        String apiKey,
        String apiBase,
        String modelName,
        boolean sslVerify) {

    public static final String DEFAULT_PROVIDER = "OpenAI";

    public static final String DEFAULT_API_BASE = "http://localhost:4000/v1";

    public static final String DEFAULT_MODEL = "gpt-4o-mini";

    public static LlmConfig fromEnv() {
        return new LlmConfig(
                envOrDefault("LLM_PROVIDER", DEFAULT_PROVIDER),
                envOrDefault("LLM_API_KEY", ""),
                envOrDefault("LLM_API_BASE", DEFAULT_API_BASE),
                envOrDefault("LLM_MODEL", DEFAULT_MODEL),
                Boolean.parseBoolean(envOrDefault("LLM_SSL_VERIFY", "false")));
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}

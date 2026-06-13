/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel;

/**
 * LLM connection configuration handed to {@link HotelPlanningAgent}.
 *
 * <p>The fields mirror what {@code ReActAgentConfig.configureModelClient(...)} expects:
 * provider / apiKey / apiBase / modelName / sslVerify. The host process is responsible for
 * sourcing these values — typically from Spring properties (see
 * <code>application.yaml</code>) or environment variables (see {@link #fromEnv()}).
 */
public record LlmConfig(
        String provider,
        String apiKey,
        String apiBase,
        String modelName,
        boolean sslVerify) {

    /**
     * Default LLM provider when {@code LLM_PROVIDER} is unset.
     * <p>openJiuwen 0.1.12 ships these provider names:
     * {@code [OpenAI, OpenRouter, SiliconFlow, DashScope, InferenceAffinity, inference_affinity]}.
     * bigmodel.dev.huawei.com exposes an OpenAI-compatible API, so we use {@code OpenAI}.
     */
    public static final String DEFAULT_PROVIDER = "OpenAI";

    /** Default API base when {@code LLM_API_BASE} is unset. */
    public static final String DEFAULT_API_BASE = "http://api.bigmodel.dev.huawei.com/v1";

    /** Default model name when {@code LLM_MODEL} is unset. */
    public static final String DEFAULT_MODEL = "deepseek-v4-pro";

    /** Default api-key placeholder; tests will override or skip. */
    public static final String DEFAULT_API_KEY_PLACEHOLDER = "xxxxxxxx";

    /**
     * Build configuration from environment variables. Keys mirror those in
     * <code>application.yaml</code>:
     * <ul>
     *   <li>{@code LLM_PROVIDER}   (default {@value #DEFAULT_PROVIDER})</li>
     *   <li>{@code LLM_API_KEY}    (default {@value #DEFAULT_API_KEY_PLACEHOLDER})</li>
     *   <li>{@code LLM_API_BASE}   (default {@value #DEFAULT_API_BASE})</li>
     *   <li>{@code LLM_MODEL}      (default {@value #DEFAULT_MODEL})</li>
     *   <li>{@code LLM_SSL_VERIFY} (default {@code false})</li>
     * </ul>
     */
    public static LlmConfig fromEnv() {
        return new LlmConfig(
                envOrDefault("LLM_PROVIDER", DEFAULT_PROVIDER),
                envOrDefault("LLM_API_KEY", DEFAULT_API_KEY_PLACEHOLDER),
                envOrDefault("LLM_API_BASE", DEFAULT_API_BASE),
                envOrDefault("LLM_MODEL", DEFAULT_MODEL),
                Boolean.parseBoolean(envOrDefault("LLM_SSL_VERIFY", "false")));
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}

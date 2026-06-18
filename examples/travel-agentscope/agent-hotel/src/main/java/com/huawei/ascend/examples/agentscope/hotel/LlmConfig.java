/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.hotel;

/**
 * LLM connection configuration for the AgentScope-flavored hotel agent.
 *
 * <p>Fields mirror what {@code io.agentscope.core.model.OpenAIChatModel.Builder} expects:
 * apiKey / baseUrl / endpointPath / modelName. The host process supplies these
 * — typically from Spring properties (see {@code application.yaml}) or environment
 * variables (see {@link #fromEnv()}).
 *
 * <p>The openJiuwen-flavored sibling also carries {@code provider} + {@code sslVerify},
 * which are openJiuwen-specific and not part of the AgentScope model contract; they
 * are deliberately dropped here.
 */
public record LlmConfig(
        String apiKey,
        String baseUrl,
        String endpointPath,
        String modelName) {

    public static final String DEFAULT_BASE_URL = "http://api.bigmodel.dev.huawei.com/v1";
    public static final String DEFAULT_ENDPOINT_PATH = "/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-v4-pro";
    public static final String DEFAULT_API_KEY_PLACEHOLDER = "sk-local-placeholder";

    public static LlmConfig fromEnv() {
        return new LlmConfig(
                envOrDefault("LLM_API_KEY", DEFAULT_API_KEY_PLACEHOLDER),
                envOrDefault("LLM_API_BASE", DEFAULT_BASE_URL),
                envOrDefault("LLM_ENDPOINT_PATH", DEFAULT_ENDPOINT_PATH),
                envOrDefault("LLM_MODEL", DEFAULT_MODEL));
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
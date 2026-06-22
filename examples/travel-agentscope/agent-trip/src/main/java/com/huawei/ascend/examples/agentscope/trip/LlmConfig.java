/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.trip;

/**
 * LLM connection configuration for the AgentScope-flavored trip planning agent.
 * Mirrors the sibling field set used by the hotel agentscope demo so a single
 * environment-variable layout (LLM_API_KEY / LLM_API_BASE / LLM_ENDPOINT_PATH /
 * LLM_MODEL) drives both hosts when they share a deployment.
 */
public record LlmConfig(
        String apiKey,
        String baseUrl,
        String endpointPath,
        String modelName) {
}
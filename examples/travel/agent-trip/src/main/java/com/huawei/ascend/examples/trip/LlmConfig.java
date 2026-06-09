/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip;

import java.io.InputStream;
import java.util.Properties;

/**
 * LLM 连接配置（字段与酒店子智能体 LlmConfig 一致，宿主可复用同一套配置）。
 */
public record LlmConfig(
        String provider,
        String apiKey,
        String apiBase,
        String modelName,
        boolean sslVerify) {

    /**
     * 读取 classpath 下的 llm.properties；同名环境变量优先级更高（可覆盖文件值）。
     */
    public static LlmConfig load() {
        Properties props = new Properties();
        try (InputStream in = LlmConfig.class.getResourceAsStream("/llm.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            throw new RuntimeException("读取 llm.properties 失败", e);
        }
        return new LlmConfig(
                value(props, "LLM_PROVIDER"),
                value(props, "LLM_API_KEY"),
                value(props, "LLM_API_BASE"),
                value(props, "LLM_MODEL"),
                Boolean.parseBoolean(value(props, "LLM_SSL_VERIFY")));
    }

    private static String value(Properties props, String key) {
        String env = System.getenv(key);
        return (env != null && !env.isBlank()) ? env : props.getProperty(key);
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip.a2a;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 行程规划智能体的 ReAct system prompt 构造器。
 * <p>Prompt 正文见 {@code src/main/resources/prompts/trip-planning-agent-system-prompt.md}。
 */
public final class SystemPromptBuilder {

    private SystemPromptBuilder() {
    }

    /**
     * 加载 markdown 模板并替换动态变量。
     *
     * @param hotelToolName 远端酒店工具名，与 hotel-a2a AgentCard name 一致（{@code hotel-planning-agent}）
     */
    public static String build(String hotelToolName) {
        String prompt = loadResource(TripAgentConstants.PROMPT_RESOURCE_PATH);
        return prompt
                .replace(TripAgentConstants.VAR_TODAY, today())
                .replace(TripAgentConstants.VAR_HOTEL_TOOL_NAME, hotelToolName);
    }

    private static String today() {
        return LocalDate.now(ZoneId.of(TripAgentConstants.TIMEZONE)).toString();
    }

    private static String loadResource(String path) {
        try (InputStream is = SystemPromptBuilder.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource: " + path, e);
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.trip.prompt;

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

    /** System prompt resource path. */
    public static final String PROMPT_RESOURCE_PATH = "/prompts/trip-planning-agent-system-prompt.md";

    /** Template variable: today's date (yyyy-MM-dd, Asia/Shanghai). */
    public static final String VAR_TODAY = "{today}";

    /** Template variable: runtime-injected remote hotel tool name. */
    public static final String VAR_HOTEL_TOOL_NAME = "{hotel_tool_name}";

    /** Timezone for "today" injection in system prompt. */
    public static final String TIMEZONE = "Asia/Shanghai";

    private SystemPromptBuilder() {
    }

    /**
     * 加载 markdown 模板并替换动态变量。
     *
     * @param hotelToolName 远端酒店工具名，与 yaml {@code remote-agents[].name} 一致（本模块为 {@code hotel}）
     */
    public static String build(String hotelToolName) {
        String prompt = loadResource(PROMPT_RESOURCE_PATH);
        return prompt
                .replace(VAR_TODAY, today())
                .replace(VAR_HOTEL_TOOL_NAME, hotelToolName);
    }

    private static String today() {
        return LocalDate.now(ZoneId.of(TIMEZONE)).toString();
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

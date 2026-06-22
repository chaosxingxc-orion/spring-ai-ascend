/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.trip.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * System prompt for the AgentScope trip ReAct agent. The {@code {today}}
 * placeholder is server-injected on every invocation so the model always knows
 * the current date in Asia/Shanghai; {@code {hotel_tool_name}} is injected so
 * the prompt refers to the same tool name the toolkit registers (see
 * {@code TripSkills.HOTEL_TOOL_NAME}).
 *
 * <p>The "Relevant memory:" rule from the openJiuwen sibling is intentionally
 * omitted: the AgentScope handler family carries no memory rail (see runtime
 * issue #316). Restore the section when the rail lands and this agent opts in.
 */
public final class SystemPromptBuilder {

    public static final ZoneId TIMEZONE = ZoneId.of("Asia/Shanghai");

    private static final String PROMPT_RESOURCE_PATH = "/prompts/trip-planning-agent-system-prompt.md";
    private static final String VAR_TODAY = "{today}";
    private static final String VAR_HOTEL_TOOL_NAME = "{hotel_tool_name}";

    private SystemPromptBuilder() {
    }

    public static String build(String hotelToolName) {
        return build(hotelToolName, LocalDate.now(TIMEZONE));
    }

    public static String build(String hotelToolName, LocalDate today) {
        String prompt = loadResource(PROMPT_RESOURCE_PATH);
        return prompt
                .replace(VAR_TODAY, today.toString())
                .replace(VAR_HOTEL_TOOL_NAME, hotelToolName);
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
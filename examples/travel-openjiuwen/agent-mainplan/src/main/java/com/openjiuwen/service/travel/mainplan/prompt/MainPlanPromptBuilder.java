/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.mainplan.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Loads {@code /prompts/main-plan-agent-system-prompt.md} from the classpath and
 * substitutes the runtime placeholders ({@code {current_datetime}}, {@code {default_city}},
 * {@code {traveler_name}}, {@code {dispatch_tool_name}}).
 *
 * <p>The dispatch tool name is hard-wired to {@code "trip"}, which matches the
 * {@link com.openjiuwen.service.travel.mainplan.rails.RemoteTripRail} tool id.
 */
public final class MainPlanPromptBuilder {

    private static final String PROMPT_RESOURCE_PATH = "/prompts/main-plan-agent-system-prompt.md";

    private static final String VAR_CURRENT_DATETIME = "{current_datetime}";
    private static final String VAR_DEFAULT_CITY = "{default_city}";
    private static final String VAR_TRAVELER_NAME = "{traveler_name}";
    private static final String VAR_DISPATCH_TOOL_NAME = "{dispatch_tool_name}";

    /** Our rail's tool name is {@code trip} (see RemoteTripRail.TOOL_NAME). */
    private static final String DISPATCH_TOOL_NAME = "trip";

    private MainPlanPromptBuilder() {
    }

    /**
     * Load the system prompt template and substitute the runtime variables.
     *
     * @param defaultCity  the default departure city (e.g. "深圳")
     * @param travelerName the traveler name (may be empty)
     * @return the prepared system prompt
     */
    public static String build(String defaultCity, String travelerName) {
        String prompt = loadResource(PROMPT_RESOURCE_PATH);
        String currentDatetime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"));
        return prompt
                .replace(VAR_CURRENT_DATETIME, currentDatetime)
                .replace(VAR_DEFAULT_CITY, defaultCity == null ? "" : defaultCity)
                .replace(VAR_TRAVELER_NAME, travelerName == null ? "" : travelerName)
                .replace(VAR_DISPATCH_TOOL_NAME, DISPATCH_TOOL_NAME);
    }

    private static String loadResource(String path) {
        try (InputStream is = MainPlanPromptBuilder.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource: " + path, e);
        }
    }
}

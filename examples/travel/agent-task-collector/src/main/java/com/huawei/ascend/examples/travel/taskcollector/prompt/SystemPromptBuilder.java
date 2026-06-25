/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector.prompt;

import com.huawei.ascend.examples.travel.taskcollector.TaskCollectorAgentConstants;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

public final class SystemPromptBuilder {

    private SystemPromptBuilder() {
    }

    public static String build(String userId, String defaultCity) {
        String prompt = loadResource(TaskCollectorAgentConstants.PROMPT_RESOURCE_PATH);
        return prompt
                .replace(TaskCollectorAgentConstants.VAR_TODAY, today())
                .replace(TaskCollectorAgentConstants.VAR_USER_ID, userId)
                .replace(TaskCollectorAgentConstants.VAR_DEFAULT_CITY, defaultCity);
    }

    private static String today() {
        return LocalDate.now(ZoneId.of(TaskCollectorAgentConstants.TIMEZONE)).toString();
    }

    private static String loadResource(String path) {
        try (InputStream input = SystemPromptBuilder.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource: " + path, e);
        }
    }
}

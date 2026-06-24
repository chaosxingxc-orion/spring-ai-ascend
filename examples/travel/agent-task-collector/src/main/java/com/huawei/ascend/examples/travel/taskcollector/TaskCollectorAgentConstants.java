/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector;

public final class TaskCollectorAgentConstants {

    public static final String AGENT_ID = "task-collector-agent";

    public static final String TOOL_CALENDAR_SEARCH = "travel_calendar_search";

    public static final String TOOL_TODO_SEARCH = "travel_todo_search";

    public static final String TOOL_POLICY_LOOKUP = "travel_policy_lookup";

    public static final String PROMPT_RESOURCE_PATH = "/prompts/task-collector-agent-system-prompt.md";

    public static final String VAR_TODAY = "{today}";

    public static final String VAR_USER_ID = "{user_id}";

    public static final String VAR_DEFAULT_CITY = "{default_city}";

    public static final int DEFAULT_MAX_ITERATIONS = 6;

    public static final String TIMEZONE = "Asia/Shanghai";

    private TaskCollectorAgentConstants() {
    }
}

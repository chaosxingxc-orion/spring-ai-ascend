/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan.a2a.constant;

/**
 * Constants for the Travel Main Plan Agent.
 */
public final class AgentConstants {

    /** Agent identifier. */
    public static final String AGENT_ID = "main-plan-agent";

    /** Tool: request user input when information is insufficient. */
    public static final String TOOL_REQUEST_USER_INPUT = "request_user_input";

    /** Remote trip planning agent id (used in YAML remote-agents config). */
    public static final String REMOTE_TRIP_AGENT_ID = "trip-planning-agent";

    /** Template variable: remote trip planning tool name (resolved at startup). */
    public static final String VAR_DISPATCH_TOOL_NAME = "{dispatch_tool_name}";

    /** System prompt resource path. */
    public static final String PROMPT_RESOURCE_PATH = "/prompts/main-plan-agent-system-prompt.md";

    /** Template variable: current datetime. */
    public static final String VAR_CURRENT_DATETIME = "{current_datetime}";

    /** Template variable: default city. */
    public static final String VAR_DEFAULT_CITY = "{default_city}";

    /** Template variable: traveler name. */
    public static final String VAR_TRAVELER_NAME = "{traveler_name}";

    private AgentConstants() {
    }
}

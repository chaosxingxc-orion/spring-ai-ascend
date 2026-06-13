/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan.constant;

/**
 * Constants for the Main Plan Agent.
 */
public final class AgentConstants {

    /** Agent identifier. */
    public static final String AGENT_ID = "main-plan-agent";

    /** Tool: dispatch travel plan instruction to TravelPlanAgent. */
    public static final String TOOL_DISPATCH_TRAVEL_PLAN = "dispatch_travel_plan";

    /** Tool: request user input when information is insufficient. */
    public static final String TOOL_REQUEST_USER_INPUT = "request_user_input";

    /** Session state key: collected travel info. */
    public static final String STATE_COLLECTED_INFO = "collected_info";

    /** Session state key: current conversation phase. */
    public static final String STATE_CONVERSATION_PHASE = "conversation_phase";

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
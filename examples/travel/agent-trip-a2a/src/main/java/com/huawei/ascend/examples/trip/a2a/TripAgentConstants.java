/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip.a2a;

/**
 * Constants for the trip planning A2A agent.
 */
public final class TripAgentConstants {

    /** OpenJiuwen / A2A agent identifier (must match travel-ascend remote-agents target). */
    public static final String AGENT_ID = "trip-planning-agent";

    /** Remote hotel agent id (agent-hotel-a2a AgentCard name). */
    public static final String REMOTE_HOTEL_AGENT_ID = "hotel-planning-agent";

    /** System prompt resource path. */
    public static final String PROMPT_RESOURCE_PATH = "/prompts/trip-planning-agent-system-prompt.md";

    /** Template variable: today's date (yyyy-MM-dd, Asia/Shanghai). */
    public static final String VAR_TODAY = "{today}";

    /** Template variable: runtime-injected remote hotel tool name. */
    public static final String VAR_HOTEL_TOOL_NAME = "{hotel_tool_name}";

    /** Default ReAct max iterations. */
    public static final int DEFAULT_MAX_ITERATIONS = 5;

    /** Timezone for "today" injection in system prompt. */
    public static final String TIMEZONE = "Asia/Shanghai";

    /**
     * Remote tool name registered by {@code RemoteAgentCardCache} for
     * {@link #REMOTE_HOTEL_AGENT_ID} — equals the downstream AgentCard {@code name}.
     */
    public static String remoteHotelToolName() {
        return REMOTE_HOTEL_AGENT_ID;
    }

    private TripAgentConstants() {
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.trip.tool;

/**
 * Boundary the trip agent uses to delegate the hotel-planning sub-task.
 *
 * <p>Kept as a one-method interface so the trip library has no compile-time
 * dependency on any concrete transport: the production binding ships in the
 * a2a wrapper module and calls the downstream hotel agent over A2A JSON-RPC.
 * Alternative hosts (in-process, mock, different protocol) can supply their
 * own implementation without touching the agent body.
 */
public interface HotelPlannerClient {

    /**
     * @param naturalLanguageRequest a fully-formed Chinese request that already
     *        embeds city, dates, and any policy / preference text; the hotel
     *        agent must be able to act on it as if it came from a user
     * @return the downstream agent's final assistant text (markdown)
     */
    String plan(String naturalLanguageRequest);
}
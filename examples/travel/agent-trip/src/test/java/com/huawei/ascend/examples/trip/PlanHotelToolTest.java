/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip;

import com.huawei.ascend.examples.trip.support.StubHotelPlannerClient;
import com.openjiuwen.core.foundation.tool.Tool;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯单元测试：不连 LLM，验证 buildHotelNl 拼装 + plan_hotel 工具透传。
 */
class PlanHotelToolTest {

    @Test
    void buildHotelNl_containsKeyElements() {
        String nl = TripPlanningAgent.buildHotelNl(Map.of(
                "city", "北京",
                "checkIn", "2026-06-16",
                "checkOut", "2026-06-18",
                "policyText", "差标：每晚不超过 800 元、最低 4 星、协议品牌 全季/亚朵",
                "preferences", "国贸附近，需要会议室"));

        assertTrue(nl.contains("北京"));
        assertTrue(nl.contains("2026-06-16") && nl.contains("2026-06-18"));
        assertTrue(nl.contains("全季"));
        assertTrue(nl.contains("国贸附近"));
    }

    @Test
    void planHotel_delegatesToHotelClient() throws Exception {
        StubHotelPlannerClient stub = new StubHotelPlannerClient();
        TripPlanningAgent agent = new TripPlanningAgent(null, stub);

        Tool planHotel = agent.buildPlanHotelTool();
        Object md = planHotel.invoke(Map.of(
                "city", "北京",
                "checkIn", "2026-06-16",
                "checkOut", "2026-06-18",
                "policyText", "差标：协议品牌 全季/亚朵",
                "preferences", "国贸附近"), null);

        assertNotNull(stub.lastNl);
        assertTrue(stub.lastNl.contains("北京"));
        assertTrue(stub.lastNl.contains("全季"));
        assertTrue(md.toString().contains("推荐："));
    }
}

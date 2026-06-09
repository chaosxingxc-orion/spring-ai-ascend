/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip;

import com.huawei.ascend.examples.trip.support.StubHotelPlannerClient;
import com.openjiuwen.core.runner.Runner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单独集成测试：真调 LLM + 酒店走桩（不依赖 agent-hotel）。
 * <p>运行前需配好 LLM_* 环境变量；可用 -DskipITs 跳过。
 */
class TripPlanningAgentIT {

    @AfterAll
    static void tearDown() {
        Runner.stop();
    }

    @Test
    void chat_withStubHotel_returnsItinerary() {
        StubHotelPlannerClient stub = new StubHotelPlannerClient();
        TripPlanningAgent agent = new TripPlanningAgent(LlmConfig.load(), stub);

        String md = agent.chat(
                "员工 zhang3 出差北京 2026-06-16 至 2026-06-18，共 3 天。"
                + "差标：每晚不超过 800 元、最低 4 星、协议品牌 全季/亚朵/希尔顿欢朋。偏好：国贸附近。");

        assertNotNull(stub.lastNl);
        assertTrue(stub.lastNl.contains("北京"));
        assertTrue(stub.lastNl.contains("2026-06-16"));
        assertTrue(md.contains("住宿") || md.contains("全季"));
    }

    @Test
    void chat_noCity_asksForClarification() {
        TripPlanningAgent agent = new TripPlanningAgent(LlmConfig.load(), new StubHotelPlannerClient());
        String md = agent.chat("帮我订个酒店");
        assertNotNull(md);
    }
}

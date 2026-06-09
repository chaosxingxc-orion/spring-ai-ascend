/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip.support;

import com.huawei.ascend.examples.trip.hotel.HotelPlannerClient;

/**
 * 测试用酒店桩：不连真酒店子智能体，返回固定 markdown，并记录收到的 NL 便于断言。
 */
public class StubHotelPlannerClient implements HotelPlannerClient {

    public volatile String lastNl;

    @Override
    public String plan(String nl) {
        this.lastNl = nl;
        return """
               1. 北京国贸全季酒店 · ★4 · 全季 · ¥620起 · 国贸 · [符合差标]
               2. 亚朵酒店(国贸店) · ★4 · 亚朵 · ¥700起 · 国贸 · [符合差标]
               推荐：北京国贸全季酒店；理由：价格合规且紧邻会议地点
               """;
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip.hotel;

import com.huawei.ascend.examples.hotel.HotelPlanningAgent;

/**
 * 本期：同进程委托酒店规划子智能体（agent-hotel 的入口类 {@link HotelPlanningAgent}）。
 * <p>{@code plan_hotel} 工具拼好的 NL 直接交给 {@code hotelAgent.chat(nl)}。
 */
public class LocalHotelPlannerClient implements HotelPlannerClient {

    private final HotelPlanningAgent hotelAgent;

    public LocalHotelPlannerClient(HotelPlanningAgent hotelAgent) {
        this.hotelAgent = hotelAgent;
    }

    @Override
    public String plan(String naturalLanguageRequest) {
        return hotelAgent.chat(naturalLanguageRequest);
    }
}

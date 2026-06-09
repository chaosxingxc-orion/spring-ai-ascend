/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip.hotel;

/**
 * A2A 远程酒店客户端（预留）：等 agent-runtime 具备拉起服务能力后实现，本期不写。
 */
public class A2aHotelPlannerClient implements HotelPlannerClient {

    @Override
    public String plan(String naturalLanguageRequest) {
        // 后续：构造 JSON-RPC message/send，POST 到酒店 agent 的 A2A endpoint，解析 markdown
        throw new UnsupportedOperationException("A2A 预留，待 agent-runtime 具备后实现");
    }
}

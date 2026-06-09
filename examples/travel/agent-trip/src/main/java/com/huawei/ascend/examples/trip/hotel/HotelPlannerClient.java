/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip.hotel;

/**
 * 出站抽象：行程规划调用下游酒店规划子智能体的客户端。
 * <p>本地实现（同进程委托）与 A2A 远程实现可热插拔。
 */
public interface HotelPlannerClient {

    /**
     * @param naturalLanguageRequest 拼给酒店子智能体的自然语言诉求
     * @return 酒店推荐 markdown
     */
    String plan(String naturalLanguageRequest);
}

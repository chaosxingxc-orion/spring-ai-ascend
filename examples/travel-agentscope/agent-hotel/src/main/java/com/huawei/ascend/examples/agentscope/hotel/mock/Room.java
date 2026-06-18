/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.hotel.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Room(
        String roomId,
        String roomName,
        String bedTypeName,
        String area,
        String window,
        boolean breakfastIncluded,
        boolean cancellable,
        double rmbPrice) {
}
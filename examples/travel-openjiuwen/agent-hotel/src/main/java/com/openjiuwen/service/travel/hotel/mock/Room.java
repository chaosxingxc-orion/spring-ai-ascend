/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.hotel.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Room offer as it appears in <code>mock/hotels.json</code> and as returned by the
 * <code>hotel_detail</code> tool.
 */
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

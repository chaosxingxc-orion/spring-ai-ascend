/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.hotel.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Hotel(
        String hotelId,
        String chineseName,
        int star,
        String brand,
        double lowestPrice,
        double commentScore,
        String district,
        String address,
        List<String> facilityTags,
        List<Room> rooms) {
}
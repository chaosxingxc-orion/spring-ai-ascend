/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Hotel record as stored in <code>mock/hotels.json</code>. Rooms are kept inline so the
 * <code>hotel_detail</code> tool can return them without an extra lookup.
 *
 * <p>Star is constrained to 2..5 — economy chains stay at 2-3 per design doc §7.2.
 */
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

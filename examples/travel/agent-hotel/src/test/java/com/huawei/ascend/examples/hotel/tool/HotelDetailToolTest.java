/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.tool;

import com.huawei.ascend.examples.hotel.mock.MockHotelInventory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HotelDetailToolTest {

    private static final MockHotelInventory INV = new MockHotelInventory();

    @Test
    void returnsHotelHeaderAndRoomsForKnownHotelId() {
        // Pick a known seed id from the JSON.
        Map<String, Object> result = HotelDetailTool.invokeDirectly(INV, Map.of(
                "hotelId", "BJ-001",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21"));

        assertThat(result.get("successCode")).isEqualTo(true);
        assertThat(result.get("hotelId")).isEqualTo("BJ-001");
        assertThat(result.get("chineseName")).isEqualTo("全季酒店（北京国贸店）");
        assertThat(result.get("star")).isEqualTo(3);
        assertThat(result.get("brand")).isEqualTo("全季");
        assertThat(result.get("district")).isEqualTo("国贸/CBD");
        assertThat(result.get("checkIn")).isEqualTo("2026-06-20");
        assertThat(result.get("checkOut")).isEqualTo("2026-06-21");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rooms = (List<Map<String, Object>>) result.get("rooms");
        assertThat(rooms).isNotEmpty();
        for (Map<String, Object> r : rooms) {
            assertThat(r).containsKeys(
                    "roomId", "roomName", "bedTypeName", "area", "window",
                    "breakfastIncluded", "cancellable", "rmbPrice");
        }
    }

    @Test
    void unknownHotelIdYieldsFailure() {
        Map<String, Object> result = HotelDetailTool.invokeDirectly(INV, Map.of(
                "hotelId", "ZZ-000",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21"));

        assertThat(result.get("successCode")).isEqualTo(false);
        assertThat(((String) result.get("errorMessage"))).contains("ZZ-000");
    }

    @Test
    void missingHotelIdYieldsFailure() {
        Map<String, Object> result = HotelDetailTool.invokeDirectly(INV, java.util.Collections.emptyMap());
        assertThat(result.get("successCode")).isEqualTo(false);
    }
}

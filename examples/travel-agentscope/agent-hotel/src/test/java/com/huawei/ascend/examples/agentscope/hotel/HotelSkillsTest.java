/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.hotel.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.agentscope.hotel.mock.MockHotelInventory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HotelSkillsTest {

    private final HotelSkills skills = new HotelSkills(new MockHotelInventory());

    @Test
    void search_known_city_returns_page() {
        Map<String, Object> out = skills.hotelSearch(
                "北京", "2026-06-20", "2026-06-21",
                "", "", "", "", "");
        assertThat(out).containsEntry("successCode", true);
        assertThat((List<?>) out.get("hotels")).isNotEmpty();
    }

    @Test
    void search_unknown_city_returns_failure() {
        Map<String, Object> out = skills.hotelSearch(
                "无此城", "2026-06-20", "2026-06-21",
                "", "", "", "", "");
        assertThat(out).containsEntry("successCode", false);
        assertThat(out).containsKey("errorMessage");
    }

    @Test
    void search_with_filters_marks_compliance() {
        Map<String, Object> out = skills.hotelSearch(
                "上海", "2026-06-20", "2026-06-21",
                "800", "4", "", "", "1");
        assertThat(out).containsEntry("successCode", true);
        List<Map<String, Object>> hotels = (List<Map<String, Object>>) out.get("hotels");
        for (Map<String, Object> h : hotels) {
            assertThat(h).containsKey("compliancePassed");
        }
    }

    @Test
    void detail_missing_id_returns_failure() {
        Map<String, Object> out = skills.hotelDetail("", "2026-06-20", "2026-06-21");
        assertThat(out).containsEntry("successCode", false);
    }
}
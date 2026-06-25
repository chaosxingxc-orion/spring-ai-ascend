/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.travel.taskcollector.mcp.TravelPolicyLookupTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TravelPolicyLookupToolTest {

    @Test
    void returnsPolicyFields() {
        Map<String, Object> result = TravelPolicyLookupTool.invokeDirectly(Map.of(
                "userId", "zhang3",
                "city", "北京"));

        assertThat(result)
                .containsEntry("hotelBudgetPerNight", 800)
                .containsEntry("minHotelStar", 4)
                .containsEntry("transportPolicy", "高铁二等座 / 经济舱");
        assertThat(((List<?>) result.get("preferredBrands")).stream().map(String::valueOf).toList())
                .contains("全季", "亚朵", "希尔顿欢朋");
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.travel.taskcollector.mcp.TravelCalendarSearchTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TravelCalendarSearchToolTest {

    @Test
    void returnsMeetingAndVisitItems() {
        Map<String, Object> result = TravelCalendarSearchTool.invokeDirectly(Map.of(
                "userId", "zhang3",
                "startDate", "2026-06-18",
                "endDate", "2026-06-20",
                "city", "北京"));

        assertThat(result)
                .containsEntry("userId", "zhang3")
                .containsEntry("startDate", "2026-06-18")
                .containsEntry("endDate", "2026-06-20");
        assertThat(types(result)).containsExactly("meeting", "visit");
    }

    private static List<String> types(Map<String, Object> result) {
        List<String> types = new ArrayList<>();
        for (Object item : (List<?>) result.get("items")) {
            types.add(String.valueOf(((Map<?, ?>) item).get("type")));
        }
        return types;
    }
}

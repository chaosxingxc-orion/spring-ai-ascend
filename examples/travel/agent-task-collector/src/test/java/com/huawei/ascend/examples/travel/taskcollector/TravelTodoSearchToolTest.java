/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.travel.taskcollector.mcp.TravelTodoSearchTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TravelTodoSearchToolTest {

    @Test
    void returnsTravelTodos() {
        Map<String, Object> result = TravelTodoSearchTool.invokeDirectly(Map.of(
                "userId", "zhang3",
                "keyword", "北京出差"));

        assertThat(result).containsEntry("keyword", "北京出差");
        assertThat(priorities(result)).containsExactly("high", "medium");
    }

    private static List<String> priorities(Map<String, Object> result) {
        List<String> priorities = new ArrayList<>();
        for (Object item : (List<?>) result.get("items")) {
            priorities.add(String.valueOf(((Map<?, ?>) item).get("priority")));
        }
        return priorities;
    }
}

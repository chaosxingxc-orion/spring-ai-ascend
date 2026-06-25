/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MockTravelMcpData {

    private MockTravelMcpData() {
    }

    static Map<String, Object> calendarItems(String city) {
        String resolvedCity = city == null || city.isBlank() ? "北京" : city;
        return new LinkedHashMap<>(Map.of(
                "items", List.of(
                        new LinkedHashMap<>(Map.of(
                                "type", "meeting",
                                "title", "客户技术交流",
                                "startTime", "2026-06-18 14:00",
                                "endTime", "2026-06-18 16:00",
                                "city", resolvedCity,
                                "location", resolvedCity + "国贸三期",
                                "source", "mock-calendar-mcp")),
                        new LinkedHashMap<>(Map.of(
                                "type", "visit",
                                "title", "客户现场拜访",
                                "startTime", "2026-06-19 09:30",
                                "endTime", "2026-06-19 11:30",
                                "city", resolvedCity,
                                "location", resolvedCity + "望京",
                                "source", "mock-calendar-mcp")))));
    }

    static Map<String, Object> todos() {
        return new LinkedHashMap<>(Map.of(
                "items", List.of(
                        new LinkedHashMap<>(Map.of(
                                "type", "todo",
                                "title", "准备客户技术交流材料",
                                "dueDate", "2026-06-17",
                                "priority", "high",
                                "source", "mock-todo-mcp")),
                        new LinkedHashMap<>(Map.of(
                                "type", "todo",
                                "title", "确认客户拜访名单",
                                "dueDate", "2026-06-17",
                                "priority", "medium",
                                "source", "mock-todo-mcp")))));
    }

    static Map<String, Object> policy(String city) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("city", city == null || city.isBlank() ? "北京" : city);
        out.put("hotelBudgetPerNight", 800);
        out.put("minHotelStar", 4);
        out.put("preferredBrands", List.of("全季", "亚朵", "希尔顿欢朋"));
        out.put("transportPolicy", "高铁二等座 / 经济舱");
        out.put("source", "mock-policy-mcp");
        return out;
    }
}

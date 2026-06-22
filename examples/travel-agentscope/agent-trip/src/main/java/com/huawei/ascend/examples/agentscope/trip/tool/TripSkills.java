/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.trip.tool;

import io.agentscope.core.tool.DefaultToolResultConverter;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AgentScope {@code Toolkit} target that exposes a single {@code plan_hotel}
 * method to the ReAct loop. The method composes the trip request fields into
 * a natural-language sentence and delegates the actual hotel planning to a
 * {@link HotelPlannerClient}.
 *
 * <p>All tool parameters are typed as {@code String} (mirrors
 * {@code com.huawei.ascend.examples.agentscope.hotel.tool.HotelSkills}): the
 * AgentScope schema reflector pairs cleanly with primitives, and string-typed
 * optional fields use the empty string as null fallback.
 */
public final class TripSkills {

    /** Tool name registered to the AgentScope toolkit; also referenced by the system prompt. */
    public static final String HOTEL_TOOL_NAME = "plan_hotel";

    private final HotelPlannerClient hotelPlannerClient;

    public TripSkills(HotelPlannerClient hotelPlannerClient) {
        this.hotelPlannerClient = Objects.requireNonNull(hotelPlannerClient, "hotelPlannerClient");
    }

    @Tool(
            name = HOTEL_TOOL_NAME,
            description = "调用酒店规划子智能体：传入城市/入离日期/差标文本/偏好，返回酒店推荐 markdown",
            converter = DefaultToolResultConverter.class)
    public Map<String, Object> planHotel(
            @ToolParam(name = "city", required = true,
                    description = "城市中文名，如 北京 / 上海") String city,
            @ToolParam(name = "checkIn", required = true,
                    description = "入住日期，yyyy-MM-dd") String checkIn,
            @ToolParam(name = "checkOut", required = true,
                    description = "离店日期，yyyy-MM-dd") String checkOut,
            @ToolParam(name = "policyText", required = false,
                    description = "差标的自然语言描述：价格上限/最低星级/协议品牌；不需要时传空串") String policyText,
            @ToolParam(name = "preferences", required = false,
                    description = "其他偏好：商圈、设施关键字等；不需要时传空串") String preferences) {

        if (isBlank(city) || isBlank(checkIn) || isBlank(checkOut)) {
            return failure("city / checkIn / checkOut 必填");
        }

        String request = buildHotelRequest(city, checkIn, checkOut, policyText, preferences);
        String response;
        try {
            response = hotelPlannerClient.plan(request);
        } catch (RuntimeException ex) {
            return failure("调用酒店规划子智能体失败：" + rootMessage(ex));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("successCode", true);
        out.put("hotelPlan", response == null ? "" : response);
        return out;
    }

    private static String buildHotelRequest(
            String city, String checkIn, String checkOut, String policyText, String preferences) {
        StringBuilder sb = new StringBuilder();
        sb.append("出差到 ").append(city).append("，")
                .append(checkIn).append(" 至 ").append(checkOut).append("。");
        if (!isBlank(policyText)) {
            sb.append('\n').append(policyText.trim());
        }
        if (!isBlank(preferences)) {
            sb.append("\n偏好：").append(preferences.trim()).append('。');
        }
        return sb.toString();
    }

    private static Map<String, Object> failure(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("successCode", false);
        out.put("errorMessage", message);
        return out;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String rootMessage(Throwable error) {
        StringBuilder out = new StringBuilder();
        Throwable cur = error;
        while (cur != null) {
            String m = cur.getMessage();
            if (m != null && !m.isBlank()) {
                if (!out.isEmpty()) {
                    out.append(": ");
                }
                out.append(m);
            }
            cur = cur.getCause();
        }
        return out.isEmpty() ? error.getClass().getName() : out.toString();
    }
}
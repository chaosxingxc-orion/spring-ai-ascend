/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.hotel.tool;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.service.travel.hotel.mock.Hotel;
import com.openjiuwen.service.travel.hotel.mock.MockHotelInventory;
import com.openjiuwen.service.travel.hotel.mock.Room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <code>hotel_detail</code> tool — given a hotelId + date range, return the hotel header
 * fields plus the list of room offers.
 *
 * <p>The date range is currently echoed back to the LLM but does not affect pricing, since
 * the mock catalog stores a single price per room. Reserved for future use.
 */
public final class HotelDetailTool extends LocalFunction {

    public static final String TOOL_ID = "hotel_detail";

    public HotelDetailTool(MockHotelInventory inventory) {
        super(buildCard(), inputs -> execute(inventory, inputs));
    }

    static Map<String, Object> execute(MockHotelInventory inventory, Map<String, Object> inputs) {
        String hotelId = asString(inputs.get("hotelId"));
        if (hotelId == null || hotelId.isBlank()) {
            return failure("missing hotelId");
        }
        Hotel h = inventory.findById(hotelId);
        if (h == null) {
            return failure("hotel not found: " + hotelId);
        }

        List<Map<String, Object>> rooms = new ArrayList<>();
        if (h.rooms() != null) {
            for (Room r : h.rooms()) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("roomId", r.roomId());
                rm.put("roomName", r.roomName());
                rm.put("bedTypeName", r.bedTypeName());
                rm.put("area", r.area());
                rm.put("window", r.window());
                rm.put("breakfastIncluded", r.breakfastIncluded());
                rm.put("cancellable", r.cancellable());
                rm.put("rmbPrice", r.rmbPrice());
                rooms.add(rm);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("successCode", true);
        out.put("hotelId", h.hotelId());
        out.put("chineseName", h.chineseName());
        out.put("star", h.star());
        out.put("brand", h.brand());
        out.put("lowestPrice", h.lowestPrice());
        out.put("commentScore", h.commentScore());
        out.put("district", h.district());
        out.put("address", h.address());
        out.put("facilityTags", h.facilityTags() == null ? List.of() : h.facilityTags());
        out.put("checkIn", asString(inputs.get("checkIn")));
        out.put("checkOut", asString(inputs.get("checkOut")));
        out.put("rooms", rooms);
        return out;
    }

    private static Map<String, Object> failure(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("successCode", false);
        out.put("errorMessage", message);
        out.put("rooms", List.of());
        return out;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static ToolCard buildCard() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("hotelId", Map.of(
                "type", "string",
                "description", "hotel_search 返回的 hotelId"));
        props.put("checkIn", Map.of(
                "type", "string",
                "description", "入住日期，yyyy-MM-dd"));
        props.put("checkOut", Map.of(
                "type", "string",
                "description", "离店日期，yyyy-MM-dd"));

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", props);
        inputParams.put("required", List.of("hotelId", "checkIn", "checkOut"));

        return ToolCard.builder()
                .id(TOOL_ID)
                .name(TOOL_ID)
                .description("酒店详情查询：按 hotelId 返回酒店头信息 + 可订房型列表")
                .inputParams(inputParams)
                .build();
    }

    /** Convenience for tests / SampleMain. */
    public static Map<String, Object> invokeDirectly(
            MockHotelInventory inventory, Map<String, Object> inputs) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(inputs, "inputs");
        return execute(inventory, inputs);
    }
}

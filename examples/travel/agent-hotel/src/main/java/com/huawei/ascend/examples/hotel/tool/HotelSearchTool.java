/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.tool;

import com.huawei.ascend.examples.hotel.mock.Hotel;
import com.huawei.ascend.examples.hotel.mock.MockHotelInventory;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * <code>hotel_search</code> tool — list candidate hotels for a city + date range, optionally
 * filtered by corporate-travel policy (max price / min star / brand whitelist) and free-text
 * keywords (district / facility tags).
 *
 * <p>Wraps {@link MockHotelInventory} as an openJiuwen {@link LocalFunction}. The pagination
 * window is 6 per page (matches the {@code maxRecommendations} contract in the prompt).
 */
public final class HotelSearchTool extends LocalFunction {

    public static final String TOOL_ID = "hotel_search";
    public static final int PAGE_SIZE = 6;

    public HotelSearchTool(MockHotelInventory inventory) {
        super(buildCard(), inputs -> execute(inventory, inputs));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> execute(MockHotelInventory inventory, Map<String, Object> inputs) {
        String cityName = asString(inputs.get("cityName"));
        List<Hotel> cityHotels = inventory.hotelsInCity(cityName);
        if (cityHotels.isEmpty()) {
            return failure("未识别的城市或无库存：" + (cityName == null ? "" : cityName));
        }

        Double maxPrice = asDoubleOrNull(inputs.get("maxPricePerNight"));
        Integer minStar = asIntOrNull(inputs.get("minStar"));
        List<String> brandWhitelist = asStringList(inputs.get("brandWhitelist"));
        List<String> keywords = parseKeywords(asString(inputs.get("keyword")));
        int pageNum = Math.max(1, asIntOrDefault(inputs.get("pageNum"), 1));

        List<Hotel> filtered = new ArrayList<>();
        for (Hotel h : cityHotels) {
            if (maxPrice != null && h.lowestPrice() > maxPrice) {
                continue;
            }
            if (minStar != null && h.star() < minStar) {
                continue;
            }
            if (brandWhitelist != null && !brandWhitelist.isEmpty()
                    && !brandWhitelist.contains(h.brand())) {
                continue;
            }
            if (!keywords.isEmpty() && !matchesKeywords(h, keywords)) {
                continue;
            }
            filtered.add(h);
        }

        int total = filtered.size();
        int from = Math.min((pageNum - 1) * PAGE_SIZE, total);
        int to = Math.min(from + PAGE_SIZE, total);
        List<Hotel> page = filtered.subList(from, to);

        List<Map<String, Object>> briefs = new ArrayList<>();
        for (Hotel h : page) {
            briefs.add(toBrief(h, maxPrice, minStar, brandWhitelist));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("successCode", true);
        out.put("totalCount", total);
        out.put("pageNum", pageNum);
        out.put("pageSize", PAGE_SIZE);
        out.put("hotels", briefs);
        return out;
    }

    private static Map<String, Object> toBrief(
            Hotel h, Double maxPrice, Integer minStar, List<String> brandWhitelist) {
        boolean compliancePassed = true;
        if (maxPrice != null && h.lowestPrice() > maxPrice) {
            compliancePassed = false;
        }
        if (minStar != null && h.star() < minStar) {
            compliancePassed = false;
        }
        if (brandWhitelist != null && !brandWhitelist.isEmpty()
                && !brandWhitelist.contains(h.brand())) {
            compliancePassed = false;
        }

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("hotelId", h.hotelId());
        b.put("chineseName", h.chineseName());
        b.put("star", h.star());
        b.put("brand", h.brand());
        b.put("lowestPrice", h.lowestPrice());
        b.put("commentScore", h.commentScore());
        b.put("district", h.district());
        b.put("address", h.address());
        b.put("compliancePassed", compliancePassed);
        b.put("facilityTags", h.facilityTags() == null ? List.of() : h.facilityTags());
        return b;
    }

    private static boolean matchesKeywords(Hotel h, List<String> keywords) {
        String district = h.district() == null ? "" : h.district().toLowerCase(Locale.ROOT);
        String address = h.address() == null ? "" : h.address().toLowerCase(Locale.ROOT);
        List<String> tags = h.facilityTags() == null ? List.of() : h.facilityTags();
        for (String kw : keywords) {
            String low = kw.toLowerCase(Locale.ROOT);
            if (district.contains(low) || address.contains(low)) {
                return true;
            }
            for (String tag : tags) {
                if (tag != null && tag.toLowerCase(Locale.ROOT).contains(low)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> parseKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split("[,，;；\\s]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static Map<String, Object> failure(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("successCode", false);
        out.put("totalCount", 0);
        out.put("hotels", List.of());
        out.put("errorMessage", message);
        return out;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Double asDoubleOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer asIntOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int asIntOrDefault(Object v, int fallback) {
        Integer i = asIntOrNull(v);
        return i == null ? fallback : i;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        // tolerate a comma-separated string fallback
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String p : s.split("[,，;；]+")) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static ToolCard buildCard() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("cityName", Map.of(
                "type", "string",
                "description", "城市中文名，如 北京 / 上海"));
        props.put("checkIn", Map.of(
                "type", "string",
                "description", "入住日期，yyyy-MM-dd"));
        props.put("checkOut", Map.of(
                "type", "string",
                "description", "离店日期，yyyy-MM-dd"));
        props.put("maxPricePerNight", Map.of(
                "type", "number",
                "description", "单晚价格上限（元）；可空"));
        props.put("minStar", Map.of(
                "type", "integer",
                "description", "最低星级 2-5；可空"));
        props.put("brandWhitelist", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "协议品牌中文名数组；可空"));
        props.put("keyword", Map.of(
                "type", "string",
                "description", "商圈/设施关键字，多个用逗号分隔；可空"));
        props.put("pageNum", Map.of(
                "type", "integer",
                "description", "翻页，1 起；默认 1，每页 6"));

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", props);
        inputParams.put("required", List.of("cityName", "checkIn", "checkOut"));

        return ToolCard.builder()
                .id(TOOL_ID)
                .name(TOOL_ID)
                .description("酒店列表查询：按城市+日期+差标过滤，返回 ≤6 条候选酒店简要信息")
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

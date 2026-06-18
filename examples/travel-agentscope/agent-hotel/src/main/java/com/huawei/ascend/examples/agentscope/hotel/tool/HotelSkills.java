/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.hotel.tool;

import com.huawei.ascend.examples.agentscope.hotel.mock.Hotel;
import com.huawei.ascend.examples.agentscope.hotel.mock.MockHotelInventory;
import com.huawei.ascend.examples.agentscope.hotel.mock.Room;
import io.agentscope.core.tool.DefaultToolResultConverter;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * AgentScope {@link io.agentscope.core.tool.Toolkit#registerTool} target: each {@link Tool}-annotated
 * method becomes a callable tool whose JSON schema is reflected from the {@link ToolParam}
 * annotations. The two methods mirror the openJiuwen siblings
 * {@code HotelSearchTool} / {@code HotelDetailTool}, returning the same structured maps
 * (AgentScope's {@link DefaultToolResultConverter} serializes the {@code Map} to JSON).
 *
 * <p>All optional fields are typed as {@code String} (not {@code Double} / {@code List<String>})
 * because the AgentScope tool-schema reflector pairs cleanly with primitive-ish parameters,
 * and the openJiuwen sibling already accepts the same comma-separated fallback shapes.
 */
public final class HotelSkills {

    public static final String SEARCH_TOOL_NAME = "hotel_search";
    public static final String DETAIL_TOOL_NAME = "hotel_detail";
    public static final int PAGE_SIZE = 6;

    private final MockHotelInventory inventory;

    public HotelSkills(MockHotelInventory inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Tool(
            name = SEARCH_TOOL_NAME,
            description = "酒店列表查询：按城市+日期+差标过滤，返回 <=6 条候选酒店简要信息",
            converter = DefaultToolResultConverter.class)
    public Map<String, Object> hotelSearch(
            @ToolParam(name = "cityName", required = true,
                    description = "城市中文名，如 北京 / 上海") String cityName,
            @ToolParam(name = "checkIn", required = true,
                    description = "入住日期，yyyy-MM-dd") String checkIn,
            @ToolParam(name = "checkOut", required = true,
                    description = "离店日期，yyyy-MM-dd") String checkOut,
            @ToolParam(name = "maxPricePerNight", required = false,
                    description = "单晚价格上限（元）；不需要时传空串") String maxPricePerNight,
            @ToolParam(name = "minStar", required = false,
                    description = "最低星级 2-5；不需要时传空串") String minStar,
            @ToolParam(name = "brandWhitelist", required = false,
                    description = "协议品牌中文名，多个用逗号分隔；不需要时传空串") String brandWhitelist,
            @ToolParam(name = "keyword", required = false,
                    description = "商圈/设施关键字，多个用逗号分隔；不需要时传空串") String keyword,
            @ToolParam(name = "pageNum", required = false,
                    description = "翻页，1 起；默认 1，每页 6") String pageNum) {

        List<Hotel> cityHotels = inventory.hotelsInCity(cityName);
        if (cityHotels.isEmpty()) {
            return failure("未识别的城市或无库存：" + (cityName == null ? "" : cityName));
        }

        Double maxPrice = parseDoubleOrNull(maxPricePerNight);
        Integer minStarInt = parseIntOrNull(minStar);
        List<String> brandList = parseCsv(brandWhitelist);
        List<String> keywords = parseCsv(keyword);
        int page = Math.max(1, parseIntOrDefault(pageNum, 1));

        List<Hotel> filtered = new ArrayList<>();
        for (Hotel h : cityHotels) {
            if (maxPrice != null && h.lowestPrice() > maxPrice) {
                continue;
            }
            if (minStarInt != null && h.star() < minStarInt) {
                continue;
            }
            if (!brandList.isEmpty() && !brandList.contains(h.brand())) {
                continue;
            }
            if (!keywords.isEmpty() && !matchesKeywords(h, keywords)) {
                continue;
            }
            filtered.add(h);
        }

        int total = filtered.size();
        int from = Math.min((page - 1) * PAGE_SIZE, total);
        int to = Math.min(from + PAGE_SIZE, total);
        List<Hotel> pageSlice = filtered.subList(from, to);

        List<Map<String, Object>> briefs = new ArrayList<>();
        for (Hotel h : pageSlice) {
            briefs.add(toBrief(h, maxPrice, minStarInt, brandList));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("successCode", true);
        out.put("totalCount", total);
        out.put("pageNum", page);
        out.put("pageSize", PAGE_SIZE);
        out.put("hotels", briefs);
        out.put("checkIn", checkIn);
        out.put("checkOut", checkOut);
        return out;
    }

    @Tool(
            name = DETAIL_TOOL_NAME,
            description = "酒店详情查询：按 hotelId 返回酒店头信息 + 可订房型列表",
            converter = DefaultToolResultConverter.class)
    public Map<String, Object> hotelDetail(
            @ToolParam(name = "hotelId", required = true,
                    description = "hotel_search 返回的 hotelId") String hotelId,
            @ToolParam(name = "checkIn", required = true,
                    description = "入住日期，yyyy-MM-dd") String checkIn,
            @ToolParam(name = "checkOut", required = true,
                    description = "离店日期，yyyy-MM-dd") String checkOut) {

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
        out.put("checkIn", checkIn);
        out.put("checkOut", checkOut);
        out.put("rooms", rooms);
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
        if (!brandWhitelist.isEmpty() && !brandWhitelist.contains(h.brand())) {
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

    private static Map<String, Object> failure(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("successCode", false);
        out.put("totalCount", 0);
        out.put("hotels", List.of());
        out.put("errorMessage", message);
        return out;
    }

    private static List<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String p : raw.split("[,，;；]+")) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static Double parseDoubleOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntOrDefault(String s, int fallback) {
        Integer v = parseIntOrNull(s);
        return v == null ? fallback : v;
    }
}
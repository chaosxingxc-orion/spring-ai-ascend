/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.hotel.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory hotel inventory loaded from <code>mock/hotels.json</code> on the classpath.
 *
 * <p>Construct once per process; lookups are pure in-memory filters keyed by a normalized
 * city name (see {@link #normalizeCityName(String)}). The instance is immutable after
 * construction and safe to share across threads.
 *
 * <p>Schema mirrors the JSON file shape:
 * <pre>
 * { "cities": [ { "cityName": "北京", "tier": "T1",
 *                 "hotels": [ { ...Hotel fields..., "rooms": [...] } ] } ] }
 * </pre>
 */
public final class MockHotelInventory {

    /** Default classpath resource. */
    public static final String DEFAULT_RESOURCE = "mock/hotels.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** English → Chinese mapping for the 31 supported cities. */
    private static final Map<String, String> ENGLISH_TO_CHINESE = buildEnglishMap();

    /** Keyed by normalized cityName. */
    private final Map<String, List<Hotel>> hotelsByCity;

    /** Flat hotelId → Hotel index for O(1) detail lookup. */
    private final Map<String, Hotel> hotelsById;

    /** Load from the default classpath resource. */
    public MockHotelInventory() {
        this(DEFAULT_RESOURCE);
    }

    /** Load from a classpath resource path. */
    public MockHotelInventory(String classpathResource) {
        Objects.requireNonNull(classpathResource, "classpathResource");
        InventoryFile file = readFile(classpathResource);

        Map<String, List<Hotel>> byCity = new HashMap<>();
        Map<String, Hotel> byId = new HashMap<>();
        for (CityBlock city : file.cities()) {
            List<Hotel> hotels = city.hotels() == null ? List.of() : city.hotels();
            byCity.put(normalizeCityName(city.cityName()), List.copyOf(hotels));
            for (Hotel h : hotels) {
                byId.put(h.hotelId(), h);
            }
        }
        this.hotelsByCity = Map.copyOf(byCity);
        this.hotelsById = Map.copyOf(byId);
    }

    /** Total number of hotels loaded (across all cities). */
    public int totalHotels() {
        return hotelsById.size();
    }

    /** Number of cities loaded. */
    public int totalCities() {
        return hotelsByCity.size();
    }

    /**
     * Get all hotels for a city. Accepts any spelling the LLM is likely to produce
     * (e.g. "北京", "北京市", "Beijing"); returns an empty list if the city is unknown.
     */
    public List<Hotel> hotelsInCity(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            return List.of();
        }
        return hotelsByCity.getOrDefault(normalizeCityName(cityName), List.of());
    }

    /** Look up a single hotel by its id. */
    public Hotel findById(String hotelId) {
        return hotelId == null ? null : hotelsById.get(hotelId);
    }

    /**
     * Normalize a city name written by the LLM into the canonical key used in storage.
     *
     * <p>Rules: trim, drop a trailing 市/省, fold case, then look up English aliases. The
     * canonical form for storage is the Chinese name without the 市 suffix (e.g. "北京").
     */
    public static String normalizeCityName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (s.endsWith("市") || s.endsWith("省")) {
            s = s.substring(0, s.length() - 1);
        }
        String lower = s.toLowerCase(Locale.ROOT);
        String mapped = ENGLISH_TO_CHINESE.get(lower);
        return mapped != null ? mapped : s;
    }

    private static InventoryFile readFile(String resource) {
        try (InputStream in = openResource(resource)) {
            return MAPPER.readValue(in, InventoryFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load hotel inventory from " + resource, e);
        }
    }

    private static InputStream openResource(String resource) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = MockHotelInventory.class.getClassLoader();
        }
        InputStream in = cl.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("classpath resource not found: " + resource);
        }
        return in;
    }

    private static Map<String, String> buildEnglishMap() {
        Map<String, String> m = new HashMap<>();
        m.put("beijing", "北京");
        m.put("shanghai", "上海");
        m.put("tianjin", "天津");
        m.put("chongqing", "重庆");
        m.put("shijiazhuang", "石家庄");
        m.put("taiyuan", "太原");
        m.put("shenyang", "沈阳");
        m.put("changchun", "长春");
        m.put("harbin", "哈尔滨");
        m.put("nanjing", "南京");
        m.put("hangzhou", "杭州");
        m.put("hefei", "合肥");
        m.put("fuzhou", "福州");
        m.put("nanchang", "南昌");
        m.put("jinan", "济南");
        m.put("zhengzhou", "郑州");
        m.put("wuhan", "武汉");
        m.put("changsha", "长沙");
        m.put("guangzhou", "广州");
        m.put("haikou", "海口");
        m.put("chengdu", "成都");
        m.put("guiyang", "贵阳");
        m.put("kunming", "昆明");
        m.put("xian", "西安");
        m.put("xi'an", "西安");
        m.put("lanzhou", "兰州");
        m.put("xining", "西宁");
        m.put("hohhot", "呼和浩特");
        m.put("yinchuan", "银川");
        m.put("urumqi", "乌鲁木齐");
        m.put("lhasa", "拉萨");
        m.put("nanning", "南宁");
        return Collections.unmodifiableMap(m);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InventoryFile(List<CityBlock> cities) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CityBlock(String cityName, String tier, List<Hotel> hotels) {
    }
}

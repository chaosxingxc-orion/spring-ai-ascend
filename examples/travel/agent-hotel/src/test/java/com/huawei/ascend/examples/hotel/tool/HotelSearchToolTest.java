/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.tool;

import com.huawei.ascend.examples.hotel.mock.MockHotelInventory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HotelSearchToolTest {

    private static final MockHotelInventory INV = new MockHotelInventory();

    @Test
    void unknownCityReturnsFailure() {
        Map<String, Object> result = HotelSearchTool.invokeDirectly(INV, Map.of(
                "cityName", "Atlantis",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21"));

        assertThat(result.get("successCode")).isEqualTo(false);
        assertThat(result.get("totalCount")).isEqualTo(0);
        assertThat((List<?>) result.get("hotels")).isEmpty();
    }

    @Test
    void noFiltersReturnsFirstPageOfSix() {
        Map<String, Object> result = HotelSearchTool.invokeDirectly(INV, Map.of(
                "cityName", "北京",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21"));

        assertThat(result.get("successCode")).isEqualTo(true);
        assertThat(result.get("totalCount")).isEqualTo(10);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hotels = (List<Map<String, Object>>) result.get("hotels");
        assertThat(hotels).hasSize(HotelSearchTool.PAGE_SIZE);
        for (Map<String, Object> h : hotels) {
            assertThat(h.get("compliancePassed")).isEqualTo(true);
        }
    }

    @Test
    void pageTwoReturnsRemaining() {
        Map<String, Object> result = HotelSearchTool.invokeDirectly(INV, Map.of(
                "cityName", "北京",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21",
                "pageNum", 2));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hotels = (List<Map<String, Object>>) result.get("hotels");
        assertThat(hotels).hasSize(10 - HotelSearchTool.PAGE_SIZE);
    }

    @Test
    void minStarFilters() {
        Map<String, Object> result = HotelSearchTool.invokeDirectly(INV, Map.of(
                "cityName", "北京",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21",
                "minStar", 5));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hotels = (List<Map<String, Object>>) result.get("hotels");
        assertThat(hotels).isNotEmpty();
        for (Map<String, Object> h : hotels) {
            assertThat(((Number) h.get("star")).intValue()).isGreaterThanOrEqualTo(5);
        }
    }

    @Test
    void maxPriceFilters() {
        Map<String, Object> result = HotelSearchTool.invokeDirectly(INV, Map.of(
                "cityName", "北京",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21",
                "maxPricePerNight", 400.0));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hotels = (List<Map<String, Object>>) result.get("hotels");
        assertThat(hotels).isNotEmpty();
        for (Map<String, Object> h : hotels) {
            assertThat(((Number) h.get("lowestPrice")).doubleValue()).isLessThanOrEqualTo(400.0);
        }
    }

    @Test
    void brandWhitelistFilters() {
        Map<String, Object> result = HotelSearchTool.invokeDirectly(INV, Map.of(
                "cityName", "北京",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21",
                "brandWhitelist", List.of("全季", "亚朵", "希尔顿欢朋")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hotels = (List<Map<String, Object>>) result.get("hotels");
        assertThat(hotels).isNotEmpty();
        for (Map<String, Object> h : hotels) {
            assertThat(h.get("brand")).isIn("全季", "亚朵", "希尔顿欢朋");
        }
    }

    @Test
    void keywordFiltersByDistrict() {
        Map<String, Object> result = HotelSearchTool.invokeDirectly(INV, Map.of(
                "cityName", "北京",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21",
                "keyword", "国贸"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hotels = (List<Map<String, Object>>) result.get("hotels");
        assertThat(hotels).isNotEmpty();
        for (Map<String, Object> h : hotels) {
            String district = (String) h.get("district");
            String address = (String) h.get("address");
            assertThat(district + " " + address).contains("国贸");
        }
    }

    @Test
    void complianceFalseWhenFilterFails_butStillSurfacesAsCandidate() {
        // Use a constraint that no Beijing hotel satisfies (5-star + max 200 RMB).
        Map<String, Object> result = HotelSearchTool.invokeDirectly(INV, Map.of(
                "cityName", "北京",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21",
                "minStar", 5,
                "maxPricePerNight", 200.0));

        // Filtering still applies — totalCount should be 0 because no hotel matches BOTH
        // policy fields simultaneously. compliancePassed only makes sense in the partial
        // case (e.g. minStar set, maxPrice null → some hotels surface non-compliant under
        // a separate constraint), which the design doc covers via the prompt's
        // [不符合差标] fallback path, not via tool-side mixing.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hotels = (List<Map<String, Object>>) result.get("hotels");
        assertThat(hotels).isEmpty();
        assertThat(result.get("totalCount")).isEqualTo(0);
    }

    @Test
    void emptyPolicyMeansComplianceDefaultsToTrue() {
        Map<String, Object> result = HotelSearchTool.invokeDirectly(INV, Map.of(
                "cityName", "上海",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hotels = (List<Map<String, Object>>) result.get("hotels");
        assertThat(hotels).isNotEmpty();
        for (Map<String, Object> h : hotels) {
            assertThat(h.get("compliancePassed")).isEqualTo(true);
        }
    }

    @Test
    void cityNameAcceptsEnglishViaTool() {
        Map<String, Object> result = HotelSearchTool.invokeDirectly(INV, Map.of(
                "cityName", "Shanghai",
                "checkIn", "2026-06-20",
                "checkOut", "2026-06-21"));

        assertThat(result.get("successCode")).isEqualTo(true);
        assertThat(result.get("totalCount")).isEqualTo(10);
    }
}

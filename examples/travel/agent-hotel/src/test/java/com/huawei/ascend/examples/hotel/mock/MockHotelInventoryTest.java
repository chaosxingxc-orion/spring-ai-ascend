/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.mock;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MockHotelInventoryTest {

    private static final MockHotelInventory INVENTORY = new MockHotelInventory();

    @Test
    void loadsAll31CitiesAnd310Hotels() {
        assertThat(INVENTORY.totalCities()).isEqualTo(31);
        assertThat(INVENTORY.totalHotels()).isEqualTo(310);
    }

    @Test
    void everyCityHasExactlyTenHotels() {
        List<String> expectedCities = List.of(
                "北京", "上海", "天津", "重庆",
                "石家庄", "太原", "沈阳", "长春", "哈尔滨", "南京", "杭州", "合肥", "福州",
                "南昌", "济南", "郑州", "武汉", "长沙", "广州", "海口", "成都", "贵阳",
                "昆明", "西安", "兰州", "西宁",
                "呼和浩特", "银川", "乌鲁木齐", "拉萨", "南宁");

        for (String city : expectedCities) {
            assertThat(INVENTORY.hotelsInCity(city))
                    .as("city %s must have 10 hotels", city)
                    .hasSize(10);
        }
    }

    @Test
    void cityNameNormalizationAcceptsCommonVariants() {
        List<Hotel> withBareName = INVENTORY.hotelsInCity("北京");
        List<Hotel> withCitySuffix = INVENTORY.hotelsInCity("北京市");
        List<Hotel> withEnglish = INVENTORY.hotelsInCity("Beijing");
        List<Hotel> withSpacesAndCase = INVENTORY.hotelsInCity("  BEIJING  ");

        assertThat(withBareName).isNotEmpty();
        assertThat(withCitySuffix).isEqualTo(withBareName);
        assertThat(withEnglish).isEqualTo(withBareName);
        assertThat(withSpacesAndCase).isEqualTo(withBareName);
    }

    @Test
    void unknownCityReturnsEmpty() {
        assertThat(INVENTORY.hotelsInCity("Atlantis")).isEmpty();
        assertThat(INVENTORY.hotelsInCity("")).isEmpty();
        assertThat(INVENTORY.hotelsInCity(null)).isEmpty();
    }

    @Test
    void allStarsBetween2And5() {
        for (int i = 0; i < INVENTORY.totalCities(); i++) {
            for (Hotel h : INVENTORY.hotelsInCity("北京")) {
                assertThat(h.star()).isBetween(2, 5);
            }
        }
    }

    @Test
    void hotelIdsAreUniqueAcrossCatalog() {
        Set<String> seen = new java.util.HashSet<>();
        int count = 0;
        for (String city : List.of(
                "北京", "上海", "天津", "重庆", "广州", "杭州", "成都",
                "西安", "拉萨", "乌鲁木齐", "海口", "南宁")) {
            for (Hotel h : INVENTORY.hotelsInCity(city)) {
                assertThat(seen.add(h.hotelId()))
                        .as("duplicate hotelId %s", h.hotelId())
                        .isTrue();
                count++;
            }
        }
        assertThat(count).isPositive();
    }

    @Test
    void findByIdResolvesKnownHotelAndReturnsNullForUnknown() {
        Hotel any = INVENTORY.hotelsInCity("北京").get(0);
        assertThat(INVENTORY.findById(any.hotelId())).isSameAs(any);
        assertThat(INVENTORY.findById("nonexistent-id")).isNull();
        assertThat(INVENTORY.findById(null)).isNull();
    }

    @Test
    void normalizeCityNameStripsSuffixes() {
        assertThat(MockHotelInventory.normalizeCityName("北京市")).isEqualTo("北京");
        assertThat(MockHotelInventory.normalizeCityName("广东省")).isEqualTo("广东");
        assertThat(MockHotelInventory.normalizeCityName(" Chengdu ")).isEqualTo("成都");
        assertThat(MockHotelInventory.normalizeCityName(null)).isEqualTo("");
    }
}

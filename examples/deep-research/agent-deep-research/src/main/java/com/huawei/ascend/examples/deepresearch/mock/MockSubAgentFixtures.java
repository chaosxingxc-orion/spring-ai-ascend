/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Loads mock sub-agent JSON fixtures for Phase-2 orchestration tests (TOPOLOGY §3.2–§3.4).
 */
public final class MockSubAgentFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private MockSubAgentFixtures() {
    }

    public static Map<String, Object> searchResults() {
        return load("fixtures/search/default-results.json");
    }

    public static Map<String, Object> readOfficialPricing() {
        return load("fixtures/read/official-pricing.json");
    }

    public static Map<String, Object> readSpaBlocked() {
        return load("fixtures/read/spa-blocked.json");
    }

    public static Map<String, Object> readCloudflare403() {
        return load("fixtures/read/cloudflare-403.json");
    }

    public static Map<String, Object> verifySupport() {
        return load("fixtures/verify/support.json");
    }

    public static Map<String, Object> verifyContradict() {
        return load("fixtures/verify/contradict.json");
    }

    public static Map<String, Object> verifyInsufficient() {
        return load("fixtures/verify/insufficient.json");
    }

    private static Map<String, Object> load(String resourcePath) {
        try (InputStream input = MockSubAgentFixtures.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing fixture: " + resourcePath);
            }
            return MAPPER.readValue(input, MAP_TYPE);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, error);
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;

final class DeepResearchFixtureTexts {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DeepResearchFixtureTexts() {
    }

    static String round1Question() {
        return fixtureUserText("a2a-fixtures/research-round1-stream.json");
    }

    static String round2Question() {
        return fixtureUserText("a2a-fixtures/research-round2-memory-stream.json");
    }

    private static String fixtureUserText(String resourcePath) {
        try (InputStream input = DeepResearchFixtureTexts.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing fixture: " + resourcePath);
            }
            JsonNode root = MAPPER.readTree(input);
            JsonNode textNode = root.path("params").path("message").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new IllegalStateException("Fixture has no user text: " + resourcePath);
            }
            return textNode.asText();
        } catch (Exception error) {
            throw new IllegalStateException("Failed to load fixture: " + resourcePath, error);
        }
    }
}

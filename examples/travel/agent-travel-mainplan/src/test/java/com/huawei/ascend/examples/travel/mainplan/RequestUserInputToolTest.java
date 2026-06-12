/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan;

import com.huawei.ascend.examples.travel.mainplan.tools.RequestUserInputTool;
import com.openjiuwen.core.foundation.tool.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link RequestUserInputTool}.
 */
class RequestUserInputToolTest {

    @Test
    @DisplayName("Tool card should have correct id and name")
    void testToolCardMetadata() {
        Tool tool = new RequestUserInputTool();

        assertEquals("request_user_input", tool.getCard().getId());
        assertEquals("request_user_input", tool.getCard().getName());
        assertNotNull(tool.getCard().getDescription());
    }

    @Test
    @DisplayName("Invoke should return default message (actual logic is in Rail)")
    void testInvokeReturnsDefault() throws Exception {
        Tool tool = new RequestUserInputTool();

        Object result = tool.invoke(
                Map.of(
                        "missing_fields", List.of("目的地"),
                        "follow_up_message", "请问您要去哪个城市？"
                ),
                Map.of()
        );

        assertEquals("user_input_collected", result);
    }

    @Test
    @DisplayName("Tool card input params should declare missing_fields and follow_up_message")
    void testInputParamsSchema() {
        Tool tool = new RequestUserInputTool();
        Map<String, Object> inputParams = tool.getCard().getInputParams();

        assertNotNull(inputParams);
        assertEquals("object", inputParams.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputParams.get("properties");
        assertNotNull(properties);
        assertNotNull(properties.get("missing_fields"));
        assertNotNull(properties.get("follow_up_message"));
    }
}
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan;

import com.huawei.ascend.examples.travel.mainplan.tools.DispatchTravelPlanTool;
import com.openjiuwen.core.foundation.tool.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DispatchTravelPlanTool}.
 */
class DispatchTravelPlanToolTest {

    @Test
    @DisplayName("Should return dispatcher result when instruction is provided")
    void testDispatchSuccess() throws Exception {
        AtomicReference<String> capturedInstruction = new AtomicReference<>();
        Tool tool = new DispatchTravelPlanTool(instruction -> {
            capturedInstruction.set(instruction);
            return "往返行程方案：上海→北京 8月2日，北京→上海 8月3日";
        });

        Object result = tool.invoke(
                Map.of("instruction", "张三从上海到北京出差，去程日期是2026年8月2日，返程日期是2026年8月3日。"),
                Map.of()
        );

        assertEquals("往返行程方案：上海→北京 8月2日，北京→上海 8月3日", result);
        assertEquals("张三从上海到北京出差，去程日期是2026年8月2日，返程日期是2026年8月3日。",
                capturedInstruction.get());
    }

    @Test
    @DisplayName("Should return error message when dispatcher throws exception")
    void testDispatchException() throws Exception {
        Tool tool = new DispatchTravelPlanTool(instruction -> {
            throw new RuntimeException("connection timeout");
        });

        Object result = tool.invoke(
                Map.of("instruction", "张三从深圳到上海出差，出发日期是2026年8月5日。"),
                Map.of()
        );

        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("行程规划智能体调用失败"),
                "Should contain error prefix, got: " + resultStr);
        assertTrue(resultStr.contains("connection timeout"),
                "Should contain original error message, got: " + resultStr);
    }

    @Test
    @DisplayName("Tool card should have correct id and name")
    void testToolCardMetadata() {
        Tool tool = new DispatchTravelPlanTool(instruction -> "ok");

        assertEquals("dispatch_travel_plan", tool.getCard().getId());
        assertEquals("dispatch_travel_plan", tool.getCard().getName());
        assertNotNull(tool.getCard().getDescription());
        assertTrue(tool.getCard().getDescription().length() > 0);
    }
}
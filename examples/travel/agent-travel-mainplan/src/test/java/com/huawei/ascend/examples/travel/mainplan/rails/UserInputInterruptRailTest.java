/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan.rails;

import com.huawei.ascend.examples.travel.mainplan.rails.UserInputInterruptRail;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link UserInputInterruptRail}.
 */
class UserInputInterruptRailTest {

    private UserInputInterruptRail rail;

    @BeforeEach
    void setUp() {
        rail = new UserInputInterruptRail();
    }

    private ToolCall buildToolCall(String arguments) {
        return ToolCall.builder()
                .id("test-call-id")
                .name("request_user_input")
                .arguments(arguments)
                .build();
    }

    @Test
    @DisplayName("resolveInterrupt with no user input returns InterruptResult")
    void testInterruptWithoutUserInput() {
        ToolCall toolCall = buildToolCall(
                "{\"missing_fields\":[\"目的地\"],\"follow_up_message\":\"请问您要去哪个城市？\"}");

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, null);

        assertNotNull(decision);
        String typeName = decision.getClass().getSimpleName();
        assert typeName.equals("InterruptResult")
                : "Expected InterruptResult but got " + typeName;
    }

    @Test
    @DisplayName("resolveInterrupt with user input returns ApproveResult")
    void testApproveWithUserInput() {
        ToolCall toolCall = buildToolCall(
                "{\"missing_fields\":[\"目的地\"],\"follow_up_message\":\"去哪？\"}");

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, "北京");

        assertNotNull(decision);
        String typeName = decision.getClass().getSimpleName();
        assert typeName.equals("ApproveResult")
                : "Expected ApproveResult but got " + typeName;
    }

    @Test
    @DisplayName("resolveInterrupt handles malformed JSON arguments")
    void testMalformedArguments() {
        ToolCall toolCall = buildToolCall("not valid json {{{");

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, null);

        assertNotNull(decision);
        String typeName = decision.getClass().getSimpleName();
        assert typeName.equals("InterruptResult")
                : "Expected InterruptResult but got " + typeName;
    }

    @Test
    @DisplayName("resolveInterrupt handles null arguments")
    void testNullArguments() {
        ToolCall toolCall = buildToolCall(null);

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, null);

        assertNotNull(decision);
        String typeName = decision.getClass().getSimpleName();
        assert typeName.equals("InterruptResult")
                : "Expected InterruptResult but got " + typeName;
    }

    @Test
    @DisplayName("resolveInterrupt with empty user input still approves")
    void testEmptyUserInput() {
        ToolCall toolCall = buildToolCall(
                "{\"missing_fields\":[\"目的地\"],\"follow_up_message\":\"去哪？\"}");

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, "");

        assertNotNull(decision);
        String typeName = decision.getClass().getSimpleName();
        assert typeName.equals("ApproveResult")
                : "Expected ApproveResult but got " + typeName;
    }
}
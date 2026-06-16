/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TripPromptContractTest {

    @Test
    void remoteHotelToolPromptUsesRuntimeRemoteInputArgument() {
        String prompt = SystemPromptBuilder.build("hotel-planning-agent");

        assertThat(prompt)
                .contains("remoteInput")
                .doesNotContain("message 字段");
    }
}

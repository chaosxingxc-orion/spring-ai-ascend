/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.travel.mainplan.a2a.constant.AgentConstants;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MainPlanPromptContractTest {

    @Test
    void remoteTripToolPromptUsesRuntimeRemoteInputArgument() throws Exception {
        String prompt;
        try (InputStream is = MainPlanPromptContractTest.class.getResourceAsStream(
                AgentConstants.PROMPT_RESOURCE_PATH)) {
            assertThat(is).isNotNull();
            prompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(prompt)
                .contains("remoteInput")
                .doesNotContain("message 字段");
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DeepResearchPromptContractTest {

    @Test
    void systemPromptCoversRemoteToolsErrorPathsAndOutputSchema() throws IOException {
        String prompt = readResource(DeepResearchConstants.SYSTEM_PROMPT_RESOURCE);

        assertThat(prompt).contains(DeepResearchConstants.REMOTE_TOOL_PLAN_SEARCH);
        assertThat(prompt).contains(DeepResearchConstants.REMOTE_TOOL_PLAN_READ);
        assertThat(prompt).contains(DeepResearchConstants.REMOTE_TOOL_PLAN_VERIFY);
        assertThat(prompt).contains("remoteInput");
        assertThat(prompt).contains("spa_blocked");
        assertThat(prompt).contains("cloudflare_403");
        assertThat(prompt).contains("insufficient");
        assertThat(prompt).contains("contradict");
        assertThat(prompt).contains("partial");
        assertThat(prompt).contains("comparison_table");
        assertThat(prompt).contains("confidence_per_field");
        assertThat(prompt).contains("citations");
        assertThat(prompt).contains("as_of_date");
        assertThat(prompt).contains("claim_type");
        assertThat(prompt).contains("长期记忆");
    }

    private static String readResource(String resourcePath) throws IOException {
        try (InputStream input = DeepResearchPromptContractTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(input).as("resource %s", resourcePath).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

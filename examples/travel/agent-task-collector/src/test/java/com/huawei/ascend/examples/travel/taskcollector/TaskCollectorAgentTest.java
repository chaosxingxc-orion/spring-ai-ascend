/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskCollectorAgentTest {

    @Test
    void registersThreeMockMcpTools() {
        LlmConfig llm = new LlmConfig("OpenAI", "", "http://localhost:4000/v1", "gpt-4o-mini", false);
        try (TaskCollectorAgent agent = new TaskCollectorAgent(llm)) {
            assertThat(agent.toolCount()).isEqualTo(3);
            assertThat(agent.newBaseAgent()).isNotNull();
        }
    }
}

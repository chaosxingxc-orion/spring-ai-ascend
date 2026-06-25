/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.trip.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TripPromptContractTest {

    @Test
    void remoteAgentToolsUseRuntimeRemoteInputArgument() {
        String prompt = SystemPromptBuilder.build("hotel-planning-agent", "task-collector-agent");

        assertThat(prompt)
                .contains("remoteInput")
                .contains("hotel-planning-agent")
                .contains("task-collector-agent")
                .contains("优先调用 task-collector-agent")
                .contains("随后调用 hotel-planning-agent")
                .contains("可以直接调用 hotel-planning-agent")
                .doesNotContain("message 字段");
    }

    @Test
    void defaultBuilderInjectsTaskCollectorToolName() {
        String prompt = SystemPromptBuilder.build("hotel-planning-agent");

        assertThat(prompt)
                .contains("hotel-planning-agent")
                .contains("task-collector-agent")
                .doesNotContain(TripAgentConstants.VAR_TASK_COLLECTOR_TOOL_NAME);
    }
}

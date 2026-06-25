/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.travel.taskcollector.prompt.SystemPromptBuilder;
import org.junit.jupiter.api.Test;

class TaskCollectorAgentPromptTest {

    @Test
    void injectsRuntimeContextAndToolNames() {
        String prompt = SystemPromptBuilder.build("zhang3", "深圳");

        assertThat(prompt)
                .contains("默认用户 ID：zhang3")
                .contains("默认城市：深圳")
                .contains(TaskCollectorAgentConstants.TOOL_CALENDAR_SEARCH)
                .contains(TaskCollectorAgentConstants.TOOL_TODO_SEARCH)
                .contains(TaskCollectorAgentConstants.TOOL_POLICY_LOOKUP)
                .doesNotContain(TaskCollectorAgentConstants.VAR_USER_ID)
                .doesNotContain(TaskCollectorAgentConstants.VAR_DEFAULT_CITY);
    }
}

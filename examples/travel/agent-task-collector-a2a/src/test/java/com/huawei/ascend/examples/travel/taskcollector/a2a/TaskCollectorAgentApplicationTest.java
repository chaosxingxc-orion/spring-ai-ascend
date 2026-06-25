/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;

class TaskCollectorAgentApplicationTest {

    @Test
    void agentCardUsesTaskCollectorNameAndA2aEndpoint() {
        TaskCollectorAgentConfiguration configuration = new TaskCollectorAgentConfiguration();

        AgentCard card = configuration.taskCollectorAgentCard();

        assertThat(card.name()).isEqualTo(TaskCollectorAgentA2aConstants.AGENT_ID);
        assertThat(card.supportedInterfaces())
                .anySatisfy(agentInterface -> assertThat(agentInterface.url()).isEqualTo("/a2a"));
        assertThat(card.skills())
                .anySatisfy(skill -> assertThat(skill.id()).isEqualTo("travel-task-collection"));
    }
}

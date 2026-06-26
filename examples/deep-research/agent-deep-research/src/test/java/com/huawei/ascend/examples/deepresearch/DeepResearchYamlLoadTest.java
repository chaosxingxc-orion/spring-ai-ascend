/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.agentsdk.factory.AgentFactory;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import org.junit.jupiter.api.Test;

class DeepResearchYamlLoadTest {

    @Test
    void loadsProdYamlIntoDeepAgent() {
        DeepAgent agent = AgentFactory.toDeepAgent(DeepResearchAgentSpecMaterializer.materializeProdYaml());

        assertThat(agent.getCard().getId()).isEqualTo(DeepResearchConstants.AGENT_ID);
        assertThat(agent.getConfig().getMaxIterations()).isEqualTo(10);
        assertThat(agent.getConfig().isEnableTaskLoop()).isTrue();
        assertThat(agent.getConfig().getTools()).isEmpty();
    }
}

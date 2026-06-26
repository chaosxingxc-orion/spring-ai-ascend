/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.agentsdk.factory.AgentFactory;
import com.huawei.ascend.examples.deepresearch.DeepResearchAgentSpecMaterializer;
import com.huawei.ascend.examples.deepresearch.DeepResearchConstants;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenRemoteToolInstaller;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeepResearchRemoteToolInstallerTest {

    @Test
    void installsThreeTopologyRemoteToolsOnDeepAgent() {
        DeepAgent agent = AgentFactory.toDeepAgent(DeepResearchAgentSpecMaterializer.materializeProdYaml());
        OpenJiuwenRemoteToolInstaller installer =
                new OpenJiuwenRemoteToolInstaller(DeepResearchRemoteToolSpecs::all);

        installer.install(agent, context());

        assertThat(agent.getRegisteredTools()).hasSize(3);
        assertThat(agent.getAgent().getAbilityManager().get(DeepResearchConstants.REMOTE_TOOL_PLAN_SEARCH))
                .isNotNull();
        assertThat(agent.getAgent().getAbilityManager().get(DeepResearchConstants.REMOTE_TOOL_PLAN_READ))
                .isNotNull();
        assertThat(agent.getAgent().getAbilityManager().get(DeepResearchConstants.REMOTE_TOOL_PLAN_VERIFY))
                .isNotNull();
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "ctx-1", "task-1", DeepResearchConstants.AGENT_ID),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("截至 2026 Q2，对比五家大模型 API")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-1"));
    }
}

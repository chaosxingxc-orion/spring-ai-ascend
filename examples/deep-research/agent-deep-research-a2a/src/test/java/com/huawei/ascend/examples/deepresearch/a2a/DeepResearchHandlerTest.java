/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.deepresearch.DeepResearchAgentSpecMaterializer;
import com.huawei.ascend.examples.deepresearch.DeepResearchConstants;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenDeepAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenRemoteToolInstaller;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeepResearchHandlerTest {

    private static Path prodYaml() {
        return DeepResearchAgentSpecMaterializer.materializeProdYaml();
    }

    @Test
    void handlerBuildsDeepAgentFromYamlWithoutHandwrittenConfig() {
        DeepResearchConfiguration.DeepResearchHandler handler =
                new DeepResearchConfiguration.DeepResearchHandler(prodYaml(), null);

        DeepAgent agent = handler.createOpenJiuwenDeepAgent(null);

        assertThat(agent.getCard().getId()).isEqualTo(DeepResearchConstants.AGENT_ID);
        assertThat(agent.getConfig().isEnableTaskLoop()).isTrue();
        assertThat(agent.getConfig().getTools()).isEmpty();
    }

    @Test
    void handlerExposesReadFileForSkillHubRuntime() {
        DeepAgent agent = DeepResearchDeepAgentSupport.loadDeepAgent(prodYaml());

        assertThat(agent.getAgent().getSkillUtil()).isNotNull();
        assertThat(agent.getAgent().getAbilityManager().get("readFile")).isNotNull();
        assertThat(agent.getAgent().getConfig()).isInstanceOf(ReActAgentConfig.class);
        ReActAgentConfig reactConfig = (ReActAgentConfig) agent.getAgent().getConfig();
        assertThat(reactConfig.getSysOperationId())
                .isEqualTo(DeepResearchDeepAgentSupport.harnessSysOperationId(agent));
    }

    @Test
    void handlerWorksWithMockRemoteToolInstaller() {
        DeepResearchConfiguration.DeepResearchHandler handler =
                new DeepResearchConfiguration.DeepResearchHandler(prodYaml(), null);
        handler.setRuntimeToolInstaller(new OpenJiuwenRemoteToolInstaller(DeepResearchRemoteToolSpecs::all));

        DeepAgent agent = handler.createOpenJiuwenDeepAgent(remoteContext());
        new OpenJiuwenRemoteToolInstaller(DeepResearchRemoteToolSpecs::all).install(agent, remoteContext());

        assertThat(agent.getRegisteredTools()).hasSize(3);
        assertThat(agent.getAgent().getAbilityManager().get(DeepResearchRemoteToolSpecs.SEARCH_AGENT))
                .isNotNull();
        assertThat(agent.getAgent().getAbilityManager().get(DeepResearchRemoteToolSpecs.READ_AGENT))
                .isNotNull();
        assertThat(agent.getAgent().getAbilityManager().get(DeepResearchRemoteToolSpecs.VERIFY_AGENT))
                .isNotNull();
    }

    @Test
    void handlerExtendsOpenJiuwenDeepAgentRuntimeHandler() {
        assertThat(OpenJiuwenDeepAgentRuntimeHandler.class)
                .isAssignableFrom(DeepResearchConfiguration.DeepResearchHandler.class);
    }

    @Test
    void memoryRailsAreEmptyWhenProviderAbsent() {
        DeepResearchConfiguration.DeepResearchHandler handler =
                new DeepResearchConfiguration.DeepResearchHandler(prodYaml(), null);

        assertThat(handler.openJiuwenRails((AgentExecutionContext) null)).isEmpty();
    }

    private static AgentExecutionContext remoteContext() {
        return new AgentExecutionContext(
                new com.huawei.ascend.runtime.common.RuntimeIdentity(
                        "tenant", "user", "ctx-1", "task-1", DeepResearchConstants.AGENT_ID),
                "USER_MESSAGE",
                java.util.List.of(com.huawei.ascend.runtime.common.RuntimeMessage.user("research")),
                java.util.Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-1"));
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.deepresearch.DeepResearchAgentSpecMaterializer;
import com.huawei.ascend.examples.deepresearch.DeepResearchConstants;
import com.huawei.ascend.examples.deepresearch.a2a.middleware.LocalDirectorySkillHubProvider;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenSkillHubInstaller;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeepResearchMiddlewareInstallerTest {

    @Test
    void installsSkillHubSkillsOnDeepAgent(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("llm-api-comparison");
        Files.createDirectories(skillDir);
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                ---
                description: Compare domestic LLM API vendors.
                ---
                # LLM API Comparison
                Include proof marker: DEEP_RESEARCH_SKILLHUB_USED
                """);

        DeepAgent agent = DeepResearchDeepAgentSupport.loadDeepAgent(
                DeepResearchAgentSpecMaterializer.materializeProdYaml());
        OpenJiuwenSkillHubInstaller installer =
                new OpenJiuwenSkillHubInstaller(new LocalDirectorySkillHubProvider(tempDir));

        installer.install(agent, context());

        assertThat(agent.getAgent().getSkillUtil()).isNotNull();
        assertThat(agent.getAgent().getSkillUtil().hasSkill()).isTrue();
        assertThat(agent.getAgent().getSkillUtil().getSkillPrompt()).contains("Compare domestic LLM API vendors");
        assertThat(agent.getAgent().getAbilityManager().get("readFile")).isNotNull();
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "ctx-1", "task-1", DeepResearchConstants.AGENT_ID),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("截至 2026 Q2，对比五家大模型 API")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-1"));
    }
}

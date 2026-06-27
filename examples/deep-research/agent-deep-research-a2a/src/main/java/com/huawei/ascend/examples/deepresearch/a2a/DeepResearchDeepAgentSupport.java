/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import com.huawei.ascend.agentsdk.factory.AgentFactory;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.nio.file.Path;

/**
 * Loads the root DeepAgent and applies runtime-only wiring that YAML cannot express.
 */
final class DeepResearchDeepAgentSupport {

    private DeepResearchDeepAgentSupport() {
    }

    static DeepAgent loadDeepAgent(Path yamlPath) {
        DeepAgent deepAgent = AgentFactory.toDeepAgent(yamlPath);
        enableSkillHubRuntime(deepAgent);
        return deepAgent;
    }

    /**
     * SkillHub installer registers skills on the inner {@link com.openjiuwen.core.singleagent.BaseAgent}.
     * {@code SkillUtil} is only created when {@code sysOperationId} is present on ReAct config.
     * OpenJiuwen skill prompts expect {@code readFile} in the inner ReAct ability manager.
     */
    static void enableSkillHubRuntime(DeepAgent deepAgent) {
        Object config = deepAgent.getAgent().getConfig();
        if (!(config instanceof ReActAgentConfig reactConfig)) {
            return;
        }
        String harnessSysOperationId = harnessSysOperationId(deepAgent);
        if (!harnessSysOperationId.equals(reactConfig.getSysOperationId())) {
            ReActAgentConfig updated = ReActAgentConfig.builder()
                    .memScopeId(reactConfig.getMemScopeId())
                    .modelName(reactConfig.getModelName())
                    .modelProvider(reactConfig.getModelProvider())
                    .apiKey(reactConfig.getApiKey())
                    .apiBase(reactConfig.getApiBase())
                    .promptTemplateName(reactConfig.getPromptTemplateName())
                    .promptTemplate(reactConfig.getPromptTemplate())
                    .promptMode(reactConfig.getPromptMode())
                    .customHeaders(reactConfig.getCustomHeaders())
                    .maxIterations(reactConfig.getMaxIterations())
                    .modelClientConfig(reactConfig.getModelClientConfig())
                    .modelConfigObj(reactConfig.getModelConfigObj())
                    .sysOperationId(harnessSysOperationId)
                    .contextEngineConfig(reactConfig.getContextEngineConfig())
                    .contextProcessors(reactConfig.getContextProcessors())
                    .build();
            deepAgent.getAgent().configure(updated);
        }
        exposeReadFileTool(deepAgent.getAgent(), harnessSysOperationId);
    }

    static String harnessSysOperationId(DeepAgent deepAgent) {
        AgentCard card = deepAgent.getCard();
        String displayName = card.getName();
        if (displayName == null || displayName.isBlank()) {
            displayName = "deep_agent";
        }
        return displayName + "_" + card.getId();
    }

    static void exposeReadFileTool(BaseAgent agent, String sysOperationId) {
        if (agent.getAbilityManager().get("readFile") != null) {
            return;
        }
        Object toolCard = Runner.resourceMgr().getSysOpToolCards(sysOperationId, "fs", "readFile");
        if (toolCard != null) {
            agent.getAbilityManager().add(toolCard);
        }
    }
}

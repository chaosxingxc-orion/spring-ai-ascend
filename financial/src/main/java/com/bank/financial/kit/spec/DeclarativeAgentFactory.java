package com.bank.financial.kit.spec;

import com.bank.financial.kit.approval.RuleBasedApprovalRail;
import com.bank.financial.kit.compliance.ComplianceRail;
import com.bank.financial.kit.spec.AgentDefinition.ToolDef;
import com.bank.financial.kit.tool.HttpTool;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.security.guardrail.GuardrailBackend;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a runnable openJiuwen {@link ReActAgent} from an {@link AgentDefinition}.
 * Shared by the served runtime handler and the local playground, so "what runs"
 * is identical in both. Tools are registered the two-step way the framework
 * requires (ability metadata + resource-manager instance).
 */
public final class DeclarativeAgentFactory {

    private DeclarativeAgentFactory() {
    }

    /** Build the agent and register its declared HTTP tools. */
    public static ReActAgent build(AgentDefinition def) {
        AgentCard card = AgentCard.builder()
                .id(def.id())
                .name(def.id())
                .description(def.description())
                .build();

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", def.prompt())))
                .maxIterations(def.maxIterations())
                .build()
                .configureModelClient(
                        def.model().provider(), def.model().apiKey(), def.model().apiBase(),
                        def.model().modelName(), def.model().sslVerify());

        ModelRequestConfig modelConfig = config.getModelConfigObj();
        modelConfig.setTemperature(0.3);
        modelConfig.setMaxTokens(1024);

        ReActAgent agent = new ReActAgent(card);
        agent.configure(config);

        for (ToolDef t : def.tools()) {
            LocalFunction tool = HttpTool.toLocalFunction(t);
            agent.getAbilityManager().add(tool.getCard());                 // metadata for the LLM
            Runner.resourceMgr().addTool(tool, agent.getCard().getId());   // instance for execution
        }
        return agent;
    }

    /** Approval rails declared in YAML (empty if none). */
    public static List<AgentRail> approvalRails(AgentDefinition def) {
        if (def.approvals() == null || def.approvals().isEmpty()) {
            return List.of();
        }
        List<AgentRail> rails = new ArrayList<>();
        rails.add(new RuleBasedApprovalRail(def.approvals()));
        return rails;
    }

    /** Input-compliance rail for the declared level (null if compliance not set). */
    public static AgentRail complianceRail(
            AgentDefinition def, String userText, String tenantId, GuardrailBackend backend) {
        if (def.complianceLevel() == null) {
            return null;
        }
        return new ComplianceRail(backend, def.complianceLevel(), userText, tenantId);
    }
}

package com.bank.financial.kit;

import com.bank.financial.kit.compliance.KeywordScreeningBackend;
import com.bank.financial.kit.spec.AgentDefinition;
import com.bank.financial.kit.spec.DeclarativeAgentFactory;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.security.guardrail.GuardrailBackend;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.List;

/**
 * The single generic handler that serves a YAML-defined agent over A2A — so a
 * business developer writes {@code agents/<id>.yaml} and gets a running agent
 * with zero Java. One bean of this is registered per YAML file by
 * {@code DeclarativeAgentsConfiguration}.
 *
 * <p>It reuses {@link DeclarativeAgentFactory} for the agent + tools, and the
 * kit's compliance / approval rails for the cross-cutting seams.
 */
public final class DeclarativeFinancialAgentHandler extends AbstractFinancialAgentHandler {

    private final AgentDefinition def;
    private final GuardrailBackend complianceBackend;

    public DeclarativeFinancialAgentHandler(AgentDefinition def) {
        super(def.id(), new ModelConnection(
                def.model().provider(), def.model().apiKey(), def.model().apiBase(),
                def.model().modelName(), def.model().sslVerify()));
        this.def = def;
        // Swap for a real AML/suitability backend in production.
        this.complianceBackend = new KeywordScreeningBackend();
    }

    @Override
    protected String description() {
        return def.description();
    }

    @Override
    protected String systemPrompt() {
        return def.prompt();
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        return DeclarativeAgentFactory.build(def);
    }

    @Override
    protected List<AgentRail> complianceRails(AgentExecutionContext context) {
        AgentRail rail = DeclarativeAgentFactory.complianceRail(
                def, context.lastUserText(), context.getScope().tenantId(), complianceBackend);
        return rail == null ? List.of() : List.of(rail);
    }

    @Override
    protected List<AgentRail> approvalRails(AgentExecutionContext context) {
        return DeclarativeAgentFactory.approvalRails(def);
    }
}

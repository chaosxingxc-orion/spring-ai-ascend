package com.bank.financial.agent;

import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.bank.financial.kit.ModelConnection;
import com.bank.financial.kit.compliance.ComplianceRail;
import com.bank.financial.kit.compliance.KeywordScreeningBackend;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.security.guardrail.GuardrailBackend;
import com.openjiuwen.core.security.guardrail.RiskLevel;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.List;

/**
 * First agent — a read-only financial advisor — built on the workspace kit
 * ({@link AbstractFinancialAgentHandler}): supply a prompt + description and
 * (optionally) a compliance screen. It moves no money, so no approval rail.
 *
 * <p>NOT a Spring {@code @Bean} itself: the platform hosts exactly ONE agent per
 * runtime instance, so which agent is served is chosen centrally by
 * {@code FinancialAgentServerConfiguration} (via {@code financial.agent}) from
 * {@link FinancialAgentRegistry}. This class just provides the handler.
 */
public final class FinancialAdvisorAgentConfiguration {

    static final String AGENT_ID = "financial-advisor-agent";

    private FinancialAdvisorAgentConfiguration() {
    }

    public static final class FinancialAdvisorAgentHandler extends AbstractFinancialAgentHandler {

        private static final GuardrailBackend SCREEN = new KeywordScreeningBackend();

        private static final String SYSTEM_PROMPT = """
                You are a bank's financial advisor assistant. Answer customer questions about
                banking products, account concepts, and general financial guidance clearly and
                accurately. You provide information only — you never execute transfers, payments,
                or any account-modifying action. If a customer asks you to move money, explain
                that such actions require a separate, authorized channel. Do not give regulated
                investment, tax, or legal advice; suggest the customer consult a licensed
                professional for those. Never invent account balances, rates, or figures — only
                state numbers returned by an authorized backend tool.
                """;

        public FinancialAdvisorAgentHandler(ModelConnection model) {
            super(AGENT_ID, model);
        }

        @Override
        protected String description() {
            return "Read-only bank financial advisor assistant.";
        }

        @Override
        protected String systemPrompt() {
            return SYSTEM_PROMPT;
        }

        @Override
        protected List<AgentRail> complianceRails(AgentExecutionContext context) {
            return List.of(new ComplianceRail(
                    SCREEN, RiskLevel.HIGH, context.lastUserText(), context.getScope().tenantId()));
        }
    }
}

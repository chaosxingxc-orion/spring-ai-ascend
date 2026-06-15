package com.bank.financial.agent;

import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.bank.financial.kit.ModelConnection;
import com.bank.financial.kit.compliance.ComplianceRail;
import com.bank.financial.kit.compliance.KeywordScreeningBackend;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.openjiuwen.core.security.guardrail.GuardrailBackend;
import com.openjiuwen.core.security.guardrail.RiskLevel;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * First agent — a read-only financial advisor — built on the workspace kit
 * ({@link AbstractFinancialAgentHandler}). It demonstrates the minimal shape:
 * supply a prompt + description, and (optionally) attach a compliance screen.
 * It moves no money, so it needs no approval rail.
 *
 * <p>Compare with the boilerplate this used to require — the kit now owns the
 * card/model/param wiring and the platform rail seam.
 */
@Configuration(proxyBeanMethods = false)
public class FinancialAdvisorAgentConfiguration {

    static final String AGENT_ID = "financial-advisor-agent";

    @Bean
    OpenJiuwenAgentRuntimeHandler financialAdvisorAgentHandler(
            @Value("${financial.llm.model-provider:${BANK_LLM_PROVIDER:openai}}") String modelProvider,
            @Value("${financial.llm.api-key:${BANK_LLM_API_KEY:sk-local-placeholder}}") String apiKey,
            @Value("${financial.llm.api-base:${BANK_LLM_API_BASE:http://localhost:4000/v1}}") String apiBase,
            @Value("${financial.llm.model-name:${BANK_LLM_MODEL:gpt-5.4-mini}}") String modelName,
            @Value("${financial.llm.ssl-verify:${BANK_LLM_SSL_VERIFY:true}}") boolean sslVerify) {
        ModelConnection model = new ModelConnection(modelProvider, apiKey, apiBase, modelName, sslVerify);
        return new FinancialAdvisorAgentHandler(model);
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

        // Demonstrates the compliance asset: screen the user's input, block at
        // HIGH+ risk, fail-closed. A real agent swaps KeywordScreeningBackend
        // for a proper AML/suitability backend.
        @Override
        protected List<AgentRail> complianceRails(AgentExecutionContext context) {
            return List.of(new ComplianceRail(
                    SCREEN, RiskLevel.HIGH, context.lastUserText(), context.getScope().tenantId()));
        }
    }
}

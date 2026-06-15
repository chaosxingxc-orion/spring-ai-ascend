package com.bank.financial.kit.spec;

import com.openjiuwen.core.security.guardrail.RiskLevel;
import java.util.List;
import java.util.Map;

/**
 * A financial agent defined in YAML — the no-Java surface a business developer
 * writes. Parsed by {@link AgentDefinitionLoader}, turned into a running agent
 * by {@link DeclarativeAgentFactory}.
 *
 * <p>See {@code financial/agents/*.yaml} for the file shape.
 */
public record AgentDefinition(
        String id,
        String description,
        String prompt,
        ModelSpec model,
        RiskLevel complianceLevel,
        int maxIterations,
        List<ToolDef> tools,
        List<ApprovalRule> approvals) {

    /** LLM connection. {@code apiKey} usually comes from a {@code ${ENV}} placeholder. */
    public record ModelSpec(
            String provider, String apiKey, String apiBase, String modelName, boolean sslVerify) {
    }

    /**
     * A backend HTTP call exposed to the agent as a tool. {@code inputParams} is a
     * JSON-Schema-shaped map (so the LLM knows the arguments); {@code {placeholders}}
     * in {@code url} are filled from the tool inputs.
     */
    public record ToolDef(
            String name,
            String description,
            String method,
            String url,
            Map<String, String> headers,
            Map<String, Object> inputParams) {
    }

    /**
     * A human-approval rule: pause before {@code tools} run. If {@code amountOver}
     * is set, only pause when the numeric argument named {@code amountField}
     * exceeds it; otherwise always pause.
     */
    public record ApprovalRule(
            List<String> tools, String message, Double amountOver, String amountField) {
    }
}

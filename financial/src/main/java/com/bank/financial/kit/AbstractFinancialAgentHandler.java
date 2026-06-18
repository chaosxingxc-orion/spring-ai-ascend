package com.bank.financial.kit;

import com.bank.financial.kit.obs.ObservabilityRail;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for every financial agent in this workspace. It removes the
 * boilerplate of building an openJiuwen {@link ReActAgent} and standardises the
 * cross-cutting seams a bank agent must have:
 *
 * <ul>
 *   <li><b>One way to declare the brain</b> — a concrete agent supplies a
 *       {@code systemPrompt()} and {@code description()}; everything else
 *       (card, model client, sensible model params) is assembled here.</li>
 *   <li><b>Compliance seam</b> — override {@link #complianceRails} to attach
 *       AML / suitability / content guardrails (see
 *       {@code com.bank.financial.kit.compliance}).</li>
 *   <li><b>Human-approval seam</b> — override {@link #approvalRails} to require
 *       human sign-off before sensitive/irreversible tool calls (see
 *       {@code com.bank.financial.kit.approval}).</li>
 * </ul>
 *
 * <p>Both seams are wired into the platform through the runtime's official
 * {@link #openJiuwenRails} extension point — we never touch the platform.
 *
 * <p>Principle reminder: numbers (balances, rates, amounts) must come from
 * backend tools, never from the model. Attach those tools per agent; never let
 * the prompt invent figures.
 */
public abstract class AbstractFinancialAgentHandler extends OpenJiuwenAgentRuntimeHandler {

    private final ModelConnection model;

    protected AbstractFinancialAgentHandler(String agentId, ModelConnection model) {
        super(agentId);
        this.model = model;
    }

    // ── What every concrete financial agent MUST define ──────────────────────

    /** Short human description used for the A2A agent card. */
    protected abstract String description();

    /** The agent's system prompt — its role, boundaries, and refusal rules. */
    protected abstract String systemPrompt();

    // ── Safe defaults a concrete agent MAY override ──────────────────────────

    protected int maxIterations() {
        return 5;
    }

    /** Low temperature by default — financial answers should be deterministic. */
    protected double temperature() {
        return 0.3;
    }

    protected int maxTokens() {
        return 1024;
    }

    /**
     * Compliance / risk rails (AML, investor suitability, content policy).
     * Built per execution so they can read the live request via {@code context}
     * (e.g. {@code context.lastUserText()}, {@code context.getScope().tenantId()}).
     */
    protected List<AgentRail> complianceRails(AgentExecutionContext context) {
        return List.of();
    }

    /** Human-approval rails for sensitive/irreversible actions. */
    protected List<AgentRail> approvalRails(AgentExecutionContext context) {
        return List.of();
    }

    /**
     * Backend tools the agent may call. Build them with
     * {@code com.bank.financial.kit.tool.HttpTool} (declarative HTTP) or any
     * openJiuwen {@link LocalFunction}. Registered automatically. This is how the
     * agent reads real numbers instead of inventing them.
     */
    protected List<LocalFunction> tools() {
        return List.of();
    }

    // ── Platform wiring (do not override) ────────────────────────────────────

    @Override
    protected final List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
        List<AgentRail> rails = new ArrayList<>();
        // Every financial agent gets observability + domain audit for free.
        rails.add(new ObservabilityRail(agentId(), context.getScope().tenantId()));
        rails.addAll(complianceRails(context));
        rails.addAll(approvalRails(context));
        return rails;
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        AgentCard card = AgentCard.builder()
                .id(agentId())
                .name(agentId())
                .description(description())
                .build();

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt())))
                .maxIterations(maxIterations())
                .build()
                .configureModelClient(
                        model.provider(), model.apiKey(), model.apiBase(),
                        model.modelName(), model.sslVerify());

        ModelRequestConfig modelConfig = config.getModelConfigObj();
        modelConfig.setTemperature(temperature());
        modelConfig.setMaxTokens(maxTokens());

        ReActAgent agent = new ReActAgent(card);
        agent.configure(config);

        for (LocalFunction tool : tools()) {
            agent.getAbilityManager().add(tool.getCard());              // metadata for the LLM
            Runner.resourceMgr().addTool(tool, agent.getCard().getId()); // instance for execution
        }
        return agent;
    }

    // ── Local run affordance (playground / tests) — no platform server needed ──

    /** This agent's id (the A2A/runtime handler id). */
    public final String id() {
        return agentId();
    }

    /** Optional usage hint the playground prints at startup (e.g. sample customer ids). */
    public String playgroundHint() {
        return null;
    }

    /** Build the fully-wired agent (prompt + model + tools) for embedded use. */
    public ReActAgent newLocalAgent() {
        return (ReActAgent) createOpenJiuwenAgent(localContext(""));
    }

    /** Compliance + approval rails for one local turn (they may read the user text). */
    public List<AgentRail> localRails(String userText, String tenantId) {
        return openJiuwenRails(localContext(userText, tenantId));
    }

    private AgentExecutionContext localContext(String userText) {
        return localContext(userText, "playground-tenant");
    }

    private AgentExecutionContext localContext(String userText, String tenantId) {
        String tenant = tenantId == null || tenantId.isBlank() ? "playground-tenant" : tenantId;
        RuntimeIdentity scope = new RuntimeIdentity(
                tenant, "playground-user", "playground-session", "playground-task", agentId());
        return new AgentExecutionContext(
                scope, "USER_MESSAGE",
                List.of(RuntimeMessage.user(userText == null ? "" : userText)), Map.of());
    }
}

package com.bank.financial.kit.obs;

import com.bank.financial.kit.audit.FinancialAudit;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import io.micrometer.core.instrument.Metrics;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Auto-attached to every financial agent (by {@code AbstractFinancialAgentHandler})
 * so observability and domain audit come for free — no per-agent wiring.
 *
 * <p>It records structured {@link FinancialAudit} events on the run lifecycle:
 * each tool call (which tool, ok/error), model/tool errors, and the turn outcome
 * (answer / blocked / interrupt / error) with duration. Every event carries the
 * correlation id, so ops can grep one customer session by trace id and examiners
 * get a decision trail. This is the production counterpart to the dev playground
 * trace (which only prints locally).
 */
public final class ObservabilityRail extends AgentRail {

    private final String agentId;
    private final String tenantId;
    private long startNanos;

    public ObservabilityRail(String agentId, String tenantId) {
        setPriority(0); // outermost: first before, last after
        this.agentId = agentId;
        this.tenantId = tenantId;
    }

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        startNanos = System.nanoTime();
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (ctx.getInputs() instanceof ToolCallInputs in) {
            boolean ok = in.getToolResult() != null && !isError(in.getToolResult());
            FinancialAudit.event(tenantId, agentId, "tool.call",
                    field("tool", in.getToolName(), "ok", ok));
            // metric: tool calls by tool + status (low cardinality; no tenant tag)
            Metrics.counter("financial.agent.tool.calls",
                    "agent", agentId, "tool", nz(in.getToolName()), "status", ok ? "ok" : "error")
                    .increment();
        }
    }

    @Override
    public void onToolException(AgentCallbackContext ctx) {
        String tool = ctx.getInputs() instanceof ToolCallInputs in ? in.getToolName() : "?";
        FinancialAudit.event(tenantId, agentId, "tool.error", field("tool", tool));
        Metrics.counter("financial.agent.errors", "agent", agentId, "type", "tool").increment();
    }

    @Override
    public void onModelException(AgentCallbackContext ctx) {
        FinancialAudit.event(tenantId, agentId, "model.error", null);
        Metrics.counter("financial.agent.errors", "agent", agentId, "type", "model").increment();
    }

    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        String outcome = "unknown";
        if (ctx.getInputs() instanceof InvokeInputs in && in.getResult() != null) {
            Object rt = in.getResult().get("result_type");
            outcome = rt == null ? "unknown" : String.valueOf(rt);
        }
        long ms = (System.nanoTime() - startNanos) / 1_000_000L;
        FinancialAudit.event(tenantId, agentId, "turn.completed",
                field("outcome", outcome, "durationMs", ms));
        // metrics: turn count by outcome + latency timer (tagged by agent, not tenant)
        Metrics.counter("financial.agent.turns", "agent", agentId, "outcome", outcome).increment();
        Metrics.timer("financial.agent.turn.latency", "agent", agentId).record(Duration.ofMillis(ms));
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "unknown" : s;
    }

    private static boolean isError(Object result) {
        return result instanceof Map<?, ?> m && m.containsKey("error");
    }

    private static Map<String, Object> field(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}

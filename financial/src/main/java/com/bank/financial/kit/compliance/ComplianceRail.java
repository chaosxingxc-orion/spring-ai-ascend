package com.bank.financial.kit.compliance;

import com.openjiuwen.core.security.guardrail.GuardrailBackend;
import com.openjiuwen.core.security.guardrail.RiskAssessment;
import com.openjiuwen.core.security.guardrail.RiskLevel;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Input-compliance guardrail, expressed as an {@link AgentRail} so it is scoped
 * to one agent (no global event plumbing). Before the first model call it runs a
 * {@link GuardrailBackend} (your AML / suitability / content screen) over the
 * user's text; if the assessed risk is at or above {@code blockAtOrAbove}, it
 * short-circuits the turn with a refusal instead of letting the LLM proceed.
 *
 * <p>Why a rail + {@code requestForceFinish} rather than the framework's
 * {@code GuardrailError} throw: a returned refusal is a clean, user-facing
 * answer (no 500), and staying per-agent avoids the global {@code trigger(...)}
 * wiring the core guardrail requires.
 *
 * <p>Two deliberate financial defaults:
 * <ul>
 *   <li><b>Severity threshold</b> — the core guardrail blocks on ANY risk; here
 *       you choose the floor (default {@link RiskLevel#HIGH}). Lower levels pass
 *       (log/observe in your backend).</li>
 *   <li><b>Fail-closed</b> — if the screen itself errors, the turn is blocked by
 *       default (safer for a bank than failing open).</li>
 * </ul>
 */
public class ComplianceRail extends AgentRail {

    private static final String SCREENED = "fin.kit.compliance.screened";
    private static final String DEFAULT_REFUSAL =
            "抱歉,您的请求触发了合规风控策略,无法继续办理。如有疑问请联系客服或前往网点。";

    private final GuardrailBackend backend;
    private final RiskLevel blockAtOrAbove;
    private final String userText;
    private final String tenantId;
    private final boolean failClosed;
    private final String refusalMessage;

    public ComplianceRail(
            GuardrailBackend backend,
            RiskLevel blockAtOrAbove,
            String userText,
            String tenantId,
            boolean failClosed,
            String refusalMessage) {
        this.backend = backend;
        this.blockAtOrAbove = blockAtOrAbove == null ? RiskLevel.HIGH : blockAtOrAbove;
        this.userText = userText == null ? "" : userText;
        this.tenantId = tenantId;
        this.failClosed = failClosed;
        this.refusalMessage = refusalMessage == null ? DEFAULT_REFUSAL : refusalMessage;
        setPriority(10); // run before approval/other rails
    }

    /** Convenience: fail-closed, default refusal message. */
    public ComplianceRail(GuardrailBackend backend, RiskLevel blockAtOrAbove, String userText, String tenantId) {
        this(backend, blockAtOrAbove, userText, tenantId, true, null);
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (Boolean.TRUE.equals(ctx.getExtra().get(SCREENED))) {
            return; // screen the initial input once, not every ReAct iteration
        }
        ctx.getExtra().put(SCREENED, Boolean.TRUE);
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("text", userText);
            if (tenantId != null) {
                data.put("tenant", tenantId);
            }
            RiskAssessment a = backend.analyze(data);
            if (a != null && a.isHasRisk() && a.getRiskLevel().ordinal() >= blockAtOrAbove.ordinal()) {
                ctx.requestForceFinish(refusal(a.getRiskType(), a.getRiskLevel().getValue()));
            }
        } catch (Exception e) {
            if (failClosed) {
                ctx.requestForceFinish(refusal("screening_error", "unknown"));
            }
        }
    }

    private Map<String, Object> refusal(String riskType, String riskLevel) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("output", refusalMessage);
        r.put("result_type", "blocked");
        if (riskType != null) {
            r.put("risk_type", riskType);
        }
        if (riskLevel != null) {
            r.put("risk_level", riskLevel);
        }
        return r;
    }
}

package com.bank.financial.kit.audit;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Domain audit trail — structured, correlatable events for ops & examiners.
 *
 * <p>Every event carries a correlation id (traceId/runId from MDC, set by the
 * platform per request) so a developer can reconstruct one customer session by
 * grepping the id, and an auditor can answer "who, on which account, did what,
 * with what decision". Routed to the dedicated {@code financial.audit} logger —
 * point that at your audit sink in production. Pass masked / non-PII detail only.
 *
 * <p>Most events are emitted automatically by {@code ObservabilityRail}; call
 * {@link #record} directly for extra domain checkpoints.
 */
public final class FinancialAudit {

    private static final Logger LOG = LoggerFactory.getLogger("financial.audit");

    private FinancialAudit() {
    }

    /** Structured event: {@code 🧾 fin-audit trace=.. tenant=.. agent=.. action=.. k=v ...}. */
    public static void event(String tenantId, String agentId, String action, Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("🧾 fin-audit")
                .append(" trace=").append(trace())
                .append(" tenant=").append(nz(tenantId))
                .append(" agent=").append(nz(agentId))
                .append(" action=").append(action);
        if (fields != null) {
            fields.forEach((k, v) -> sb.append(' ').append(k).append('=').append(v));
        }
        LOG.info(sb.toString());
    }

    /** Convenience: a single masked detail string. */
    public static void record(String tenantId, String agentId, String action, String maskedDetail) {
        event(tenantId, agentId, action, Map.of("detail", maskedDetail));
    }

    /** Correlation id set by the platform on each request (falls back gracefully). */
    private static String trace() {
        String t = MDC.get("traceId");
        if (t == null) {
            t = MDC.get("runId");
        }
        return t == null ? "n/a" : t;
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "n/a" : s;
    }
}

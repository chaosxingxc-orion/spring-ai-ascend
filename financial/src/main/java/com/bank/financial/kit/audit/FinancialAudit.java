package com.bank.financial.kit.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal business-level audit trail.
 *
 * <p>The runtime already records a per-turn trajectory and OTel spans (technical
 * audit). Use this for explicit DOMAIN audit events — who, on which account, did
 * what, with what decision — which examiners care about. Always pass masked /
 * non-sensitive detail (account tails, amounts, decision codes), never full PII.
 */
public final class FinancialAudit {

    private static final Logger LOG = LoggerFactory.getLogger("financial.audit");

    private FinancialAudit() {
    }

    /**
     * @param tenantId     resolved tenant (from {@code context.getScope().tenantId()})
     * @param agentId      the agent id
     * @param action       a stable action code, e.g. {@code "transfer.requested"}
     * @param maskedDetail human-readable, masked detail (no raw PII)
     */
    public static void record(String tenantId, String agentId, String action, String maskedDetail) {
        LOG.info("[FIN-AUDIT] tenant={} agent={} action={} detail={}", tenantId, agentId, action, maskedDetail);
    }
}

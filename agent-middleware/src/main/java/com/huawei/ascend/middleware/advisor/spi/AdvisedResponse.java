package com.huawei.ascend.middleware.advisor.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Tenant-scoped advised response envelope returned along the
 * {@link ChatAdvisor} chain.
 *
 * <p>Authority: ADR-0132, schema at
 * {@code docs/contracts/chat-advisor.v1.yaml}.
 *
 * @param tenantId        owning tenant (Rule R-C.c); MUST be non-blank.
 * @param responseEnvelope provider-neutral response payload returned by
 *                         the terminal gateway binding or synthesised
 *                         by a short-circuiting advisor. Never null.
 * @param advisorContext  cross-advisor scratch map propagated back up
 *                        the chain (e.g. citation annotations, cost
 *                        attribution totals, redaction reports). The
 *                        map is mutable across chain calls but
 *                        immutable per envelope: advisors propagate
 *                        state by constructing a new
 *                        {@code AdvisedResponse}. Never null, may be
 *                        empty.
 */
public record AdvisedResponse(
        String tenantId,
        Map<String, Object> responseEnvelope,
        Map<String, Object> advisorContext) {

    public AdvisedResponse {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(responseEnvelope, "responseEnvelope");
        Objects.requireNonNull(advisorContext, "advisorContext");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank (Rule R-C.c)");
        }
        responseEnvelope = Map.copyOf(responseEnvelope);
        advisorContext = Map.copyOf(advisorContext);
    }
}

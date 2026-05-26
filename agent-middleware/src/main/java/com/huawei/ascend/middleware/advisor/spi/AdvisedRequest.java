package com.huawei.ascend.middleware.advisor.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Tenant-scoped advised request envelope passed along the
 * {@link ChatAdvisor} chain.
 *
 * <p>Authority: ADR-0132, schema at
 * {@code docs/contracts/chat-advisor.v1.yaml}.
 *
 * @param tenantId        owning tenant (Rule R-C.c); MUST be non-blank.
 * @param requestEnvelope provider-neutral request payload that the
 *                        terminal model gateway binding will receive.
 *                        Never null.
 * @param advisorContext  cross-advisor scratch map (e.g. PII
 *                        annotations, retrieval-augmentation traces,
 *                        cache lookup keys). The map is mutable across
 *                        chain calls but immutable per envelope:
 *                        advisors propagate state by constructing a new
 *                        {@code AdvisedRequest} for the next chain step.
 *                        Never null, may be empty.
 */
public record AdvisedRequest(
        String tenantId,
        Map<String, Object> requestEnvelope,
        Map<String, Object> advisorContext) {

    public AdvisedRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(requestEnvelope, "requestEnvelope");
        Objects.requireNonNull(advisorContext, "advisorContext");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank (Rule R-C.c)");
        }
        requestEnvelope = Map.copyOf(requestEnvelope);
        advisorContext = Map.copyOf(advisorContext);
    }
}

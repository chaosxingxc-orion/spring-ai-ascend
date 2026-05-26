package com.huawei.ascend.middleware.advisor.spi;

/**
 * Around-call interceptor over model invocation.
 *
 * <p>Authority: ADR-0132. The chain runs inside the
 * {@code HookDispatcher.fire(BEFORE_LLM)}/{@code AFTER_LLM} brackets
 * at runtime (ADR-0073); customers compose advisors at agent
 * definition time and never import {@code HookDispatcher} directly.
 *
 * <p>Implementations:
 * <ul>
 *   <li>MUST return a stable, non-blank {@link #advisorName()} for
 *       registry lookup, hook attribution, and audit.</li>
 *   <li>MUST be thread-safe; the runtime invokes from virtual
 *       threads.</li>
 *   <li>MUST call {@link AdvisorChain#next(AdvisedRequest)} exactly
 *       once unless they short-circuit by returning a synthetic
 *       {@link AdvisedResponse} (PII-filter rejection, cache hit,
 *       policy denial).</li>
 * </ul>
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-module middleware SPI siblings.
 */
public interface ChatAdvisor {

    /** Stable advisor identifier (e.g. {@code "pii-redaction-advisor"}); non-blank. */
    String advisorName();

    /**
     * Ordinal sort key — advisors with smaller {@code order()} run first
     * on the inbound (around-request) leg and last on the outbound
     * (around-response) leg. Convention mirrors Spring's
     * {@code Ordered.LOWEST_PRECEDENCE = Integer.MAX_VALUE}.
     */
    int order();

    /**
     * Intercept a model call. Implementations MUST call
     * {@link AdvisorChain#next(AdvisedRequest)} exactly once, unless
     * they short-circuit the call by returning a synthetic
     * {@link AdvisedResponse} (e.g. PII-filter rejection or cache hit).
     *
     * @param request the advised request envelope; never null.
     * @param chain   the advisor chain; never null.
     * @return the advised response; never null.
     */
    AdvisedResponse aroundCall(AdvisedRequest request, AdvisorChain chain);
}

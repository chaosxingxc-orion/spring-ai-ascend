package com.huawei.ascend.middleware.advisor.spi;

/**
 * Continuation handle for a {@link ChatAdvisor} chain step.
 *
 * <p>Authority: ADR-0132. The chain is owned by the runtime; advisors
 * receive a fresh {@code AdvisorChain} per call and MUST NOT cache
 * one across invocations.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-module middleware SPI siblings.
 */
public interface AdvisorChain {

    /**
     * Continue advisor chain execution. Each call advances exactly
     * one chain step. Calling {@code next} after the chain has been
     * fully consumed delegates to the terminal {@code ModelGateway.invoke}
     * call.
     *
     * @param request the (possibly mutated) advised request to pass
     *                downstream; never null.
     * @return the advised response from downstream; never null.
     */
    AdvisedResponse next(AdvisedRequest request);
}

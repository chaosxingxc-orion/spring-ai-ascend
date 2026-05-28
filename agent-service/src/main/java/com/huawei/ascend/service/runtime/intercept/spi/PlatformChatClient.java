package com.huawei.ascend.service.runtime.intercept.spi;

import java.util.concurrent.Flow;

/**
 * Model-call interception entry point used by in-process Agents.
 *
 * <p>The Agent constructs its own messages list and invokes this SPI
 * with the constructed messages and a model reference. M6 applies
 * TTI-02 boundary treatment (policy, redaction, token-budget audit,
 * fallback trim), routes to the vendor adapter via TTI-10, and
 * returns a normalised ContentBlock stream.</p>
 *
 * <p>Authority: ADR-0155. Status: design_only.</p>
 */
public interface PlatformChatClient {

    /**
     * Invoke a model with Agent-constructed messages.
     *
     * @param messagesRef reference to the Agent's constructed messages
     *                    (schema: {@code docs/contracts/governed-messages.v1.yaml}
     *                    after TTI-02 treatment; pre-treatment shape lives
     *                    inside the contract).
     * @param modelRef    canonical model identifier resolved via TTI-10.
     * @return publisher of ContentBlock chunks for streaming models, or a
     *                    one-element publisher for non-streaming models.
     */
    Flow.Publisher<Object> invoke(Object messagesRef, String modelRef);
}

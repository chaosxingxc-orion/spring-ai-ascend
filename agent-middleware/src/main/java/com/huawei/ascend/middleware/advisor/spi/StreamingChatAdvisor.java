package com.huawei.ascend.middleware.advisor.spi;

import java.util.stream.Stream;

/**
 * Streaming sibling of {@link ChatAdvisor}.
 *
 * <p>Authority: ADR-0132. This SPI composes streaming model calls
 * without importing the model SPI package.
 */
public interface StreamingChatAdvisor {

    /** Stable advisor identifier; non-blank. */
    String advisorName();

    /** Ordinal sort key; smaller values run first on the inbound leg. */
    int order();

    /**
     * Intercept a streaming model call.
     *
     * @param request the advised request envelope; never null.
     * @param chain   the streaming advisor chain; never null.
     * @return finite ordered advised stream; never null.
     */
    Stream<AdvisedStreamChunk> aroundStream(AdvisedRequest request, StreamingAdvisorChain chain);
}

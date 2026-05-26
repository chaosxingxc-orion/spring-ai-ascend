package com.huawei.ascend.middleware.advisor.spi;

import java.util.stream.Stream;

/**
 * Continuation handle for a streaming {@link StreamingChatAdvisor} step.
 *
 * <p>Authority: ADR-0132.
 */
public interface StreamingAdvisorChain {

    /**
     * Continue streaming advisor chain execution.
     *
     * @param request the advised request to pass downstream; never null.
     * @return finite ordered stream of advised chunks; never null.
     */
    Stream<AdvisedStreamChunk> proceed(AdvisedRequest request);
}

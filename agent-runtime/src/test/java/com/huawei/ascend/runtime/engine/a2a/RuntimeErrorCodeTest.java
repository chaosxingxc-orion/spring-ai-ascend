package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class RuntimeErrorCodeTest {

    @Test
    void classifiesTimeoutAsRetryable() {
        assertThat(RuntimeErrorCode.classify(new TimeoutException("slow"))).isEqualTo(RuntimeErrorCode.TIMEOUT);
        assertThat(RuntimeErrorCode.TIMEOUT.retryable()).isTrue();
    }

    @Test
    void classifiesIllegalArgumentAsNonRetryableInvalidInput() {
        assertThat(RuntimeErrorCode.classify(new IllegalArgumentException("bad")))
                .isEqualTo(RuntimeErrorCode.INVALID_INPUT);
        assertThat(RuntimeErrorCode.INVALID_INPUT.retryable()).isFalse();
    }

    @Test
    void classifiesIoFailureAsRetryableUpstreamUnavailable() {
        assertThat(RuntimeErrorCode.classify(new IOException("conn reset")))
                .isEqualTo(RuntimeErrorCode.UPSTREAM_UNAVAILABLE);
        assertThat(RuntimeErrorCode.UPSTREAM_UNAVAILABLE.retryable()).isTrue();
    }

    @Test
    void classifiesCancellation() {
        assertThat(RuntimeErrorCode.classify(new CancellationException())).isEqualTo(RuntimeErrorCode.CANCELLED);
    }

    /** Async failures wrap the real cause — the classifier must unwrap to find it. */
    @Test
    void unwrapsCauseChainToFindTimeout() {
        assertThat(RuntimeErrorCode.classify(new CompletionException(new TimeoutException())))
                .isEqualTo(RuntimeErrorCode.TIMEOUT);
    }

    @Test
    void defaultsToInternal() {
        assertThat(RuntimeErrorCode.classify(new RuntimeException("weird"))).isEqualTo(RuntimeErrorCode.INTERNAL);
    }
}

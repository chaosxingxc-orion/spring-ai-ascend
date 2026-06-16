package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.ErrorCategory;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

class TrajectoryEventTest {

    @Test
    void mandatoryKindsAreTheCrossFrameworkCore() {
        assertThat(TrajectoryEvent.MANDATORY_KINDS).containsExactlyInAnyOrder(
                Kind.RUN_START, Kind.RUN_END, Kind.TOOL_CALL_START, Kind.TOOL_CALL_END, Kind.ERROR);
    }

    @Test
    void wireContractVersionIsThree() {
        assertThat(TrajectoryEvent.SCHEMA_VERSION).isEqualTo("3");
    }

    @Test
    void draftFactoriesCarryTheRightKindAndPayload() {
        assertThat(TrajectoryDraft.runStart().kind()).isEqualTo(Kind.RUN_START);
        assertThat(TrajectoryDraft.runEnd().kind()).isEqualTo(Kind.RUN_END);
        assertThat(TrajectoryDraft.toolCallStart("search", "q").kind()).isEqualTo(Kind.TOOL_CALL_START);
        assertThat(TrajectoryDraft.toolCallStart("search", "q").name()).isEqualTo("search");
        assertThat(TrajectoryDraft.toolCallStart("search", "q").args()).isEqualTo("q");

        TrajectoryDraft end = TrajectoryDraft.modelCallEnd(
                new TrajectoryEvent.Usage(10, 20, 5.0, "m", "openai", 1500L), "stop", "thinking");
        assertThat(end.kind()).isEqualTo(Kind.MODEL_CALL_END);
        assertThat(end.usage().inputTokens()).isEqualTo(10);
        assertThat(end.usage().provider()).isEqualTo("openai");
        assertThat(end.usage().costMicros()).isEqualTo(1500L);
        assertThat(end.reasoning()).isEqualTo("thinking");
        // finish_reason has its own typed field now — it must NOT ride in the result slot.
        assertThat(end.finishReason()).isEqualTo("stop");
        assertThat(end.result()).isNull();

        TrajectoryDraft error = TrajectoryDraft.error("tool", "CODE", "boom", ErrorCategory.SERVER_ERROR, 2, true);
        assertThat(error.kind()).isEqualTo(Kind.ERROR);
        assertThat(error.error().code()).isEqualTo("CODE");
        assertThat(error.error().category()).isEqualTo(ErrorCategory.SERVER_ERROR);
        assertThat(error.attempt()).isEqualTo(2);
        assertThat(error.retryable()).isTrue();
    }

    @Test
    void errorCategoryClassifiesByCauseChain() {
        assertThat(ErrorCategory.classify(new SocketTimeoutException("read timed out")))
                .isEqualTo(ErrorCategory.TIMEOUT);
        // Walks the cause chain: neutral outer message, classifiable cause.
        assertThat(ErrorCategory.classify(
                new RuntimeException("wrapper", new IllegalStateException("Connection refused"))))
                .isEqualTo(ErrorCategory.NETWORK);
        assertThat(ErrorCategory.classify(new RuntimeException("HTTP 429 Too Many Requests")))
                .isEqualTo(ErrorCategory.RATE_LIMIT);
        assertThat(ErrorCategory.classify(new RuntimeException("403 Forbidden")))
                .isEqualTo(ErrorCategory.AUTH);
        assertThat(ErrorCategory.classify(new IllegalArgumentException("bad")))
                .isEqualTo(ErrorCategory.INVALID_REQUEST);
        assertThat(ErrorCategory.classify(null)).isEqualTo(ErrorCategory.UNKNOWN);
        assertThat(ErrorCategory.classify(new RuntimeException("totally opaque")))
                .isEqualTo(ErrorCategory.UNKNOWN);
    }
}

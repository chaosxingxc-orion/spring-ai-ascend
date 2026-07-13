package com.huawei.ascend.bus.forwarding.spi;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the six gateway-side observed invocation statuses of
 * {@link InvocationResponseStatus} (FEAT-013 §2.3.3, shared by FEAT-014 for
 * the remote-Task viewpoint).
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §2.3.3};
 * {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-014-a2a-call-event-forwarding.md §2.3.2}.
 */
class InvocationResponseStatusTest {

    @Test
    void has_six_observed_statuses() {
        assertThat(EnumSet.allOf(InvocationResponseStatus.class)).containsExactlyInAnyOrder(
                InvocationResponseStatus.COMPLETED_RESPONSE,
                InvocationResponseStatus.ACCEPTED_WITH_TASK,
                InvocationResponseStatus.STREAM_READY,
                InvocationResponseStatus.REJECTED,
                InvocationResponseStatus.FAILED,
                InvocationResponseStatus.UNKNOWN);
    }

    @Test
    void value_of_rejects_an_unknown_status() {
        assertThatThrownBy(() -> InvocationResponseStatus.valueOf("NOT_A_REAL_STATUS"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

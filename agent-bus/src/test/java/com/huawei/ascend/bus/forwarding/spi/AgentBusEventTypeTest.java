package com.huawei.ascend.bus.forwarding.spi;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the FEAT-013 + FEAT-014 event-family membership of
 * {@link AgentBusEventType} so a later edit cannot silently drop or rename a
 * discriminator the gateway / event-bus / agent-runtime wire to.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §2.3.2};
 * {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-014-a2a-call-event-forwarding.md §2.3.1}.
 */
class AgentBusEventTypeTest {

    @Test
    void has_all_20_event_types_across_feat_013_and_feat_014_families() {
        assertThat(EnumSet.allOf(AgentBusEventType.class)).containsExactlyInAnyOrder(
                // FEAT-013: client -> server (gateway -> event-bus -> agent-runtime)
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED,
                AgentBusEventType.CLIENT_INVOCATION_CANCEL_REQUESTED,
                AgentBusEventType.CLIENT_INVOCATION_QUERY_REQUESTED,
                AgentBusEventType.CLIENT_STREAM_SUBSCRIBE_REQUESTED,
                // FEAT-013: server -> gateway (agent-runtime -> event-bus -> gateway)
                AgentBusEventType.INVOCATION_ACCEPTED,
                AgentBusEventType.INVOCATION_REJECTED,
                AgentBusEventType.INVOCATION_FAILED,
                AgentBusEventType.INVOCATION_RESPONSE,
                AgentBusEventType.INVOCATION_STREAM_READY,
                AgentBusEventType.INVOCATION_TERMINAL,
                // FEAT-014: source runtime -> target runtime (caller -> event-bus -> target)
                AgentBusEventType.A2A_CALL_REQUESTED,
                AgentBusEventType.A2A_CALL_CANCEL_REQUESTED,
                AgentBusEventType.A2A_CALL_QUERY_REQUESTED,
                AgentBusEventType.A2A_STREAM_SUBSCRIBE_REQUESTED,
                // FEAT-014: target runtime -> source runtime (target -> event-bus -> caller)
                AgentBusEventType.A2A_CALL_ACCEPTED,
                AgentBusEventType.A2A_CALL_REJECTED,
                AgentBusEventType.A2A_CALL_FAILED,
                AgentBusEventType.A2A_CALL_RESPONSE,
                AgentBusEventType.A2A_STREAM_READY,
                AgentBusEventType.A2A_CALL_TERMINAL);
    }

    @Test
    void value_of_rejects_an_unknown_event_type() {
        assertThatThrownBy(() -> AgentBusEventType.valueOf("NOT_A_REAL_EVENT_TYPE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

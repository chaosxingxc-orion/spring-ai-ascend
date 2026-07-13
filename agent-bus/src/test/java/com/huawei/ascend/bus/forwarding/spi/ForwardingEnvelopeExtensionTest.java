package com.huawei.ascend.bus.forwarding.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract test for the FEAT-013+014 additive extension of
 * {@link ForwardingEnvelope}: the new {@code eventType} /
 * {@code sourceServiceId} / {@code targetServiceId} components, their
 * compact-constructor validation, and the preserved tenant + payloadRef
 * invariants.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §2.3.1}.
 */
class ForwardingEnvelopeExtensionTest {

    private static final String TENANT = "tenant-a";

    /** Minimal valid envelope (CONTROL_ONLY, no payloadRef). */
    private static ForwardingEnvelope envelope(AgentBusEventType eventType,
                                               String sourceServiceId,
                                               String targetServiceId) {
        return new ForwardingEnvelope(
                new ForwardingMessageId("msg-1"),
                eventType,
                TENANT,
                "trace-1",
                "corr-1",
                "idem-1",
                new ForwardingRouteHandle("route-1", TENANT),
                "cap-1",
                sourceServiceId,
                targetServiceId,
                Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY,
                null);
    }

    @Test
    void exposes_the_three_additive_fields() {
        ForwardingEnvelope e = envelope(
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "src-1", "tgt-1");
        assertThat(e.eventType()).isEqualTo(AgentBusEventType.CLIENT_INVOCATION_REQUESTED);
        assertThat(e.sourceServiceId()).isEqualTo("src-1");
        assertThat(e.targetServiceId()).isEqualTo("tgt-1");
    }

    @Test
    void compact_constructor_rejects_null_event_type() {
        assertThatThrownBy(() -> envelope(null, "src-1", "tgt-1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void compact_constructor_rejects_null_source_service_id() {
        assertThatThrownBy(() -> envelope(
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED, null, "tgt-1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sourceServiceId");
    }

    @Test
    void compact_constructor_rejects_blank_source_service_id() {
        assertThatThrownBy(() -> envelope(
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "  ", "tgt-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceServiceId");
    }

    @Test
    void compact_constructor_rejects_null_target_service_id() {
        assertThatThrownBy(() -> envelope(
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "src-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("targetServiceId");
    }

    @Test
    void compact_constructor_rejects_blank_target_service_id() {
        assertThatThrownBy(() -> envelope(
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "src-1", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetServiceId");
    }

    @Test
    void tenant_invariant_is_preserved_tenant_mismatch_rejected() {
        // routeHandle tenantScope "tenant-other" != envelope tenantId "tenant-a"
        assertThatThrownBy(() -> new ForwardingEnvelope(
                new ForwardingMessageId("msg-tm"),
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED,
                TENANT,
                "trace-tm", "corr-tm", "idem-tm",
                new ForwardingRouteHandle("route-1", "tenant-other"),
                "cap-tm", "src-tm", "tgt-tm", Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant_mismatch");
    }

    @Test
    void payload_ref_conditional_invariant_is_preserved() {
        // DATA_BEARING with a null payloadRef is still rejected
        assertThatThrownBy(() -> new ForwardingEnvelope(
                new ForwardingMessageId("msg-db"),
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED,
                TENANT, "trace-db", "corr-db", "idem-db",
                new ForwardingRouteHandle("route-1", TENANT),
                "cap-db", "src-db", "tgt-db", Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.DATA_BEARING, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payloadRef");
    }

    @Test
    void data_bearing_envelope_with_payload_ref_constructs() {
        ForwardingEnvelope e = new ForwardingEnvelope(
                new ForwardingMessageId("msg-db2"),
                AgentBusEventType.A2A_CALL_REQUESTED,
                TENANT, "trace-db2", "corr-db2", "idem-db2",
                new ForwardingRouteHandle("route-1", TENANT),
                "cap-db2", "src-db2", "tgt-db2", Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.DATA_BEARING, "ref://payload/123");
        assertThat(e.carriesPayloadRef()).isTrue();
        assertThat(e.eventType()).isEqualTo(AgentBusEventType.A2A_CALL_REQUESTED);
        assertThat(e.sourceServiceId()).isEqualTo("src-db2");
        assertThat(e.targetServiceId()).isEqualTo("tgt-db2");
    }
}

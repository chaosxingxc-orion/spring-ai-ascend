package com.huawei.ascend.bus.spi.ingress;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Library-mode SPI conformance test for {@link IngressEnvelope}.
 *
 * <p>Stage 1 harness (Slice 2): proves the ingress envelope's compact-constructor
 * contract — six mandatory fields, non-blank tenant scope, and the W3C 32-char
 * lowercase-hex traceId rule. Schema authority:
 * {@code docs/contracts/ingress-envelope.v1.yaml#request}; CLAUDE.md Rule R-C
 * sub-clause .c (Contract Spine Completeness) + Rule R-I sub-clause .b.
 *
 * <p>Pure JUnit Jupiter — no Spring context, no transport. Mirrors the style of
 * {@code S2cCallbackEnvelopeLibraryTest}. Stage 1 does NOT modify production
 * code; this test only locks the constructor's existing invariants.
 *
 * <p>Assertion ID: HA-002.
 */
class IngressEnvelopeTest {

    private static final String VALID_TRACE_ID = "0123456789abcdef0123456789abcdef";  // 32 lowercase hex

    // ---- positive path -----------------------------------------------------

    @Test
    void envelope_constructor_accepts_minimal_valid_input() {
        UUID requestId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        IngressEnvelope env = new IngressEnvelope(
                requestId, "tenant-acme", idempotencyKey,
                IngressEnvelope.IngressRequestType.RUN_CREATE, "payload", VALID_TRACE_ID,
                null, null);

        assertThat(env.requestId()).isEqualTo(requestId);
        assertThat(env.tenantId()).isEqualTo("tenant-acme");
        assertThat(env.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(env.requestType()).isEqualTo(IngressEnvelope.IngressRequestType.RUN_CREATE);
        assertThat(env.payload()).isEqualTo("payload");
        assertThat(env.traceId()).isEqualTo(VALID_TRACE_ID);
        assertThat(env.deadlineMillisEpoch()).isNull();
        // null requestAttributes is normalised to an empty immutable Map.
        assertThat(env.requestAttributes()).isEmpty();
    }

    // ---- required-field negative path -------------------------------------

    @Test
    void envelope_constructor_rejects_null_request_id() {
        assertThatThrownBy(() -> validEnvelope()
                .requestId(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestId is required");
    }

    @Test
    void envelope_constructor_rejects_null_tenant_id() {
        assertThatThrownBy(() -> validEnvelope()
                .tenantId(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId is required");
    }

    @Test
    void envelope_constructor_rejects_blank_tenant_id() {
        assertThatThrownBy(() -> validEnvelope()
                .tenantId("   ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must not be blank");
    }

    @Test
    void envelope_constructor_rejects_null_idempotency_key() {
        assertThatThrownBy(() -> validEnvelope()
                .idempotencyKey(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idempotencyKey is required");
    }

    @Test
    void envelope_constructor_rejects_null_request_type() {
        assertThatThrownBy(() -> validEnvelope()
                .requestType(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestType is required");
    }

    @Test
    void envelope_constructor_rejects_null_payload() {
        assertThatThrownBy(() -> validEnvelope()
                .payload(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payload is required");
    }

    // ---- traceId format ----------------------------------------------------

    @Test
    void envelope_constructor_rejects_null_trace_id() {
        assertThatThrownBy(() -> validEnvelope()
                .traceId(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("traceId is required");
    }

    @Test
    void envelope_constructor_rejects_trace_id_wrong_length() {
        assertThatThrownBy(() -> validEnvelope()
                .traceId("abc123").build())  // only 6 chars
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    void envelope_constructor_rejects_trace_id_with_uppercase() {
        assertThatThrownBy(() -> validEnvelope()
                .traceId("0123456789ABCDEF0123456789ABCDEF").build())  // uppercase A-F
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase hex");
    }

    @Test
    void envelope_constructor_rejects_trace_id_with_non_hex_chars() {
        // 32 chars long but contains 'g', 'h', 'z' — non-hex even though lowercase.
        assertThatThrownBy(() -> validEnvelope()
                .traceId("0123456789abcdez0123456789abcghz").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase hex");
    }

    // ---- requestAttributes normalisation & defensive copy -----------------

    @Test
    void envelope_request_attributes_null_is_normalised_to_empty_immutable_map() {
        IngressEnvelope env = validEnvelope().requestAttributes(null).build();
        assertThat(env.requestAttributes()).isEmpty();
        // Map.of() is unmodifiable — confirms the "immutable" part of the contract.
        assertThatThrownBy(() -> env.requestAttributes().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void envelope_request_attributes_is_defensive_copy() {
        HashMap<String, Object> mutable = new HashMap<>();
        mutable.put("k1", "v1");
        IngressEnvelope env = validEnvelope().requestAttributes(mutable).build();
        // mutate the source map AFTER construction — envelope must be unaffected.
        mutable.put("k2", "v2");
        assertThat(env.requestAttributes()).containsOnlyKeys("k1");
    }

    @Test
    void envelope_request_attributes_returned_map_is_unmodifiable() {
        IngressEnvelope env = validEnvelope()
                .requestAttributes(Map.of("k1", "v1")).build();
        assertThatThrownBy(() -> env.requestAttributes().put("k2", "v2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- minimal envelope builder for negative tests -----------------------

    /**
     * Fluent holder so each negative test overrides exactly one field while the
     * rest stay at valid defaults. Keeps the intent ("only this one field is
     * invalid") obvious at the call site.
     */
    private static EnvelopeBuilder validEnvelope() {
        return new EnvelopeBuilder();
    }

    private static final class EnvelopeBuilder {
        private UUID requestId = UUID.randomUUID();
        private String tenantId = "tenant-acme";
        private UUID idempotencyKey = UUID.randomUUID();
        private IngressEnvelope.IngressRequestType requestType =
                IngressEnvelope.IngressRequestType.RUN_CREATE;
        private Object payload = "payload";
        private String traceId = VALID_TRACE_ID;
        private Long deadlineMillisEpoch = null;
        private Map<String, Object> requestAttributes = null;

        EnvelopeBuilder requestId(UUID v) { this.requestId = v; return this; }
        EnvelopeBuilder tenantId(String v) { this.tenantId = v; return this; }
        EnvelopeBuilder idempotencyKey(UUID v) { this.idempotencyKey = v; return this; }
        EnvelopeBuilder requestType(IngressEnvelope.IngressRequestType v) { this.requestType = v; return this; }
        EnvelopeBuilder payload(Object v) { this.payload = v; return this; }
        EnvelopeBuilder traceId(String v) { this.traceId = v; return this; }
        EnvelopeBuilder deadlineMillisEpoch(Long v) { this.deadlineMillisEpoch = v; return this; }
        EnvelopeBuilder requestAttributes(Map<String, Object> v) { this.requestAttributes = v; return this; }

        IngressEnvelope build() {
            return new IngressEnvelope(requestId, tenantId, idempotencyKey, requestType,
                    payload, traceId, deadlineMillisEpoch, requestAttributes);
        }
    }
}

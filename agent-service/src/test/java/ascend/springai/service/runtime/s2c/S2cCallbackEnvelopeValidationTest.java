package ascend.springai.service.runtime.s2c;

import ascend.springai.bus.spi.s2c.S2cCallbackEnvelope;
import ascend.springai.bus.spi.s2c.S2cCallbackResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates the six mandatory fields of S2cCallbackEnvelope per
 * docs/contracts/s2c-callback.v1.yaml#request + Phase 3a audit matrix §5.2.
 *
 * <p>Authority: ADR-0074.
 */
class S2cCallbackEnvelopeValidationTest {

    private static final String VALID_TRACE = "abcdef1234567890abcdef1234567890";

    @Test
    void all_six_mandatory_fields_present_constructs_ok() {
        S2cCallbackEnvelope env = new S2cCallbackEnvelope(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "client.browser.screenshot",
                "payload",
                VALID_TRACE,
                UUID.randomUUID(),
                null,
                null);
        assertThat(env.capabilityRef()).isEqualTo("client.browser.screenshot");
        assertThat(env.requestAttributes()).isEmpty();
    }

    @Test
    void null_callbackId_rejected() {
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                null, UUID.randomUUID(), "cap", "p", VALID_TRACE, UUID.randomUUID(), null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("callbackId");
    }

    @Test
    void blank_capabilityRef_rejected() {
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                UUID.randomUUID(), UUID.randomUUID(), "  ", "p", VALID_TRACE, UUID.randomUUID(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capabilityRef");
    }

    @Test
    void wrong_length_traceId_rejected() {
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                UUID.randomUUID(), UUID.randomUUID(), "cap", "p", "too-short", UUID.randomUUID(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traceId");
    }

    @Test
    void uppercase_hex_traceId_rejected() {
        String upperHex = "ABCDEF1234567890ABCDEF1234567890";
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                UUID.randomUUID(), UUID.randomUUID(), "cap", "p", upperHex, UUID.randomUUID(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase hex");
    }

    @Test
    void non_hex_traceId_rejected() {
        String nonHex = "zzzzzz1234567890abcdef1234567890";
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                UUID.randomUUID(), UUID.randomUUID(), "cap", "p", nonHex, UUID.randomUUID(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase hex");
    }

    @Test
    void uppercase_hex_clientTraceId_rejected_on_response() {
        String upperHex = "ABCDEF1234567890ABCDEF1234567890";
        assertThatThrownBy(() -> S2cCallbackResponse.ok(UUID.randomUUID(), upperHex, "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase hex");
    }

    @Test
    void requestAttributes_defaults_to_empty_immutable_map() {
        S2cCallbackEnvelope env = new S2cCallbackEnvelope(
                UUID.randomUUID(), UUID.randomUUID(), "cap", "p", VALID_TRACE, UUID.randomUUID(), null, null);
        assertThatThrownBy(() -> env.requestAttributes().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void response_callbackId_mismatch_validation_logic_is_responsibility_of_orchestrator() {
        // This test documents the contract boundary: the envelope only validates its OWN fields.
        // Cross-correlation of request.callbackId vs response.callbackId is the orchestrator's job
        // (see SyncOrchestrator.handleClientCallback). Recorded here as an executable note.
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        S2cCallbackEnvelope env = new S2cCallbackEnvelope(
                id1, UUID.randomUUID(), "cap", "p", VALID_TRACE, UUID.randomUUID(), null, null);
        S2cCallbackResponse mismatched = S2cCallbackResponse.ok(id2, VALID_TRACE, "result");
        assertThat(env.callbackId()).isNotEqualTo(mismatched.callbackId());
    }
}

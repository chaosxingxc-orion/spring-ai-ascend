package com.huawei.ascend.bus.spi.ingress;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Library-mode SPI conformance test for {@link IngressResponse}.
 *
 * <p>Stage 1 harness (Slice 3): proves the bus acknowledgement envelope's
 * constructor invariants and its three status factories
 * ({@link IngressResponse#accepted}/{@link #rejected}/{@link #deferred}).
 * Schema authority: {@code docs/contracts/ingress-envelope.v1.yaml#response};
 * CLAUDE.md Rule R-F (Cursor Flow) + Rule R-I sub-clause .b.
 *
 * <p>Pure JUnit Jupiter — no Spring context, no transport. Mirrors the style of
 * {@code S2cCallbackEnvelopeLibraryTest}. Stage 1 does NOT modify production
 * code; this test only locks the constructor's existing invariants.
 *
 * <p>Assertion ID: HA-003.
 */
class IngressResponseTest {

    // ---- required-field negative path -------------------------------------

    @Test
    void response_constructor_rejects_null_request_id() {
        assertThatThrownBy(() -> new IngressResponse(
                null, IngressResponse.IngressStatus.ACCEPTED, "cursor", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestId is required");
    }

    @Test
    void response_constructor_rejects_null_status() {
        assertThatThrownBy(() -> new IngressResponse(
                UUID.randomUUID(), null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status is required");
    }

    @Test
    void response_constructor_rejects_rejected_with_null_reason() {
        assertThatThrownBy(() -> new IngressResponse(
                UUID.randomUUID(), IngressResponse.IngressStatus.REJECTED, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rejectionReason is required when status=REJECTED");
    }

    @Test
    void response_constructor_rejects_rejected_with_blank_reason() {
        assertThatThrownBy(() -> new IngressResponse(
                UUID.randomUUID(), IngressResponse.IngressStatus.REJECTED, null, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejectionReason must not be blank when status=REJECTED");
    }

    // ---- factory methods ---------------------------------------------------

    @Test
    void accepted_factory_returns_accepted_status_with_cursor() {
        UUID requestId = UUID.randomUUID();
        IngressResponse r = IngressResponse.accepted(requestId, "cursor-run-42");
        assertThat(r.requestId()).isEqualTo(requestId);
        assertThat(r.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(r.cursor()).isEqualTo("cursor-run-42");
        assertThat(r.rejectionReason()).isNull();
    }

    @Test
    void rejected_factory_returns_rejected_status_with_reason() {
        UUID requestId = UUID.randomUUID();
        IngressResponse r = IngressResponse.rejected(requestId, "quota-exceeded");
        assertThat(r.requestId()).isEqualTo(requestId);
        assertThat(r.status()).isEqualTo(IngressResponse.IngressStatus.REJECTED);
        assertThat(r.rejectionReason()).isEqualTo("quota-exceeded");
        assertThat(r.cursor()).isNull();
    }

    @Test
    void deferred_factory_returns_deferred_status_without_cursor_or_reason() {
        UUID requestId = UUID.randomUUID();
        IngressResponse r = IngressResponse.deferred(requestId);
        assertThat(r.requestId()).isEqualTo(requestId);
        assertThat(r.status()).isEqualTo(IngressResponse.IngressStatus.DEFERRED);
        assertThat(r.cursor()).isNull();
        assertThat(r.rejectionReason()).isNull();
    }

    @Test
    void status_enum_is_closed_to_three_values() {
        assertThat(IngressResponse.IngressStatus.values())
                .containsExactly(
                        IngressResponse.IngressStatus.ACCEPTED,
                        IngressResponse.IngressStatus.REJECTED,
                        IngressResponse.IngressStatus.DEFERRED);
    }

    // ---- characterisation of current behaviour (待确认项) -------------------

    /**
     * Characterisation test — records the CURRENT behaviour that
     * {@link IngressResponse#accepted(UUID, String)} does not enforce a non-null
     * {@code cursor}. This is explicitly flagged as a 待确认项 in the Stage 1
     * delivery projection: if the architecture owner decides ACCEPTED + RUN_CREATE
     * MUST carry a cursor, that becomes a separate contract-correction slice and
     * this test will then flip to red, surfacing the change. Stage 1 records the
     * status quo rather than silently tightening the contract.
     */
    @Test
    void accepted_currently_does_not_enforce_non_null_cursor_pending_owner_decision() {
        UUID requestId = UUID.randomUUID();
        // No exception today — documented behaviour, not an endorsement.
        IngressResponse r = IngressResponse.accepted(requestId, null);
        assertThat(r.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(r.cursor()).isNull();
    }
}

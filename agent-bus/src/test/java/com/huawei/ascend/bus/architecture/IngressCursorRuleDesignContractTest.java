package com.huawei.ascend.bus.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Design-level harness for the IngressGateway cursor rules (Stage 4, slice 4).
 *
 * <p>Stage 3 decided that {@code IngressResponse} stays low-context and that the
 * {@code RUN_CREATE + ACCEPTED ⇒ cursor} rule is enforced at the gateway /
 * handler layer rather than baked into a richer response shape. Stage 4 keeps
 * that decision and pins the four cursor rules as design-level assertions, so a
 * future edit that weakens them — making cursor mandatory on non-create
 * responses, dropping the rejectionReason requirement, or silently dropping
 * DEFERRED without a follow-up observation path — fails the build.
 *
 * <p>Authority: {@code docs/contracts/ingress-envelope.v1.yaml#response}
 * (ADR-0089 / Rule R-F — Cursor Flow); {@code IngressResponse} /
 * {@code IngressEnvelope} records.
 *
 * <p>This is a design-level harness: it reads the contract YAML, the response
 * record source, and the request-type enum source as plain text and anchors on
 * the cursor-rule phrases. It does NOT exercise a gateway implementation —
 * there is none yet (the contract is {@code design_only}).
 */
class IngressCursorRuleDesignContractTest {

    private static final Path CONTRACT = Path.of("../docs/contracts/ingress-envelope.v1.yaml");
    private static final Path RESPONSE_SOURCE = Path.of(
            "src/main/java/com/huawei/ascend/bus/spi/ingress/IngressResponse.java");
    private static final Path ENVELOPE_SOURCE = Path.of(
            "src/main/java/com/huawei/ascend/bus/spi/ingress/IngressEnvelope.java");

    private static String contractYaml;
    private static String responseSource;
    private static String envelopeSource;

    @BeforeAll
    static void readSources() throws Exception {
        assertThat(CONTRACT)
                .as("ingress-envelope.v1.yaml must be reachable from the surefire working "
                  + "directory (agent-bus module basedir)")
                .exists();
        contractYaml = Files.readString(CONTRACT);

        assertThat(RESPONSE_SOURCE)
                .as("IngressResponse.java must be reachable from the module basedir")
                .exists();
        responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(ENVELOPE_SOURCE)
                .as("IngressEnvelope.java must be reachable from the module basedir")
                .exists();
        envelopeSource = Files.readString(ENVELOPE_SOURCE);
    }

    // ---- rule 1: RUN_CREATE + ACCEPTED must produce a cursor ---------------

    @Test
    void run_create_accepted_produces_cursor() {
        assertThat(contractYaml)
                .as("contract must state cursor is present iff ACCEPTED + RUN_CREATE (Rule R-F)")
                .contains("present iff (status=ACCEPTED && request_type=RUN_CREATE)");
        assertThat(responseSource)
                .as("IngressResponse.cursor field must document the same iff rule")
                .contains("present iff status == ACCEPTED")
                .contains("RUN_CREATE");
    }

    // ---- rule 2: non-create request types carry request-type-scoped cursor rules

    @Test
    void non_create_request_types_have_request_type_scoped_cursor_rule() {
        // The closed request-type enum carries RUN_GET / RUN_CANCEL / RUN_RESUME
        // alongside RUN_CREATE. The cursor iff rule is scoped to RUN_CREATE, so
        // the other request types define their own response shape (status
        // snapshot, cancel ack, resume ack) rather than reusing the create
        // cursor. Pinning the closed enum prevents a silent new request type
        // from inheriting an undefined cursor rule.
        assertThat(envelopeSource)
                .as("IngressRequestType must be a closed enum covering the four request types")
                .contains("RUN_CREATE")
                .contains("RUN_GET")
                .contains("RUN_CANCEL")
                .contains("RUN_RESUME");
        assertThat(contractYaml)
                .as("contract must confine the cursor rule to RUN_CREATE (request-type scoped)")
                .contains("request_type=RUN_CREATE");
    }

    // ---- rule 3: REJECTED carries a rejection reason, not a cursor --------

    @Test
    void rejected_requires_rejection_reason_without_cursor() {
        assertThat(contractYaml)
                .as("contract must require rejection_reason iff status=REJECTED")
                .contains("present iff status=REJECTED");
        assertThat(responseSource)
                .as("IngressResponse compact constructor must enforce a non-blank "
                  + "rejectionReason when status=REJECTED")
                .contains("rejectionReason is required when status=REJECTED")
                .contains("rejectionReason must not be blank when status=REJECTED");
    }

    // ---- rule 4: DEFERRED has no cursor but a follow-up observation path --

    @Test
    void deferred_has_follow_up_observation_path() {
        assertThat(contractYaml)
                .as("contract must define a DEFERRED follow-up observation path (retry/backoff)")
                .contains("DEFERRED")
                .contains("retry with backoff");
        assertThat(responseSource)
                .as("IngressResponse must ship a deferred() factory (cursor=null, no rejection)")
                .contains("IngressStatus.DEFERRED");
    }
}

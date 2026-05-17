package ascend.springai.service.platform.architecture;

import ascend.springai.service.platform.web.ErrorEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@link ErrorEnvelope} JSON shape (plan §6.4). Every 4xx/5xx response
 * MUST serialise to {@code {"error": {"code": ..., "message": ..., "details": [...]}}}.
 * No top-level field other than {@code error}; no 200 with embedded failure.
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E8.
 */
class ErrorEnvelopeContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shape_is_exactly_error_code_message_details() throws Exception {
        ErrorEnvelope env = ErrorEnvelope.of("tenant_mismatch", "Bad tenant.");
        String json = mapper.writeValueAsString(env);
        JsonNode root = mapper.readTree(json);

        assertThat(root.fieldNames()).toIterable().containsExactly("error");
        JsonNode err = root.get("error");
        assertThat(err.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("code", "message", "details");
        assertThat(err.get("code").asText()).isEqualTo("tenant_mismatch");
        assertThat(err.get("message").asText()).isEqualTo("Bad tenant.");
        assertThat(err.get("details").isArray()).isTrue();
    }

    @Test
    void details_defaults_to_empty_array_not_null() throws Exception {
        ErrorEnvelope env = ErrorEnvelope.of("invalid_request", "Bad body.");
        JsonNode root = mapper.readTree(mapper.writeValueAsString(env));
        assertThat(root.get("error").get("details").isArray()).isTrue();
        assertThat(root.get("error").get("details").size()).isEqualTo(0);
    }

    @Test
    void details_list_is_immutable_after_construction() {
        List<String> mutable = new java.util.ArrayList<>(List.of("first"));
        ErrorEnvelope env = new ErrorEnvelope(new ErrorEnvelope.Error("c", "m", mutable));
        mutable.add("second-after-construction");
        assertThat(env.error().details()).containsExactly("first");
    }
}

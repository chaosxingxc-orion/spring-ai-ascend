package ascend.springai.service.runtime.resilience;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilienceContractTest {

    @Test
    void knownOperationId_resolves_to_configured_policy() {
        var policy = new ResiliencePolicy("llm-cb", "llm-retry", "llm-tl");
        var contract = new YamlResilienceContract(Map.of("llm-call", policy), "dev");
        assertThat(contract.resolve("llm-call")).isEqualTo(policy);
    }

    @Test
    void unknownOperationId_in_dev_returns_default_policy() {
        var contract = new YamlResilienceContract(Map.of(), "dev");
        assertThat(contract.resolve("unknown-op")).isEqualTo(ResilienceContract.DEFAULT_POLICY);
    }

    @Test
    void unknownOperationId_in_research_throws() {
        var contract = new YamlResilienceContract(Map.of(), "research");
        assertThatThrownBy(() -> contract.resolve("unknown-op"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown-op")
            .hasMessageContaining("posture=research");
    }

    @Test
    void unknownOperationId_in_prod_throws() {
        var contract = new YamlResilienceContract(Map.of(), "prod");
        assertThatThrownBy(() -> contract.resolve("any-op"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("posture=prod");
    }

    @Test
    void resiliencePolicy_rejects_null_cbName() {
        assertThatThrownBy(() -> new ResiliencePolicy(null, "r", "t"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resiliencePolicy_rejects_null_retryName() {
        assertThatThrownBy(() -> new ResiliencePolicy("cb", null, "t"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.huawei.ascend.service.runtime.resilience;

import com.huawei.ascend.service.runtime.resilience.spi.ResiliencePolicy;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer 2: verifies that a name resolved by ResilienceContract maps to a real
 * Resilience4j circuit breaker without a Spring context.
 */
class ResilienceContractIT {

    @Test
    void resolvedCbName_creates_real_circuit_breaker() {
        var policy = new ResiliencePolicy("llm-circuit-breaker", "llm-retry", "llm-tl");
        var contract = new YamlResilienceContract(Map.of("llm-call", policy), "dev");

        ResiliencePolicy resolved = contract.resolve("llm-call");

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        var cb = registry.circuitBreaker(resolved.cbName());
        assertThat(cb).isNotNull();
        assertThat(cb.getName()).isEqualTo("llm-circuit-breaker");
    }
}

package com.huawei.ascend.service.runtime.resilience;

import com.huawei.ascend.service.runtime.resilience.spi.ResilienceContract;
import com.huawei.ascend.service.runtime.resilience.spi.ResiliencePolicy;

import java.util.Map;

/**
 * Map-backed implementation of ResilienceContract.
 * Reads a pre-built map of operationId → ResiliencePolicy.
 *
 * W2: replace direct construction with @ConfigurationProperties binding and @Bean registration
 * in the LLM gateway module, reading from springai.ascend.resilience.operations.<op-id>.
 */
public class YamlResilienceContract implements ResilienceContract {

    private final Map<String, ResiliencePolicy> policies;
    private final String posture;

    public YamlResilienceContract(Map<String, ResiliencePolicy> policies, String posture) {
        this.policies = Map.copyOf(policies);
        this.posture = posture;
    }

    @Override
    public ResiliencePolicy resolve(String operationId) {
        ResiliencePolicy policy = policies.get(operationId);
        if (policy != null) {
            return policy;
        }
        if ("dev".equalsIgnoreCase(posture)) {
            return DEFAULT_POLICY;
        }
        throw new IllegalArgumentException(
            "No resilience policy configured for operationId='" + operationId + "' posture=" + posture);
    }
}

package com.huawei.ascend.service.runtime.resilience.spi;

/**
 * Dual-surface resilience SPI. Two distinct axes intentionally co-located on a single contract:
 *
 * <ul>
 *   <li><b>Operation-policy axis</b> ({@link #resolve(String)}, W0+): maps an operation identifier
 *   to a named resilience policy triple. Call sites apply Resilience4j annotations
 *   ({@code @CircuitBreaker}, {@code @Retry}, {@code @TimeLimiter}) using the resolved policy names.
 *   Spring {@code @ConfigurationProperties} wiring is deferred to W2.</li>
 *   <li><b>Skill-capacity axis</b> ({@link #resolve(String, String)}, W1.x Phase 9+): tenant + skill
 *   admission.b). Consults
 *   {@code docs/governance/skill-capacity.yaml} via {@link SkillCapacityRegistry}.</li>
 * </ul>
 *
 * <p>The two axes MUST NOT be conflated. The pre-ADR-0070 plan to extend the operation-policy
 * surface to {@code (tenantId, operationId)} (ADR-0030 §HD-C.3, ADR-0044 catalog row) is
 * <b>superseded</b> by ADR-0070's skill axis and formally reconciled in ADR-0081 — the skill
 * argument is the cross-cutting capacity dimension, NOT a renamed operation argument.
 *
 * @see <a href="../../../../../../../../../docs/adr/0030-skill-spi-lifecycle-resource-matrix.md">ADR-0030</a> operation-policy routing
 * @see <a href="../../../../../../../../../docs/adr/0070-cursor-flow-and-skill-capacity-runtime.yaml">ADR-0070</a> skill-capacity arbitration (introduces the two-arg overload)
 * @see <a href="../../../../../../../../../docs/adr/0080-resilience-contract-spi-package-alignment.yaml">ADR-0080</a> package home (this {@code .spi} location)
 * @see <a href="../../../../../../../../../docs/adr/0081-resilience-contract-dual-surface-reconciliation.yaml">ADR-0081</a> dual-surface reconciliation (supersedes the (tenantId, operationId) plan)
 */
public interface ResilienceContract {

    ResiliencePolicy DEFAULT_POLICY = new ResiliencePolicy("default-cb", "default-retry", "default-tl");

    /**
     * Resolve the resilience policy for the given operation.
     * Dev posture: returns DEFAULT_POLICY for unknown operations.
     * Research/prod posture: throws IllegalArgumentException for unknown operations.
     */
    ResiliencePolicy resolve(String operationId);

    /**
     * Two-arg resolve for the {@code (tenant, skill)} surface introduced in W1.x Phase 9
     * (ADR-0070, Rule R-K (legacy 41.b)). Consults {@code docs/governance/skill-capacity.yaml} via the
     * injected {@link SkillCapacityRegistry}; over-cap callers receive a
     * {@link SkillResolution} with {@code admitted = false} carrying a
     * {@link SuspendReason.RateLimited} so the scheduler maps the rejection to
     * {@code RunStatus.SUSPENDED}, NOT to {@code FAILED}.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException} so
     * legacy single-arg implementations stay source-compatible without silently
     * admitting every caller. Production code MUST inject the
     * {@code DefaultSkillResilienceContract}.
     */
    default SkillResolution resolve(String tenant, String skill) {
        throw new UnsupportedOperationException(
                "Two-arg resolve(tenant, skill) requires DefaultSkillResilienceContract "
                        + "(Rule R-K (legacy 41.b) activation.");
    }
}

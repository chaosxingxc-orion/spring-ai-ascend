package ascend.springai.service.runtime.resilience;

/**
 * Fin-services routing layer: maps an operation identifier to a named resilience policy triple.
 * Call sites apply Resilience4j annotations (@CircuitBreaker, @Retry, @TimeLimiter) using
 * the resolved policy names. Spring @ConfigurationProperties wiring is deferred to W2.
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
     * (ADR-0070, Rule 41.b). Consults {@code docs/governance/skill-capacity.yaml} via the
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
                        + "(Rule 41.b activation per ADR-0070).");
    }
}

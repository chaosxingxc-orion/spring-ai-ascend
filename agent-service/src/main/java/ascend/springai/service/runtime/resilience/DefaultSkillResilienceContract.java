package ascend.springai.service.runtime.resilience;

/**
 * Production {@link ResilienceContract} that implements the W1.x Phase 9 two-arg
 * surface (Rule 41.b / ADR-0070). The single-arg {@link #resolve(String)} delegates
 * to the {@link YamlResilienceContract} legacy map for source compatibility with
 * existing call sites; the {@link #resolve(String, String)} variant consults the
 * injected {@link SkillCapacityRegistry}.
 *
 * <p>On rejection the returned {@link SkillResolution} carries
 * {@link SuspendReason.RateLimited} with code {@code SKILL_CAPACITY_EXCEEDED}. Per
 * Rule 41, callers translate this into {@code RunStatus.SUSPENDED} — never
 * {@code FAILED}.
 *
 * <p>Bean registration is owned by
 * {@code ascend.springai.service.platform.resilience.ResilienceAutoConfiguration}.
 */
public class DefaultSkillResilienceContract implements ResilienceContract {

    private final SkillCapacityRegistry registry;

    public DefaultSkillResilienceContract(SkillCapacityRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ResiliencePolicy resolve(String operationId) {
        // Single-arg resilience routing belongs to the W2 LLM gateway; at W1.x the
        // default policy is sufficient (Rule 2 — minimum code that solves the stated
        // problem). Callers wanting custom policy lookup register a YamlResilienceContract.
        return DEFAULT_POLICY;
    }

    @Override
    public SkillResolution resolve(String tenant, String skill) {
        if (registry.tryAcquire(tenant, skill)) {
            return SkillResolution.admit();
        }
        return SkillResolution.reject(new SuspendReason.RateLimited(
                skill, SuspendReason.RateLimited.SKILL_CAPACITY_EXCEEDED));
    }
}

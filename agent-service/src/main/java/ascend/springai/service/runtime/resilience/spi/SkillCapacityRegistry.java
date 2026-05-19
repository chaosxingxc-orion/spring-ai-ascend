package ascend.springai.service.runtime.resilience.spi;

/**
 * Per-skill capacity tracker consulted by {@link ResilienceContract#resolve(String, String)}.
 * Implementations honour {@code docs/governance/skill-capacity.yaml}'s
 * {@code capacity_per_tenant} and {@code global_capacity} columns.
 *
 * <p>Lifecycle is balanced: every successful {@link #tryAcquire(String, String)} MUST
 * be matched by exactly one {@link #release(String, String)} on the same {@code (tenant, skill)}
 * pair.
 *
 * <p>Authority: ADR-0070; CLAUDE.md Rule R-K (Skill Capacity Matrix).
 */
public interface SkillCapacityRegistry {

    /**
     * Atomically reserve one capacity slot for {@code (tenant, skill)}.
     *
     * @return {@code true} if a slot was reserved (caller must invoke {@link #release})
     *         or {@code false} if the per-tenant or global cap is full
     */
    boolean tryAcquire(String tenant, String skill);

    /** Release one previously-acquired slot. No-op if no matching {@link #tryAcquire}. */
    void release(String tenant, String skill);
}

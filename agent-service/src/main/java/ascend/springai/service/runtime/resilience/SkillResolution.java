package ascend.springai.service.runtime.resilience;

/**
 * Outcome of {@link ResilienceContract#resolve(String, String)}. Either the caller is
 * admitted ({@code admitted = true} and {@code reasonIfRejected = null}) or the caller
 * is rejected ({@code admitted = false} and {@code reasonIfRejected} non-null carrying
 * the {@link SuspendReason} the scheduler should map to a suspension transition).
 *
 * <p>Per Rule 41 / Layer-0 principle P-K (Skill-Dimensional Resource Arbitration),
 * a rejection means "park the agent process on the skill's wait-queue and free the
 * OS thread" — NOT "fail the run". The caller is responsible for the actual
 * {@code Run.withSuspension(...)} transition (W2 orchestrator wiring).
 *
 * <p>Authority: ADR-0070; CLAUDE.md Rule 41.b.
 */
public record SkillResolution(boolean admitted, SuspendReason reasonIfRejected) {

    public static SkillResolution admit() {
        return new SkillResolution(true, null);
    }

    public static SkillResolution reject(SuspendReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("reasonIfRejected is required when admitted=false");
        }
        return new SkillResolution(false, reason);
    }
}
